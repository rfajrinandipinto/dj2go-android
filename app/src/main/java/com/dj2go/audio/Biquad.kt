package com.dj2go.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A single RBJ biquad section. Coefficients are shared between the two stereo
 * channels; each channel keeps its own state ([processLeft] / [processRight]).
 */
class Biquad {

    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f

    private var lx1 = 0f
    private var lx2 = 0f
    private var ly1 = 0f
    private var ly2 = 0f
    private var rx1 = 0f
    private var rx2 = 0f
    private var ry1 = 0f
    private var ry2 = 0f

    fun setLowShelf(sampleRate: Int, frequency: Float, gainDb: Float) {
        val a = amp(gainDb)
        val w0 = omega(sampleRate, frequency)
        val cosw = cos(w0)
        val alpha = sin(w0) / 2f * sqrt(2f)
        val sqrtA = sqrt(a)
        val b0n = a * ((a + 1f) - (a - 1f) * cosw + 2f * sqrtA * alpha)
        val b1n = 2f * a * ((a - 1f) - (a + 1f) * cosw)
        val b2n = a * ((a + 1f) - (a - 1f) * cosw - 2f * sqrtA * alpha)
        val a0n = (a + 1f) + (a - 1f) * cosw + 2f * sqrtA * alpha
        val a1n = -2f * ((a - 1f) + (a + 1f) * cosw)
        val a2n = (a + 1f) + (a - 1f) * cosw - 2f * sqrtA * alpha
        set(b0n, b1n, b2n, a0n, a1n, a2n)
    }

    fun setPeaking(sampleRate: Int, frequency: Float, gainDb: Float, q: Float) {
        val a = amp(gainDb)
        val w0 = omega(sampleRate, frequency)
        val alpha = sin(w0) / (2f * q)
        val cosw = cos(w0)
        set(
            1f + alpha * a,
            -2f * cosw,
            1f - alpha * a,
            1f + alpha / a,
            -2f * cosw,
            1f - alpha / a
        )
    }

    fun setHighShelf(sampleRate: Int, frequency: Float, gainDb: Float) {
        val a = amp(gainDb)
        val w0 = omega(sampleRate, frequency)
        val cosw = cos(w0)
        val alpha = sin(w0) / 2f * sqrt(2f)
        val sqrtA = sqrt(a)
        val b0n = a * ((a + 1f) + (a - 1f) * cosw + 2f * sqrtA * alpha)
        val b1n = -2f * a * ((a - 1f) + (a + 1f) * cosw)
        val b2n = a * ((a + 1f) + (a - 1f) * cosw - 2f * sqrtA * alpha)
        val a0n = (a + 1f) - (a - 1f) * cosw + 2f * sqrtA * alpha
        val a1n = 2f * ((a - 1f) - (a + 1f) * cosw)
        val a2n = (a + 1f) - (a - 1f) * cosw - 2f * sqrtA * alpha
        set(b0n, b1n, b2n, a0n, a1n, a2n)
    }

    fun setLowPass(sampleRate: Int, frequency: Float, q: Float) {
        val w0 = omega(sampleRate, frequency)
        val alpha = sin(w0) / (2f * q)
        val cosw = cos(w0)
        set(
            (1f - cosw) / 2f,
            1f - cosw,
            (1f - cosw) / 2f,
            1f + alpha,
            -2f * cosw,
            1f - alpha
        )
    }

    fun setHighPass(sampleRate: Int, frequency: Float, q: Float) {
        val w0 = omega(sampleRate, frequency)
        val alpha = sin(w0) / (2f * q)
        val cosw = cos(w0)
        set(
            (1f + cosw) / 2f,
            -(1f + cosw),
            (1f + cosw) / 2f,
            1f + alpha,
            -2f * cosw,
            1f - alpha
        )
    }

    fun processLeft(x: Float): Float {
        val y = b0 * x + b1 * lx1 + b2 * lx2 - a1 * ly1 - a2 * ly2
        lx2 = lx1
        lx1 = x
        ly2 = ly1
        ly1 = y
        return y
    }

    fun processRight(x: Float): Float {
        val y = b0 * x + b1 * rx1 + b2 * rx2 - a1 * ry1 - a2 * ry2
        rx2 = rx1
        rx1 = x
        ry2 = ry1
        ry1 = y
        return y
    }

    fun reset() {
        lx1 = 0f; lx2 = 0f; ly1 = 0f; ly2 = 0f
        rx1 = 0f; rx2 = 0f; ry1 = 0f; ry2 = 0f
    }

    private fun set(b0n: Float, b1n: Float, b2n: Float, a0n: Float, a1n: Float, a2n: Float) {
        b0 = b0n / a0n
        b1 = b1n / a0n
        b2 = b2n / a0n
        a1 = a1n / a0n
        a2 = a2n / a0n
    }

    private fun amp(gainDb: Float) = 10f.pow(gainDb / 40f)

    private fun omega(sampleRate: Int, frequency: Float): Float =
        (2.0 * PI * frequency / sampleRate).toFloat()
}
