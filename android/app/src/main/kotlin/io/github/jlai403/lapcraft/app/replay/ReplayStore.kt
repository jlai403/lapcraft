package io.github.jlai403.lapcraft.app.replay

import android.content.Context
import android.net.Uri
import java.io.File

object ReplayStore {
    fun sourceFile(sessionDir: File, lapIndex: Int): File =
        File(sessionDir, "source.mp4")

    fun copy(context: Context, uri: Uri, destination: File): Boolean {
        val temp = File(destination.absolutePath + ".tmp")
        return try {
            destination.parentFile?.mkdirs()
            val input = context.contentResolver.openInputStream(uri)
                ?: run {
                    temp.delete()
                    return false
                }
            input.use { source ->
                temp.outputStream().use { output -> source.copyTo(output) }
            }
            if (temp.length() <= 0L) {
                temp.delete()
                return false
            }
            destination.delete()
            if (!temp.renameTo(destination)) {
                temp.delete()
                false
            } else {
                true
            }
        } catch (_: Exception) {
            temp.delete()
            false
        }
    }
}
