package app.tandem.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.tandem.BuildConfig
import app.tandem.data.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

@Serializable
private data class GhAsset(val name: String, @SerialName("browser_download_url") val url: String, val size: Long = 0, val digest: String? = null)

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long, val sha256: String? = null)

/** Checks the latest GitHub Release of the configured repo and installs its .apk asset. */
object Updater {
    // GitHub's API is public for public repos and doesn't need our auth header.
    private val client = OkHttpClient()

    /** Returns info if a newer release than the installed version exists, else null. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val repo = BuildConfig.GITHUB_REPO
        if (!Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(repo) || repo.startsWith("YOUR-")) return@withContext null
        val req = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 404) return@withContext null
            if (!resp.isSuccessful) throw IOException("GitHub returned ${resp.code}")
            val rel = AppJson.decodeFromString(GhRelease.serializer(), resp.body!!.string())
            if (rel.prerelease) return@withContext null
            // Only accept downloads that really are assets of THIS repository's releases, over https.
            val apk = rel.assets.firstOrNull { it.name.endsWith(".apk") && it.url.startsWith("https://github.com/$repo/releases/download/") }
                ?: return@withContext null
            val v = rel.tag.removePrefix("v")
            val sha = apk.digest?.removePrefix("sha256:")?.takeIf { it.length == 64 }
            if (isNewer(v, BuildConfig.VERSION_NAME)) UpdateInfo(v, rel.body.orEmpty().trim(), apk.url, apk.size, sha) else null
        }
    }

    fun isNewer(remote: String, local: String): Boolean {
        fun parts(s: String) = s.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Downloads the APK (reporting 0..1 progress) and returns the file. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "tandem-${info.version}.apk")
        client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download failed (${resp.code})")
            val body = resp.body ?: throw IOException("Empty download")
            val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(32 * 1024); var done = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        output.write(buf, 0, n); done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        // If GitHub published a SHA-256 for the asset, make sure the download matches it before offering to install.
        info.sha256?.let { expected ->
            val md = java.security.MessageDigest.getInstance("SHA-256")
            out.inputStream().use { ins -> val buf = ByteArray(64 * 1024); while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
            val actual = md.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expected, ignoreCase = true)) { out.delete(); throw IOException("Download is corrupted") }
        }
        out
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallSettings(context: Context) {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
