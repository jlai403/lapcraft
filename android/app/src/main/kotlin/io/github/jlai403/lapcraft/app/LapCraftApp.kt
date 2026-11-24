package io.github.jlai403.lapcraft.app

import android.app.Application
import io.github.jlai403.lapcraft.app.telemetry.Telemetry

class LapCraftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.init(this)
        Telemetry.initIfEnabled(this)
    }
}
