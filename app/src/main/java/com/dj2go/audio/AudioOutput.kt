package com.dj2go.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Owns the output [AudioTrack]s.
 *
 * Routing strategy:
 *  - If the master device can carry 4 channels, a single quad track is used:
 *    channels 0/1 = master, channels 2/3 = cue (this is the DJ2GO2's own sound
 *    card: master jack on 1/2, headphone jack on 3/4).
 *  - Otherwise the master goes out as stereo and, if a different cue device is
 *    selected, a second stereo track carries the cue bus to that device.
 */
class AudioOutput(
    masterDevice: AudioDeviceInfo?,
    cueDevice: AudioDeviceInfo?,
    private val sampleRate: Int
) {
    val mainChannels: Int
    val hasSeparateCue: Boolean
    private val mainTrack: AudioTrack?
    private val cueTrack: AudioTrack?

    init {
        val sameDevice =
            masterDevice != null && cueDevice != null && masterDevice.id == cueDevice.id
        val wantQuad = supportsQuad(masterDevice) && (cueDevice == null || sameDevice)

        var channels = if (wantQuad) 4 else 2
        var main = createTrack(masterDevice, channels)
        if (main == null && channels == 4) {
            channels = 2
            main = createTrack(masterDevice, 2)
        }

        mainChannels = channels
        mainTrack = main
        hasSeparateCue = main != null && channels == 2 && cueDevice != null &&
            (masterDevice == null || cueDevice.id != masterDevice.id)
        cueTrack = if (hasSeparateCue) createTrack(cueDevice, 2) else null

        mainTrack?.play()
        cueTrack?.play()
    }

    val isReady: Boolean get() = mainTrack?.state == AudioTrack.STATE_INITIALIZED

    fun writeMain(buffer: ShortArray, frames: Int): Int {
        val track = mainTrack ?: return -1
        return track.write(buffer, 0, frames * mainChannels)
    }

    fun writeCue(buffer: ShortArray, frames: Int): Int {
        val track = cueTrack ?: return 0
        return track.write(buffer, 0, frames * 2)
    }

    fun pause() {
        runCatching { mainTrack?.pause() }
        runCatching { cueTrack?.pause() }
    }

    fun release() {
        runCatching { mainTrack?.stop() }
        runCatching { mainTrack?.release() }
        runCatching { cueTrack?.stop() }
        runCatching { cueTrack?.release() }
    }

    private fun createTrack(device: AudioDeviceInfo?, channels: Int): AudioTrack? {
        val mask = if (channels == 4) {
            AudioFormat.CHANNEL_OUT_QUAD
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT)
        val bufferBytes = if (minBytes > 0) minBytes * 2 else sampleRate * channels * 2 / 5

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(mask)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return null
        }
        if (device != null) track.setPreferredDevice(device)
        return track
    }

    companion object {
        fun supportsQuad(device: AudioDeviceInfo?): Boolean {
            val counts = device?.channelCounts ?: return false
            return counts.any { it >= 4 }
        }
    }
}
