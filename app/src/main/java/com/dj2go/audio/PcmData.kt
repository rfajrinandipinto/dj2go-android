package com.dj2go.audio

/**
 * A short decoded sample kept fully in memory (used by the sampler).
 */
class PcmData(
    val samples: ShortArray,
    val sampleRate: Int
) {
    val frameCount: Int get() = samples.size / 2

    fun left(frame: Int): Int = samples[frame * 2].toInt()

    fun right(frame: Int): Int = samples[frame * 2 + 1].toInt()
}
