package com.shilapi.xcertplay.media

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface

/**
 * Manages video decoder initialization with fallback logic for head unit chipsets (e.g. Desay SV):
 * 1080p 60fps -> 720p 30fps -> Software Decoder.
 */
class VideoCodecFallbackManager(
    private val codecCreator: ((mimeType: String) -> MediaCodec)? = null,
    private val formatFactory: ((mimeType: String, width: Int, height: Int, fps: Int) -> MediaFormat?)? = null,
    private val logger: ((tag: String, msg: String, isError: Boolean) -> Unit)? = null,
) {

    @Volatile
    var mediaCodec: MediaCodec? = null
        private set

    @Volatile
    var currentWidth: Int = 0
        private set

    @Volatile
    var currentHeight: Int = 0
        private set

    @Volatile
    var currentFps: Int = 0
        private set

    @Volatile
    var isSoftwareDecoderRequired: Boolean = false
        private set

    fun initVideoCodecWithFallback(
        surface: Surface? = null,
        width: Int = 1920,
        height: Int = 1080,
        fps: Int = 60,
    ) {
        try {
            val format = try {
                formatFactory?.invoke(MediaFormat.MIMETYPE_VIDEO_AVC, width, height, fps)
                    ?: MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                    }
            } catch (_: Throwable) {
                null
            }

            val codec = codecCreator?.invoke(MediaFormat.MIMETYPE_VIDEO_AVC)
                ?: MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)

            if (format != null) {
                codec.configure(format, surface, null, 0)
            }
            codec.start()

            mediaCodec = codec
            currentWidth = width
            currentHeight = height
            currentFps = fps
            isSoftwareDecoderRequired = false
            logMessage("VideoCodec", "Successfully initialized video decoder for $width x $height @ $fps fps", isError = false)

        } catch (e: Exception) {
            logMessage(
                "VideoCodec",
                "Failed to init $width x $height @ $fps fps. Falling back to 720p 30fps.",
                isError = false,
            )

            if (width == 1920) {
                // Resolution လျှော့ချ၍ ပြန်လည် စတင်ခြင်း
                initVideoCodecWithFallback(surface, 1280, 720, 30)
            } else {
                logMessage("VideoCodec", "Software decoder required.", isError = true)
                isSoftwareDecoderRequired = true
            }
        }
    }

    fun release() {
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (_: Throwable) {
        } finally {
            mediaCodec = null
        }
    }

    private fun logMessage(tag: String, msg: String, isError: Boolean) {
        if (logger != null) {
            logger.invoke(tag, msg, isError)
        } else {
            try {
                if (isError) {
                    Log.e(tag, msg)
                } else {
                    Log.w(tag, msg)
                }
            } catch (_: Throwable) {
                println("[$tag] $msg")
            }
        }
    }
}
