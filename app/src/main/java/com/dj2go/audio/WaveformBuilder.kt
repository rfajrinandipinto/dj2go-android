package com.dj2go.audio

import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.exp

/**
 * Builds a [WaveformData] while PCM is decoded.
 *
 * A one-pole low pass and high pass split each sample into low / mid / high
 * energy; the peak of each band is stored once per bucket. This is a cheap
 * approximation of the spectral colouring Serato uses.
 */
class WaveformBuilder(sampleRate: Int) {

    private val lowOut = ByteArrayOutputStream()
    private val midOut = ByteArrayOutputStream()
    private val highOut = ByteArrayOutputStream()

    private val lowCoefficient = (1.0 - exp(-2.0 * Math.PI * 180.0 / sampleRate)).toFloat()
    private val highCoefficient = (1.0 - exp(-2.0 * Math.PI * 3000.0 / sampleRate)).toFloat()

    private var lowState = 0f
    private var highLowPass = 0f
    private var peakLow = 0f
    private var peakMid = 0f
    private var peakHigh = 0f
    private var frames = 0

    fun add(left: Int, right: Int) {
        val mono = (left + right) * 0.5f / 32768f

        lowState += lowCoefficient * (mono - lowState)
        highLowPass += highCoefficient * (mono - highLowPass)

        val low = lowState
        val high = mono - highLowPass
        val mid = mono - low - high

        peakLow = maxOf(peakLow, abs(low))
        peakMid = maxOf(peakMid, abs(mid))
        peakHigh = maxOf(peakHigh, abs(high))

        frames++
        if (frames >= BUCKET_FRAMES) flush()
    }

    fun finish(): WaveformData {
        if (frames > 0) flush()
        return WaveformData(
            BUCKET_FRAMES,
            lowOut.toByteArray(),
            midOut.toByteArray(),
            highOut.toByteArray()
        )
    }

    private fun flush() {
        lowOut.write(encode(peakLow))
        midOut.write(encode(peakMid))
        highOut.write(encode(peakHigh))
        peakLow = 0f
        peakMid = 0f
        peakHigh = 0f
        frames = 0
    }

    private fun encode(value: Float): Int = (value * 255f * 1.8f).toInt().coerceIn(0, 255)

    companion object {
        const val BUCKET_FRAMES = 512
    }
}
