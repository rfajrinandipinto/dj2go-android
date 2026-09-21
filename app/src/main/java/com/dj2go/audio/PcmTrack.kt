package com.dj2go.audio

import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.MappedByteBuffer

/**
 * A fully decoded track: interleaved stereo 16-bit PCM backed by a memory
 * mapped temporary file (so long tracks do not sit on the Java heap).
 *
 * Frames are read on the audio render thread only.
 */
class PcmTrack(
    private val source: RandomAccessFile,
    private val buffer: MappedByteBuffer,
    val frameCount: Int,
    val sampleRate: Int,
    val waveform: WaveformData,
    val beatGrid: BeatGrid,
    val key: String,
    val camelot: String
) {
    init {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
    }

    fun left(frame: Int): Int = buffer.getShort(frame shl 2).toInt()

    fun right(frame: Int): Int = buffer.getShort((frame shl 2) + 2).toInt()

    val durationMs: Long get() = frameCount.toLong() * 1000L / sampleRate

    fun close() {
        runCatching { source.close() }
    }
}
