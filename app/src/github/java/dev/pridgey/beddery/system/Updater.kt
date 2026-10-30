package dev.pridgey.beddery.system

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import dev.pridgey.beddery.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A newer Beddery on GitHub. */
data class Release(
    val version: String,
    val notes: String,
    /** The APK's API address (works with a token) and its public download address. */
    val apiUrl: String,
    val downloadUrl: String,
    val size: Long,
    /** "sha256:…", when GitHub provides one. */
    val digest: String?,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    /** Downloaded; Android asks the user to confirm the install. */
    data class Installing(val release: Release) : UpdateState
    /** Beddery needs "Install unknown apps" before it can update itself. */
    data class NeedsPermission(val release: Release) : UpdateState
    data class Failed(val reason: String, val release: Release? = null) : UpdateState
}

/** While an update is waiting or on its way, the gallery shows a banner for it. */
val UpdateState.showsBanner: Boolean
    get() = this is UpdateState.Available || this is UpdateState.Downloading || this is UpdateState.Installing || this is UpdateState.NeedsPermission

/**
 * Keeps Beddery up to date from its GitHub releases: checks the latest release, downloads its
 * APK, checks it against GitHub's digest and hands it to Android's package installer. Release
 * builds are all signed with the same key, so each one installs over the last.
 */
object Updater {
    private const val TAG = "BedderyUpdater"
    private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Debug builds are a different app ID, so a release APK would install beside them, not over them. */
    val supported: Boolean get() = !BuildConfig.DEBUG

    val currentVersion: String get() = BuildConfig.VERSION_NAME.substringBefore('-')

    /** Checks at most every few hours; for app start. */
    fun checkIfDue(context: Context) {
        if (!supported) return
        val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)
        val last = prefs.getLong("lastCheck", 0)
        if (System.currentTimeMillis() - last < CHECK_EVERY_MS) {
            // Still show an update found earlier.
            if (_state.value == UpdateState.Idle) restore(prefs.getString("lastRelease", null))
            return
        }
        check(context)
    }

    fun check(context: Context) {
        val s = _state.value
        if (!supported || s is UpdateState.Checking || s is UpdateState.Downloading || s is UpdateState.Installing) return
        _state.value = UpdateState.Checking
        val app = context.applicationContext
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { fetchLatest() } }
            val prefs = app.getSharedPreferences("updater", Context.MODE_PRIVATE)
            result.onSuccess { release ->
                prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
                _state.value = if (release != null && isNewer(release.version, currentVersion)) {
                    prefs.edit().putString("lastRelease", release.toJson()).apply()
                    UpdateState.Available(release)
                } else {
                    prefs.edit().remove("lastRelease").apply()
                    UpdateState.UpToDate
                }
            }.onFailure {
                Log.w(TAG, "Update check failed", it)
                _state.value = UpdateState.Failed(it.message ?: it.javaClass.simpleName)
            }
        }
    }

    /** Downloads [release] and starts the install, asking for install permission first if needed. */
    fun install(context: Context, release: Release) {
        val app = context.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(release)
            return
        }
        _state.value = UpdateState.Downloading(release, 0f)
        scope.launch {
            runCatching {
                val apk = withContext(Dispatchers.IO) { download(app, release) }
                _state.value = UpdateState.Installing(release)
                withContext(Dispatchers.IO) { startInstall(app, apk) }
            }.onFailure {
                Log.w(TAG, "Update failed", it)
                _state.value = UpdateState.Failed(it.message ?: it.javaClass.simpleName, release)
            }
        }
    }

    /** Opens the system page where the user lets Beddery install updates. */
    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** After the user comes back from the permission page. */
    fun onResume(context: Context) {
        val s = _state.value
        if (s is UpdateState.NeedsPermission && context.packageManager.canRequestPackageInstalls()) install(context, s.release)
    }

    internal fun onInstallResult(status: Int, message: String?) {
        val release = when (val s = _state.value) {
            is UpdateState.Installing -> s.release
            else -> null
        }
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.UpToDate
            // Cancelled from the system dialog: offer it again.
            PackageInstaller.STATUS_FAILURE_ABORTED -> release?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            else -> UpdateState.Failed(message ?: "Install failed ($status)", release)
        }
    }

    // ---------------------------------------------------------------- GitHub

    private fun connect(url: String, accept: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", accept)
        conn.setRequestProperty("User-Agent", "Beddery/${BuildConfig.VERSION_NAME}")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (BuildConfig.UPDATE_TOKEN.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer ${BuildConfig.UPDATE_TOKEN}")
        return conn
    }

    private fun fetchLatest(): Release? {
        val conn = connect("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest", "application/vnd.github+json")
        try {
            when (conn.responseCode) {
                in 200..299 -> Unit
                404 -> error("No releases found")
                else -> error("GitHub said ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith("-release.apk") }
                ?: (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name").endsWith(".apk") }
                ?: return null
            return Release(
                version = json.getString("tag_name").removePrefix("v"),
                notes = json.optString("body"),
                apiUrl = apk.getString("url"),
                downloadUrl = apk.getString("browser_download_url"),
                size = apk.optLong("size"),
                digest = apk.optString("digest").takeIf { it.startsWith("sha256:") },
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun download(context: Context, release: Release): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "beddery-${release.version}.apk")
        // With a token, the API address serves the file (and works for private repos).
        val conn = if (BuildConfig.UPDATE_TOKEN.isNotEmpty()) connect(release.apiUrl, "application/octet-stream")
        else connect(release.downloadUrl, "application/octet-stream")
        val sha = MessageDigest.getInstance("SHA-256")
        try {
            if (conn.responseCode !in 200..299) error("Download failed (${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.size
            var read = 0L
            var lastShown = 0f
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        sha.update(buf, 0, n)
                        read += n
                        val p = if (total > 0) (read.toFloat() / total).coerceIn(0f, 1f) else 0f
                        if (p - lastShown >= 0.01f) {
                            lastShown = p
                            _state.value = UpdateState.Downloading(release, p)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        release.digest?.let { expected ->
            val actual = "sha256:" + sha.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expected, ignoreCase = true)) {
                file.delete()
                error("The download was damaged. Try again.")
            }
        }
        return file
    }

    private fun startInstall(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
    }

    // ---------------------------------------------------------------- Helpers

    /** True if [a] is a later version than [b], comparing dotted numbers ("1.0.10" > "1.0.9"). */
    fun isNewer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    private fun Release.toJson() = JSONObject()
        .put("version", version).put("notes", notes).put("apiUrl", apiUrl)
        .put("downloadUrl", downloadUrl).put("size", size).put("digest", digest ?: "")
        .toString()

    private fun restore(json: String?) {
        json ?: return
        runCatching {
            val o = JSONObject(json)
            val r = Release(o.getString("version"), o.optString("notes"), o.getString("apiUrl"), o.getString("downloadUrl"), o.optLong("size"), o.optString("digest").ifEmpty { null })
            if (isNewer(r.version, currentVersion)) _state.value = UpdateState.Available(r)
        }
    }
}

/** Hears back from the package installer: shows Android's confirm screen, or reports the result. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT))
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
            return
        }
        Updater.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
