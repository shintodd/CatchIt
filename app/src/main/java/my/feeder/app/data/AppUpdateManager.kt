package my.feeder.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.feeder.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Metadata for a discovered GitHub APK release.
 */
data class AppUpdate(
    val tagName: String,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val apkSizeBytes: Long
)

sealed interface UpdateStatus {
    object Idle : UpdateStatus
    object Checking : UpdateStatus
    data class UpToDate(val currentVersion: String) : UpdateStatus
    data class Available(val update: AppUpdate) : UpdateStatus
    data class Downloading(val progressPercent: Int) : UpdateStatus
    data class ReadyToInstall(val update: AppUpdate, val file: File) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

/**
 * In-App Silent Auto-Updater powered by GitHub Releases.
 *
 * Allows CatchIt to self-update without requiring the Google Play Store ($25 fee avoided):
 * 1. Checks GitHub Releases API in the background.
 * 2. Compares semantic versioning against BuildConfig.VERSION_NAME.
 * 3. Silently downloads the release APK to cache when requested.
 * 4. Invokes Android's PackageInstaller via FileProvider for a 1-tap seamless update.
 */
object AppUpdateManager {

    private const val GITHUB_REPO_PRIMARY = "shintodd/CatchIt"
    private const val GITHUB_REPO_FALLBACK = "shintodd/FeederMY"
    private const val TIMEOUT_MS = 6000
    private const val USER_AGENT = "CatchIt-Android-Updater/1.1"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private val _updateAvailable = MutableStateFlow<AppUpdate?>(null)
    val updateAvailable: StateFlow<AppUpdate?> = _updateAvailable.asStateFlow()

    /**
     * Checks GitHub for updates asynchronously. Safe to call on cold start.
     */
    fun checkSilently(context: Context) {
        scope.launch {
            checkForUpdates(context, silent = true)
        }
    }

    /**
     * Check GitHub for updates.
     * Returns [AppUpdate] if a newer release exists with an attached APK.
     */
    suspend fun checkForUpdates(context: Context, silent: Boolean = false): AppUpdate? = withContext(Dispatchers.IO) {
        if (!silent) _updateStatus.value = UpdateStatus.Checking

        val update = queryGitHubRelease(GITHUB_REPO_PRIMARY)
            ?: queryGitHubRelease(GITHUB_REPO_FALLBACK)

        if (update != null && isNewerVersion(update.versionName, BuildConfig.VERSION_NAME)) {
            _updateAvailable.value = update
            _updateStatus.value = UpdateStatus.Available(update)
            update
        } else {
            if (!silent) _updateStatus.value = UpdateStatus.UpToDate(BuildConfig.VERSION_NAME)
            null
        }
    }

    private fun queryGitHubRelease(repoPath: String): AppUpdate? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://api.github.com/repos/$repoPath/releases/latest")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }
            if (conn.responseCode != 200) return null

            val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(jsonText)
            val tagName = root.optString("tag_name", "").trim()
            val versionName = tagName.removePrefix("v").removePrefix("V").trim()
            val releaseNotes = root.optString("body", "").trim()
            val assets = root.optJSONArray("assets") ?: return null

            var apkUrl: String? = null
            var apkSize = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "")
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }

            if (!apkUrl.isNullOrBlank() && versionName.isNotBlank()) {
                AppUpdate(
                    tagName = tagName,
                    versionName = versionName,
                    downloadUrl = apkUrl,
                    releaseNotes = releaseNotes,
                    apkSizeBytes = apkSize
                )
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Compares remote semantic version against current version.
     * e.g. "1.2.0" > "1.1.0" -> true, "1.1.1" > "1.1.0" -> true.
     */
    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val remoteClean = remoteVersion.removePrefix("v").removePrefix("V").trim()
        val currentClean = currentVersion.removePrefix("v").removePrefix("V").trim()
        val rParts = remoteClean.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * Downloads the APK file into cache and prompts Android PackageInstaller to install it.
     */
    fun downloadAndInstall(context: Context, update: AppUpdate) {
        scope.launch {
            try {
                _updateStatus.value = UpdateStatus.Downloading(0)
                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val targetFile = File(updatesDir, "catchit-v${update.versionName}.apk")

                val url = URL(update.downloadUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", USER_AGENT)
                    instanceFollowRedirects = true
                }

                // Handle redirects (GitHub release downloads redirect to AWS S3)
                val finalConn = if (conn.responseCode in 300..399) {
                    val redirectUrl = conn.getHeaderField("Location")
                    conn.disconnect()
                    (URL(redirectUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10000
                        readTimeout = 15000
                        setRequestProperty("User-Agent", USER_AGENT)
                    }
                } else {
                    conn
                }

                val totalBytes = finalConn.contentLengthLong.takeIf { it > 0 } ?: update.apkSizeBytes
                finalConn.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead: Int
                        var downloaded = 0L
                        var lastReportedPercent = 0

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalBytes > 0) {
                                val percent = ((downloaded * 100) / totalBytes).toInt()
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    _updateStatus.value = UpdateStatus.Downloading(percent)
                                }
                            }
                        }
                    }
                }
                finalConn.disconnect()

                _updateStatus.value = UpdateStatus.ReadyToInstall(update, targetFile)

                // Launch package installer
                installApk(context, targetFile)
            } catch (t: Throwable) {
                _updateStatus.value = UpdateStatus.Error(t.message ?: "Download failed")
            }
        }
    }

    /**
     * Launches the native Android package installer using FileProvider.
     */
    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
