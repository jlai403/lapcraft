package io.github.jlai403.lapcraft.app.telemetry

import android.content.Context
import android.content.pm.PackageManager
import com.posthog.PersonProfiles
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import io.github.jlai403.lapcraft.BuildConfig
import io.github.jlai403.lapcraft.app.settings.SettingsStore

/**
 * Thin wrapper around the PostHog SDK. Nothing else in the app imports
 * PostHog directly, so the SDK can be swapped or removed in one place.
 *
 * Consent is explicit and off by default: [initIfEnabled] does nothing
 * unless the user opted in, and [setEnabled] is the only way to turn it
 * on. No network call happens while disabled.
 */
object Telemetry {
    @Volatile
    private var enabled: Boolean = false

    fun initIfEnabled(context: Context) {
        if (SettingsStore(context).analyticsEnabled) {
            enable(context)
        }
    }

    fun setEnabled(context: Context, value: Boolean) {
        SettingsStore(context).analyticsEnabled = value
        if (value) enable(context) else disable()
    }

    private fun enable(context: Context) {
        if (enabled) return
        if (BuildConfig.POSTHOG_KEY.isBlank()) return
        val installer = runCatching {
            context.packageManager.getInstallerPackageName(context.packageName)
        }.getOrNull()
        val isPlayInstall = installer == "com.android.vending"
        val config = PostHogAndroidConfig(
            apiKey = BuildConfig.POSTHOG_KEY,
            host = BuildConfig.POSTHOG_HOST,
        ).apply {
            captureApplicationLifecycleEvents = true
            personProfiles = PersonProfiles.NEVER
            disableGeoIp = true
            // Capture uncaught exceptions only for non-Play installs, which
            // have no Play Console vitals coverage. Play installs already get
            // crash stacks there, so capturing here would double count.
            errorTrackingConfig.autoCapture = !isPlayInstall
            sessionReplay = false
            surveys = false
        }
        PostHogAndroid.setup(context.applicationContext, config)
        enabled = true
    }

    private fun disable() {
        if (!enabled) return
        PostHog.optOut()
        enabled = false
    }

    fun isEnabled(): Boolean = enabled

    fun capture(event: String, properties: Map<String, Any> = emptyMap()) {
        if (!enabled) return
        PostHog.capture(event, properties = properties)
    }

    fun screen(screen: String, section: String? = null) {
        if (!enabled) return
        val props = linkedMapOf<String, Any>("screen" to screen)
        section?.let { props["section"] = it }
        PostHog.capture("screen_viewed", properties = props)
    }

    fun failed(reason: String, properties: Map<String, Any> = emptyMap()) {
        if (!enabled) return
        PostHog.capture("trim_failed", properties = properties + ("reason" to reason))
    }
}