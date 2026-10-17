package app.tandem.data

import kotlinx.coroutines.Dispatchers
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
 * Finds a poster / cover and a short description for what someone is watching, playing, listening to or reading.
 * No account, no API key: Wikipedia (films, series, games), Apple's public iTunes Search (music) and Open Library (books).
 * It only runs when the person taps the search button, and the text they typed goes to that service (never to the Tandem server).
 * The Tandem sign-in token is not attached because these hosts differ from the Tandem server.
 */
object MediaLookup {
    const val WATCHING = "watching"
    const val PLAYING = "playing"
    const val LISTENING = "listening"
    const val READING = "reading"

    /** Who gets the typed text (shown under the search box). */
    fun sourceName(kind: String) = when (kind) {
        LISTENING -> "Apple iTunes Search"
        READING -> "Open Library"
        else -> "Wikipedia"
    }

    suspend fun search(http: OkHttpClient, kind: String, query: String): List<MediaInfo> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        try {
            when (kind) {
                LISTENING -> itunes(http, q)
                READING -> openLibrary(http, q).ifEmpty { wikipedia(http, q, "book novel") }
                PLAYING -> wikipedia(http, q, "video game")
                else -> wikipedia(http, q, "film OR television series")
            }
        } catch (e: IOException) {
            throw AppException("Can't reach the lookup service. Check your connection.")
        }
    }

    private fun get(http: OkHttpClient, url: HttpUrl): JsonElement? {
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw AppException("The lookup service is busy. Try again in a moment.")
            return runCatching { AppJson.parseToJsonElement(r.body?.string().orEmpty()) }.getOrNull()
        }
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.arr(k: String) = this[k] as? JsonArray

    private fun clip(s: String, n: Int): String {
        val t = s.replace(Regex("\\s+"), " ").trim()
        if (t.length <= n) return t
        val cut = t.take(n)
        val end = cut.lastIndexOf(". ")
        return if (end > n / 2) cut.take(end + 1) else cut.trimEnd(' ', ',', ';') + "…"
    }

    // ---- Wikipedia: one request returns title, intro extract, short description and a 400 px picture
    private fun wikipedia(http: OkHttpClient, q: String, hint: String): List<MediaInfo> {
        val url = HttpUrl.Builder().scheme("https").host("en.wikipedia.org").addPathSegments("w/api.php")
            .addQueryParameter("action", "query").addQueryParameter("format", "json").addQueryParameter("formatversion", "2")
            .addQueryParameter("generator", "search").addQueryParameter("gsrsearch", "$q $hint").addQueryParameter("gsrlimit", "6")
            .addQueryParameter("prop", "pageimages|extracts|description")
            .addQueryParameter("piprop", "thumbnail").addQueryParameter("pithumbsize", "400")
            .addQueryParameter("exintro", "1").addQueryParameter("explaintext", "1").addQueryParameter("exsentences", "3").addQueryParameter("exlimit", "max")
            .build()
        val pages = (get(http, url) as? JsonObject)?.obj("query")?.arr("pages") ?: return emptyList()
        return pages.mapNotNull { it as? JsonObject }
            .sortedBy { (it["index"] as? JsonPrimitive)?.intOrNull ?: 99 }
            .mapNotNull { p ->
                val title = p.str("title").ifBlank { return@mapNotNull null }
                val img = p.obj("thumbnail")?.str("source").orEmpty()
                MediaInfo(title = title.take(60), sub = clip(p.str("description"), 80), blurb = clip(p.str("extract"), 240), img = img.takeIf { it.startsWith("https://") } ?: "", src = "wikipedia")
            }
            .filter { it.sub.isNotBlank() || it.blurb.isNotBlank() }   // skips list/disambiguation noise with no description at all
            .take(6)
    }

    // ---- iTunes Search (music)
    private fun itunes(http: OkHttpClient, q: String): List<MediaInfo> {
        val url = HttpUrl.Builder().scheme("https").host("itunes.apple.com").addPathSegment("search")
            .addQueryParameter("term", q).addQueryParameter("media", "music").addQueryParameter("entity", "song").addQueryParameter("limit", "8").build()
        val results = (get(http, url) as? JsonObject)?.arr("results") ?: return emptyList()
        return results.mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val track = o.str("trackName").ifBlank { return@mapNotNull null }
            val art = o.str("artworkUrl100").replace("100x100bb", "400x400bb")
            val year = o.str("releaseDate").take(4)
            val genre = o.str("primaryGenreName")
            val album = o.str("collectionName")
            MediaInfo(
                title = track.take(60),
                sub = listOf(o.str("artistName"), album).filter { it.isNotBlank() }.joinToString(" · ").take(80),
                blurb = listOf(genre, year).filter { it.isNotBlank() }.joinToString(" · "),
                img = art.takeIf { it.startsWith("https://") } ?: "", src = "itunes",
            )
        }.take(6)
    }

    // ---- Open Library (books)
    private fun openLibrary(http: OkHttpClient, q: String): List<MediaInfo> {
        val url = HttpUrl.Builder().scheme("https").host("openlibrary.org").addPathSegments("search.json")
            .addQueryParameter("q", q).addQueryParameter("limit", "6").addQueryParameter("fields", "title,author_name,first_publish_year,cover_i,subject").build()
        val docs = (get(http, url) as? JsonObject)?.arr("docs") ?: return emptyList()
        return docs.mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val title = o.str("title").ifBlank { return@mapNotNull null }
            val authors = o.arr("author_name")?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.take(2)?.joinToString(", ").orEmpty()
            val year = (o["first_publish_year"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val subjects = o.arr("subject")?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.take(3)?.joinToString(", ").orEmpty()
            val cover = (o["cover_i"] as? JsonPrimitive)?.intOrNull
            MediaInfo(
                title = title.take(60), sub = authors.take(80),
                blurb = listOf(if (year.isNotBlank()) "First published $year" else "", subjects).filter { it.isNotBlank() }.joinToString(" · ").take(240),
                img = if (cover != null) "https://covers.openlibrary.org/b/id/$cover-M.jpg" else "", src = "openlibrary",
            )
        }.take(6)
    }
}
