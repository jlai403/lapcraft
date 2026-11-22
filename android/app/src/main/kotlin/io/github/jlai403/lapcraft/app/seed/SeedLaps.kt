package io.github.jlai403.lapcraft.app.seed

import android.content.Context
import android.util.Log
import io.github.jlai403.lapcraft.app.settings.SettingsStore
import io.github.jlai403.lapcraft.core.session.LapStore
import io.github.jlai403.lapcraft.core.session.LibraryArchive
import io.github.jlai403.lapcraft.core.session.ImportReport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.temporal.ChronoUnit

object SeedLaps {
    const val VERSION = 1
    private const val ASSET = "seed-laps.zip"
    private val mutex = Mutex()
    private const val TAG = "SeedLaps"

    fun shouldSeed(lapCount: Int, seedVersion: Int, previewEmpty: Boolean): Boolean =
        lapCount == 0 && seedVersion < VERSION && !previewEmpty

    suspend fun importIfNeeded(
        context: Context,
        store: LapStore,
        settings: SettingsStore,
        previewEmpty: Boolean,
        rootDir: java.io.File = context.filesDir,
    ): ImportReport? = mutex.withLock {
        if (!shouldSeed(store.list().size, settings.seedVersion, previewEmpty)) return null
        val before = store.list().toSet()
        val report = runCatching {
            context.assets.open(ASSET).use { input ->
                LibraryArchive.import(rootDir, input)
            }
        }.getOrElse { e ->
            Log.w(TAG, "seed import failed", e)
            return null
        }
        val now = Instant.now()
        (store.list().toSet() - before).sortedBy { it.value }.forEachIndexed { i, id ->
            val record = store.load(id) ?: return@forEachIndexed
            store.save(id, record.copy(capturedAt = now.minus(2L * i, ChronoUnit.MINUTES).toString()))
        }
        settings.seedVersion = VERSION
        Log.i(TAG, "seeded ${report.laps} lap(s)")
        return report
    }
}
