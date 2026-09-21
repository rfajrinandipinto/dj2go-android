package com.dj2go.audio

import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import kotlin.math.exp

/**
 * Estimates a track's tempo and first beat from its PCM.
 *
 * Low-passed energy is reduced to an onset novelty curve, then autocorrelated
 * over a plausible BPM range; the strongest period gives the tempo and a
 * pulse-train correlation gives the downbeat offset. This is a lightweight
 * detector, good enough for beat-syncing two tracks.
 */
object BeatDetector {

    private const val HOP = 512
    private const val MIN_BPM = 70f
    private const val MAX_BPM = 190f

    fun detect(buffer: MappedByteBuffer, frameCount: Int, sampleRate: Int): BeatGrid {
        if (frameCount < sampleRate * 6) return BeatGrid.EMPTY
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        val envelope = buildEnvelope(buffer, frameCount, sampleRate)
        val count = envelope.size
        if (count < 16) return BeatGrid.EMPTY

        val novelty = FloatArray(count)
        var mean = 0f
        for (i in 1 until count) {
            novelty[i] = (envelope[i] - envelope[i - 1]).coerceAtLeast(0f)
            mean += novelty[i]
        }
        mean /= count
        if (mean <= 1e-7f) return BeatGrid.EMPTY
        for (i in 0 until count) novelty[i] -= mean

        val hopRate = sampleRate.toFloat() / HOP
        val minLag = (60f * hopRate / MAX_BPM).toInt().coerceAtLeast(1)
        val maxLag = (60f * hopRate / MIN_BPM).toInt().coerceAtMost(count - 2)
        if (maxLag <= minLag) return BeatGrid.EMPTY

        var bestLag = minLag
        var bestScore = -Float.MAX_VALUE
        for (lag in minLag..maxLag) {
            var sum = 0f
            var i = lag
            while (i < count) {
                sum += novelty[i] * novelty[i - lag]
                i++
            }
            if (sum > bestScore) {
                bestScore = sum
                bestLag = lag
            }
        }

        val refinedLag = refinePeak(novelty, bestLag, minLag, maxLag)
        var bpm = (60f * hopRate / refinedLag).toFloat()
        while (bpm < 80f) bpm *= 2f
        while (bpm > 170f) bpm /= 2f
        if (bpm < MIN_BPM || bpm > MAX_BPM) return BeatGrid.EMPTY

        val periodHops = (60f * hopRate / bpm).toDouble()
        val step = periodHops.toInt().coerceAtLeast(1)

        var bestOffset = 0
        var bestPhase = -Float.MAX_VALUE
        for (offset in 0 until step) {
            var sum = 0f
            var i = offset
            while (i < count) {
                sum += novelty[i]
                i += step
            }
            if (sum > bestPhase) {
                bestPhase = sum
                bestOffset = offset
            }
        }

        val firstBeatFrames = (bestOffset * HOP).toLong()
        val periodFrames = 60.0 * sampleRate / bpm
        return BeatGrid(bpm, firstBeatFrames, periodFrames)
    }

    private fun buildEnvelope(
        buffer: MappedByteBuffer,
        frameCount: Int,
        sampleRate: Int
    ): FloatArray {
        val size = frameCount / HOP + 1
        val envelope = FloatArray(size)
        val coefficient = (1.0 - exp(-2.0 * Math.PI * 150.0 / sampleRate)).toFloat()

        var lowPass = 0f
        var accumulator = 0f
        var bucket = 0
        var frame = 0
        while (frame < frameCount && bucket < size) {
            val index = frame shl 2
            val left = buffer.getShort(index).toInt()
            val right = buffer.getShort(index + 2).toInt()
            val mono = (left + right) * 0.5f / 32768f
            lowPass += coefficient * (mono - lowPass)
            accumulator += lowPass * lowPass

            frame++
            if (frame % HOP == 0) {
                envelope[bucket++] = accumulator
                accumulator = 0f
            }
        }
        return envelope
    }

    /** Parabolic interpolation around the autocorrelation peak. */
    private fun refinePeak(novelty: FloatArray, lag: Int, minLag: Int, maxLag: Int): Double {
        if (lag <= minLag || lag >= maxLag) return lag.toDouble()
        val score = { l: Int ->
            var sum = 0f
            var i = l
            while (i < novelty.size) {
                sum += novelty[i] * novelty[i - l]
                i++
            }
            sum
        }
        val y0 = score(lag - 1)
        val y1 = score(lag)
        val y2 = score(lag + 1)
        val denominator = (y0 - 2f * y1 + y2)
        if (denominator == 0f) return lag.toDouble()
        val delta = 0.5f * (y0 - y2) / denominator
        return (lag + delta).toDouble().coerceIn(minLag.toDouble(), maxLag.toDouble())
    }
}
