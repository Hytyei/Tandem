package app.tandem.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Finds a poster / cover and a short description for what someone is watching, playing, reading or obsessed with.
 * Every source is free and needs no account or API key:
 *  - TV series: TVmaze (fallback IMDb)          - movies: IMDb search (fallback Apple iTunes)
 *  - games: Steam store (fallback Wikipedia)    - books: Google Books (fallback Open Library)
 *  - tracks: Deezer (fallback Apple iTunes)
 * It only runs when the person taps the search button, and the text they typed goes to that service (never to the Tandem server).
 * The Tandem sign-in token is not attached because these hosts differ from the Tandem server.
 */
object MediaLookup {
    const val WATCHING = "watching"     // a TV series
    const val MOVIE = "movie"
    const val PLAYING = "playing"       // a game
    const val LISTENING = "listening"   // the track they're obsessed with
    const val READING = "reading"

    /** Who gets the typed text (shown under the search box). */
    fun sourceName(kind: String) = when (kind) {
        WATCHING -> "TVmaze and IMDb"
        MOVIE -> "IMDb and Apple iTunes"
        PLAYING -> "Steam and Wikipedia"
        READING -> "Google Books and Open Library"
        LISTENING -> "Deezer and Apple iTunes"
        else -> "the web"
    }

    suspend fun search(http: OkHttpClient, kind: String, query: String): List<MediaInfo> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val chain: List<() -> List<MediaInfo>> = when (kind) {
            WATCHING -> listOf({ tvmaze(http, q) }, { imdb(http, q, setOf("tvSeries", "tvMiniSeries")) })
            MOVIE -> listOf({ imdb(http, q, setOf("movie", "tvMovie")) }, { itunesMovies(http, q) })
            LISTENING -> listOf({ deezer(http, q) }, { itunesSongs(http, q) })
            READING -> listOf({ googleBooks(http, q) }, { openLibrary(http, q) })
            PLAYING -> listOf({ runBlockingSteam(http, q) }, { wikipedia(http, q, "video game") })
            else -> listOf({ wikipedia(http, q, "") })
        }
        // first source that answers with something wins; a source that is down just hands over to the next one
        var reached = false
        for (source in chain) {
            try {
                val r = source()
                reached = true
                if (r.isNotEmpty()) return@withContext r
            } catch (e: IOException) { /* try the next one */ } catch (e: AppException) { reached = true }
        }
        if (!reached) throw AppException("Can't reach the lookup service. Check your connection.")
        emptyList()
    }

    private fun get(http: OkHttpClient, url: HttpUrl): JsonElement? {
        val req = Request.Builder().url(url).header("Accept", "application/json").header("User-Agent", "Tandem/1.3 (couples app)").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw AppException("The lookup service is busy. Try again in a moment.")
            return runCatching { AppJson.parseToJsonElement(r.body?.string().orEmpty()) }.getOrNull()
        }
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.arr(k: String) = this[k] as? JsonArray
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonArray.strings() = mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    private fun String.https() = if (startsWith("http://")) "https://" + removePrefix("http://") else this
    private fun String.plain() = replace(Regex("<[^>]*>"), " ").replace("&quot;", "\"").replace("&amp;", "&").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")

    private fun clip(s: String, n: Int): String {
        val t = s.plain().replace(Regex("\\s+"), " ").trim()
        if (t.length <= n) return t
        val cut = t.take(n)
        val end = cut.lastIndexOf(". ")
        return if (end > n / 2) cut.take(end + 1) else cut.trimEnd(' ', ',', ';') + "…"
    }

    // ---- TVmaze (TV series): title, premiere year, channel, genres, summary and a portrait poster, in one request
    private fun tvmaze(http: OkHttpClient, q: String): List<MediaInfo> {
        val url = HttpUrl.Builder().scheme("https").host("api.tvmaze.com").addPathSegments("search/shows").addQueryParameter("q", q).build()
        val hits = get(http, url) as? JsonArray ?: return emptyList()
        return hits.mapNotNull { (it as? JsonObject)?.obj("show") }.mapNotNull { s ->
            val name = s.str("name").ifBlank { return@mapNotNull null }
            val year = s.str("premiered").take(4)
            val channel = s.obj("network")?.str("name").orEmpty().ifBlank { s.obj("webChannel")?.str("name").orEmpty() }
            val genres = s.arr("genres")?.strings()?.take(2)?.joinToString(", ").orEmpty()
            val poster = s.obj("image")?.str("medium").orEmpty().https()
            MediaInfo(
                title = name.take(60), sub = listOf(year, channel, genres).filter { it.isNotBlank() }.joinToString(" · ").take(80),
                blurb = clip(s.str("summary"), 240), img = poster, src = "tvmaze",
            )
        }.take(6)
    }

    // ---- IMDb's public search suggestions (movies and series): title, year, main cast and a poster
    private fun imdb(http: OkHttpClient, q: String, types: Set<String>): List<MediaInfo> {
        val url = HttpUrl.Builder().scheme("https").host("v3.sg.media-imdb.com").addPathSegments("suggestion/x").addPathSegment("${q.lowercase().take(60)}.json").build()
        val items = (get(http, url) as? JsonObject)?.arr("d") ?: return emptyList()
        return items.mapNotNull { it as? JsonObject }.filter { it.str("qid") in types }.mapNotNull { o ->
            val title = o.str("l").ifBlank { return@mapNotNull null }
            val year = o.int("y")?.toString().orEmpty()
            val label = when (o.str("qid")) { "tvSeries" -> "TV series"; "tvMiniSeries" -> "Mini-series"; "tvMovie" -> "TV movie"; else -> "Film" }
            val cast = o.str("s")
            var img = o.obj("i")?.str("imageUrl").orEmpty().https()
            if (img.endsWith("._V1_.jpg")) img = img.removeSuffix("._V1_.jpg") + "._V1_UY450_.jpg"      // a ~300 px poster instead of the full-size scan
            MediaInfo(
                title = title.take(60), sub = listOf(year, label).filter { it.isNotBlank() }.joinToString(" · "),
                blurb = if (cast.isNotBlank()) "Starring ${cast.take(150)}" else "", img = img.takeIf { it.length <= 400 } ?: "", src = "imdb",
            )
        }.take(6)
    }

    // ---- Apple iTunes Search (movies and songs): official, keyless
    private fun itunesMovies(http: OkHttpClient, q: String): List<MediaInfo> {
        val url = HttpUrl.Builder().scheme("https").host("itunes.apple.com").addPathSegment("search")
            .addQueryParameter("term", q).addQueryParameter("media", "movie").addQueryParameter("entity", "movie").addQueryParameter("limit", "8").build()
        val results = (get(http, url) as? JsonObject)?.arr("results") ?: return emptyList()
        return results.mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val title = o.str("trackName").ifBlank { return@mapNotNull null }
            val art = o.str("artworkUrl100").replace("100x100bb", "400x400bb")
            MediaInfo(
                title = title.take(60),
                sub = listOf(o.str("releaseDate").take(4), o.str("primaryGenreName"), o.str("artistName")).filter { it.isNotBlank() }.joinToString(" · ").take(80),
                blurb = clip(o.str("longDescription").ifBlank { o.str("shortDescription") }, 240),
                img = art.takeIf { it.startsWith("https://") } ?: "", src = "itunes",
            )
        }.take(6)
    }

    private fun itunesSongs(http: OkHttpClient, q: String): List<MediaInfo> {
        val url = HttpUrl.Builder().scheme("https").host("itunes.apple.com").addPathSegment("search")
            .addQueryParameter("term", q).addQueryParameter("media", "music").addQueryParameter("entity", "song").addQueryParameter("limit", "8").build()
        val results = (get(http, url) as? JsonObject)?.arr("results") ?: return emptyList()
        return results.mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val track = o.str("trackName").ifBlank { return@mapNotNull null }
            val art = o.str("artworkUrl100").replace("100x100bb", "400x400bb")
            MediaInfo(
                title = track.take(60),
                sub = listOf(o.str("artistName"), o.str("collectionName")).filter { it.isNotBlank() }.joinToString(" · ").take(80),
                blurb = listOf(o.str("primaryGenreName"), o.str("releaseDate").take(4)).filter { it.isNotBlank() }.joinToString(" · "),
                img = art.takeIf { it.startsWith("https://") } ?: "", src = "itunes",
            )
        }.take(6)
    }

    // ---- Deezer (tracks): worldwide catalogue, square album art
    private fun deezer(http: OkHttpClient, q: String): List<MediaInfo> {
        val url = HttpUrl.Builder().scheme("https").host("api.deezer.com").addPathSegments("search/track")
            .addQueryParameter("q", q).addQueryParameter("limit", "8").build()
        val data = (get(http, url) as? JsonObject)?.arr("data") ?: return emptyList()
        return data.mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val title = o.str("title").ifBlank { return@mapNotNull null }
            val album = o.obj("album")
            val secs = o.int("duration") ?: 0
            MediaInfo(
                title = title.take(60),
                sub = listOf(o.obj("artist")?.str("name").orEmpty(), album?.str("title").orEmpty()).filter { it.isNotBlank() }.joinToString(" · ").take(80),
                blurb = if (secs > 0) "Song · %d:%02d".format(secs / 60, secs % 60) else "Song",
                img = (album?.str("cover_big").orEmpty().ifBlank { album?.str("cover_medium").orEmpty() }).https(), src = "deezer",
            )
        }.take(6)
    }

    // ---- Steam store (games): search, then one details call per hit for the description, year and a portrait cover
    private fun runBlockingSteam(http: OkHttpClient, q: String): List<MediaInfo> = kotlinx.coroutines.runBlocking { steam(http, q) }

    private suspend fun steam(http: OkHttpClient, q: String): List<MediaInfo> = coroutineScope {
        val url = HttpUrl.Builder().scheme("https").host("store.steampowered.com").addPathSegments("api/storesearch/")
            .addQueryParameter("term", q).addQueryParameter("l", "english").addQueryParameter("cc", "US").build()
        val items = (get(http, url) as? JsonObject)?.arr("items")?.mapNotNull { it as? JsonObject }?.filter { it.str("type") == "app" }?.take(5) ?: return@coroutineScope emptyList()
        items.map { o -> async(Dispatchers.IO) { steamDetails(http, o) } }.awaitAll().filterNotNull()
    }

    private fun steamDetails(http: OkHttpClient, o: JsonObject): MediaInfo? {
        val id = o.int("id") ?: return null
        val name = o.str("name").ifBlank { return null }
        var sub = ""; var blurb = ""; var header = ""
        runCatching {
            val url = HttpUrl.Builder().scheme("https").host("store.steampowered.com").addPathSegments("api/appdetails")
                .addQueryParameter("appids", id.toString()).addQueryParameter("l", "english").build()
            val d = (get(http, url) as? JsonObject)?.obj(id.toString())?.obj("data")
            if (d != null) {
                val year = d.obj("release_date")?.str("date").orEmpty().takeLast(4).takeIf { it.all(Char::isDigit) }.orEmpty()
                val dev = d.arr("developers")?.strings()?.firstOrNull().orEmpty()
                val genres = d.arr("genres")?.mapNotNull { (it as? JsonObject)?.str("description") }?.take(2)?.joinToString(", ").orEmpty()
                sub = listOf(year, dev, genres).filter { it.isNotBlank() }.joinToString(" · ").take(80)
                blurb = clip(d.str("short_description"), 240)
                header = d.str("header_image").substringBefore('?')
            }
        }
        // the portrait cover exists for most games; if not, the wide header picture is used
        val portrait = "https://cdn.cloudflare.steamstatic.com/steam/apps/$id/library_600x900.jpg"
        val hasPortrait = runCatching {
            http.newCall(Request.Builder().url(portrait).head().build()).execute().use { it.isSuccessful }
        }.getOrDefault(false)
        val img = if (hasPortrait) portrait else header.https()
        return MediaInfo(title = name.take(60), sub = sub.ifBlank { "PC game on Steam" }, blurb = blurb, img = img, src = "steam")
    }

    // ---- Google Books (books): title, authors, description and a cover
    private fun googleBooks(http: OkHttpClient, q: String): List<MediaInfo> {
        val url = HttpUrl.Builder().scheme("https").host("www.googleapis.com").addPathSegments("books/v1/volumes")
            .addQueryParameter("q", q).addQueryParameter("maxResults", "8").addQueryParameter("printType", "books")
            .addQueryParameter("fields", "items(volumeInfo(title,authors,description,imageLinks,publishedDate,categories))").build()
        val items = (get(http, url) as? JsonObject)?.arr("items") ?: return emptyList()
        return items.mapNotNull { (it as? JsonObject)?.obj("volumeInfo") }.mapNotNull { v ->
            val title = v.str("title").ifBlank { return@mapNotNull null }
            val authors = v.arr("authors")?.strings()?.take(2)?.joinToString(", ").orEmpty()
            val year = v.str("publishedDate").take(4)
            val cat = v.arr("categories")?.strings()?.firstOrNull().orEmpty()
            val cover = v.obj("imageLinks")?.let { it.str("thumbnail").ifBlank { it.str("smallThumbnail") } }.orEmpty().https().replace("&edge=curl", "")
            MediaInfo(
                title = title.take(60), sub = listOf(authors, year).filter { it.isNotBlank() }.joinToString(" · ").take(80),
                blurb = clip(v.str("description").ifBlank { cat }, 240), img = cover, src = "googlebooks",
            )
        }.take(6)
    }

    // ---- Open Library (books, fallback)
    private fun openLibrary(http: OkHttpClient, q: String): List<MediaInfo> {
        val url = HttpUrl.Builder().scheme("https").host("openlibrary.org").addPathSegments("search.json")
            .addQueryParameter("q", q).addQueryParameter("limit", "6").addQueryParameter("fields", "title,author_name,first_publish_year,cover_i,subject").build()
        val docs = (get(http, url) as? JsonObject)?.arr("docs") ?: return emptyList()
        return docs.mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val title = o.str("title").ifBlank { return@mapNotNull null }
            val authors = o.arr("author_name")?.strings()?.take(2)?.joinToString(", ").orEmpty()
            val year = o.int("first_publish_year")?.toString().orEmpty()
            val subjects = o.arr("subject")?.strings()?.take(3)?.joinToString(", ").orEmpty()
            val cover = o.int("cover_i")
            MediaInfo(
                title = title.take(60), sub = authors.take(80),
                blurb = listOf(if (year.isNotBlank()) "First published $year" else "", subjects).filter { it.isNotBlank() }.joinToString(" · ").take(240),
                img = if (cover != null) "https://covers.openlibrary.org/b/id/$cover-M.jpg" else "", src = "openlibrary",
            )
        }.take(6)
    }

    // ---- Wikipedia (last resort, mostly for console games)
    private fun wikipedia(http: OkHttpClient, q: String, hint: String): List<MediaInfo> {
        val url = HttpUrl.Builder().scheme("https").host("en.wikipedia.org").addPathSegments("w/api.php")
            .addQueryParameter("action", "query").addQueryParameter("format", "json").addQueryParameter("formatversion", "2")
            .addQueryParameter("generator", "search").addQueryParameter("gsrsearch", "$q $hint".trim()).addQueryParameter("gsrlimit", "6")
            .addQueryParameter("prop", "pageimages|extracts|description")
            .addQueryParameter("piprop", "thumbnail").addQueryParameter("pithumbsize", "400")
            .addQueryParameter("exintro", "1").addQueryParameter("explaintext", "1").addQueryParameter("exsentences", "3").addQueryParameter("exlimit", "max")
            .build()
        val pages = (get(http, url) as? JsonObject)?.obj("query")?.arr("pages") ?: return emptyList()
        return pages.mapNotNull { it as? JsonObject }
            .sortedBy { it.int("index") ?: 99 }
            .mapNotNull { p ->
                val title = p.str("title").ifBlank { return@mapNotNull null }
                val img = p.obj("thumbnail")?.str("source").orEmpty()
                MediaInfo(title = title.take(60), sub = clip(p.str("description"), 80), blurb = clip(p.str("extract"), 240), img = img.takeIf { it.startsWith("https://") } ?: "", src = "wikipedia")
            }
            .filter { it.sub.isNotBlank() || it.blurb.isNotBlank() }
            .take(6)
    }
}
