package io.github.jlai403.lapcraft.app.replay

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.StatFs
import android.util.Log
import io.github.jlai403.lapcraft.app.Diagnostics
import io.github.jlai403.lapcraft.core.clip.TrimRange
import io.github.jlai403.lapcraft.core.clip.TrimTail
import java.io.File
import java.nio.ByteBuffer

data class TrimResult(val output: File, val startOffsetMs: Int)

sealed interface TrimOutcome {
    data class Success(val result: TrimResult) : TrimOutcome
    data class Failure(val reason: TrimFailureReason) : TrimOutcome
}

enum class TrimFailureReason {
    SourceMissing,
    NoVideoTrack,
    NoKeyframe,
    SampleTooLarge,
    MuxerUnsupported,
    NoSpace,
    SwapFailed,
    Unknown,
}

/** Maps a trim failure to the short message shown on the sync screen. Pure, unit-testable. */
fun trimFailureMessage(reason: TrimFailureReason, hadClip: Boolean): String {
    val base = when (reason) {
        TrimFailureReason.SourceMissing -> "The replay file could not be read."
        TrimFailureReason.NoVideoTrack -> "No video track was found in the replay."
        TrimFailureReason.NoKeyframe -> "No usable keyframe exists in the trim range."
        TrimFailureReason.SampleTooLarge -> "A video frame is too large to trim; try a lower-resolution clip."
        TrimFailureReason.MuxerUnsupported -> "This clip's format is not supported for trimming."
        TrimFailureReason.NoSpace -> "Not enough free space to trim this clip."
        TrimFailureReason.SwapFailed -> "Could not write the trimmed replay."
        TrimFailureReason.Unknown -> "Trim failed."
    }
    val suffix = if (hadClip) {
        " The current replay is unchanged; try again."
    } else {
        " No replay was attached; try again."
    }
    return base + suffix
}

class ClipTrimmer {
    fun trim(source: File, output: File, range: TrimRange): TrimOutcome {
        if (!source.isFile || range.endMs <= range.startMs) {
            return TrimOutcome.Failure(TrimFailureReason.NoKeyframe)
        }

        // Conservative space check: the temp, the backup and the output all live in the same
        // directory, and the trimmed clip is never larger than the source.
        output.parentFile?.let { dir ->
            runCatching { dir.mkdirs() }
            val stat = runCatching { StatFs(dir.absolutePath) }.getOrNull()
            if (stat != null && stat.availableBytes < source.length()) {
                Diagnostics.log("clip trim: not enough space, available=${stat.availableBytes}, source=${source.length()}")
                return TrimOutcome.Failure(TrimFailureReason.NoSpace)
            }
        }

        val extractor = MediaExtractor()
        val temp = File(output.absolutePath + ".tmp")
        var muxer: MediaMuxer? = null
        var started = false
        var result: TrimOutcome = TrimOutcome.Failure(TrimFailureReason.Unknown)
        var bufferTooSmall = false
        try {
            try {
                extractor.setDataSource(source.absolutePath)
            } catch (t: Throwable) {
                Diagnostics.log("clip trim: setDataSource failed: ${t.message}")
                return TrimOutcome.Failure(TrimFailureReason.SourceMissing)
            }

            var videoIndex = -1
            var audioIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") && videoIndex < 0) {
                    videoIndex = index
                    videoFormat = format
                } else if (mime.startsWith("audio/") && audioIndex < 0) {
                    audioIndex = index
                    audioFormat = format
                }
            }
            if (videoIndex < 0 || videoFormat == null) {
                return TrimOutcome.Failure(TrimFailureReason.NoVideoTrack)
            }
            val video = videoFormat
            val audio = audioFormat

            extractor.selectTrack(videoIndex)
            if (audioIndex >= 0) extractor.selectTrack(audioIndex)

            val startUs = range.startMs * 1_000
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val maxInputSize = runCatching { video.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }
                .getOrDefault(0).coerceAtLeast(0)
            var bufferCapacity = (maxInputSize.takeIf { it > 0 } ?: (1 shl 20)).coerceAtLeast(1 shl 20)
            var buffer = ByteBuffer.allocateDirect(bufferCapacity)

            var keyframeUs: Long? = null
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    if (extractor.sampleTime >= 0) {
                        // The sample did not fit in the buffer; grow and retry once.
                        if (bufferCapacity < (64 shl 20)) {
                            bufferCapacity = (bufferCapacity * 2).coerceAtMost(64 shl 20)
                            buffer = ByteBuffer.allocateDirect(bufferCapacity)
                            continue
                        }
                        bufferTooSmall = true
                    }
                    break
                }
                if (extractor.sampleTrackIndex == videoIndex) {
                    keyframeUs = extractor.sampleTime
                    break
                }
                extractor.advance()
            }
            val keyframe = keyframeUs ?: return TrimOutcome.Failure(
                if (bufferTooSmall) TrimFailureReason.SampleTooLarge else TrimFailureReason.NoKeyframe,
            )
            extractor.seekTo(keyframe, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            output.parentFile?.mkdirs()
            temp.delete()
            val mux = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            val muxVideo = try {
                mux.addTrack(video)
            } catch (e: IllegalArgumentException) {
                Diagnostics.log("clip trim: addTrack(video) failed: ${e.message}")
                return TrimOutcome.Failure(TrimFailureReason.MuxerUnsupported)
            }
            val muxAudio = try {
                audio?.let { mux.addTrack(it) } ?: -1
            } catch (e: IllegalArgumentException) {
                Diagnostics.log("clip trim: addTrack(audio) failed, dropping audio: ${e.message}")
                -1
            }
            mux.start()
            started = true

            val endUs = range.endMs * 1_000
            val tail = TrimTail(endUs)
            val info = MediaCodec.BufferInfo()
            var samples = 0
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    if (extractor.sampleTime >= 0) {
                        if (bufferCapacity < (64 shl 20)) {
                            bufferCapacity = (bufferCapacity * 2).coerceAtMost(64 shl 20)
                            buffer = ByteBuffer.allocateDirect(bufferCapacity)
                            continue
                        }
                        bufferTooSmall = true
                    }
                    break
                }
                val time = extractor.sampleTime
                val track = extractor.sampleTrackIndex
                if (time in keyframe..endUs) {
                    val muxTrack = if (track == videoIndex) muxVideo else muxAudio
                    if (muxTrack >= 0) {
                        val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                        } else {
                            0
                        }
                        info.set(0, size, time - keyframe, flags)
                        mux.writeSampleData(muxTrack, buffer, info)
                        samples++
                    }
                }
                if (tail.onSample(video = track == videoIndex, timeUs = time)) break
                extractor.advance()
            }

            if (samples <= 0) {
                return TrimOutcome.Failure(
                    if (bufferTooSmall) TrimFailureReason.SampleTooLarge else TrimFailureReason.NoKeyframe,
                )
            }
            mux.stop()
            started = false
            if (temp.length() <= 0L) {
                Diagnostics.log("clip trim: muxer produced an empty file")
                return TrimOutcome.Failure(TrimFailureReason.Unknown)
            }
            if (!swap(temp, output)) {
                return TrimOutcome.Failure(TrimFailureReason.SwapFailed)
            }
            result = TrimOutcome.Success(TrimResult(output, ((startUs - keyframe + 500) / 1_000).toInt()))
        } catch (t: Throwable) {
            Diagnostics.log("clip trim: unexpected failure: ${t.message}")
            Log.w("ClipTrimmer", "trim failed", t)
            result = TrimOutcome.Failure(TrimFailureReason.Unknown)
        } finally {
            runCatching { if (started) muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            if (result is TrimOutcome.Failure) temp.delete()
        }
        return result
    }

    private fun swap(temp: File, output: File): Boolean {
        val backup = File(output.absolutePath + ".bak")
        backup.delete()
        val hadOutput = output.exists()
        if (hadOutput && !output.renameTo(backup)) return false
        if (temp.renameTo(output)) {
            backup.delete()
            return true
        }
        if (hadOutput) backup.renameTo(output)
        return false
    }

    private companion object {
        const val BUFFER_SIZE = 1 shl 20
    }
}