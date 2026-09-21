package com.dj2go.audio

/**
 * Pre-computed waveform envelope for one track: three frequency bands per
 * bucket (0..255). Used by the UI to draw a Serato-style coloured waveform.
 */
class WaveformData(
    val bucketFrames: Int,
    val low: ByteArray,
    val mid: ByteArray,
    val high: ByteArray
) {
    val bucketCount: Int get() = low.size
}
