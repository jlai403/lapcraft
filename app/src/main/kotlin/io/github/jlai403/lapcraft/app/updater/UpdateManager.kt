package io.github.jlai403.lapcraft.app.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import io.github.jlai403.lapcraft.BuildConfig
import io.github.jlai403.lapcraft.core.update.UpdateJsonCodec
import io.github.jlai403.lapcraft.core.update.UpdateManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Version check only. Updates are downloaded and installed by the browser, so the app never
 * requests the install-packages permission and Play Protect has nothing to flag.
 */
object UpdateManager {
    private const val PREVIEW_MANIFEST =
        "https://github.com/jlai403/lapcraft/releases/download/preview-latest/latest.json"
    private const val STABLE_MANIFEST =
        "https://github.com/jlai403/lapcraft/releases/latest/download/latest.json"
    private const val PREVIEW_PAGE = "https://github.com/jlai403/lapcraft/releases"
    private const val STABLE_PAGE = "https://github.com/jlai403/lapcraft/releases/latest"

    private const val PREFS = "updates"
    private const val LAST_CHECK = "last_check_ms"
    private const val STABLE_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    // Installers and updaters that manage their own updates: stay quiet for them.
    private val MANAGED_INSTALLERS = setOf(
        "org.fdroid.fdroid",
        "com.android.vending",
        "dev.imranr.obtainium",
        "dev.imranr.obtainium.fdroid",
    )

    private data class Channel(
        val manifestUrl: String,
        val pageUrl: String,
        val checkIntervalMs: Long,
    )

    private val channel: Channel = if (BuildConfig.UPDATE_CHANNEL == "stable") {
        Channel(STABLE_MANIFEST, STABLE_PAGE, STABLE_CHECK_INTERVAL_MS)
    } else {
        Channel(PREVIEW_MANIFEST, PREVIEW_PAGE, 0L)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val manifest: UpdateManifest) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    fun checkIfStale(context: Context) {
        val app = context.applicationContext
        if (managedByUpdater(app)) return
        val last = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(LAST_CHECK, 0)
        if (System.currentTimeMillis() - last < channel.checkIntervalMs) return
        if (_state.value !is State.Idle && _state.value !is State.UpToDate) return
        check(app)
    }

    fun check(context: Context) {
        val app = context.applicationContext
        if (managedByUpdater(app)) return
        if (_state.value is State.Checking) return
        _state.value = State.Checking
        scope.launch {
            val manifest = runCatching { fetchManifest() }
                .getOrElse {
                    _state.value = State.Failed("Update check failed: ${it.message}")
                    return@launch
                }
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(LAST_CHECK, System.currentTimeMillis())
                .apply()
            _state.value = if (manifest.isNewerThan(BuildConfig.VERSION_CODE)) {
                State.Available(manifest)
            } else {
                State.UpToDate
            }
        }
    }

    /** Opens the release page for this build's channel in the browser. */
    fun openReleasePage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(channel.pageUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun fetchManifest(): UpdateManifest {
        val connection = open(channel.manifestUrl)
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "HTTP ${connection.responseCode}"
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            return UpdateJsonCodec.decode(text) ?: error("Unreadable update manifest")
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
        }

    private fun managedByUpdater(context: Context): Boolean = runCatching {
        val manager = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= 30) {
            manager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            manager.getInstallerPackageName(context.packageName)
        }
        installer in MANAGED_INSTALLERS
    }.getOrDefault(false)
}
