package com.dj2go.audio

/**
 * Per-deck 3-band EQ plus a DJ-style filter.
 *
 * Knob values are 0..127: EQ is centred at 64 (0 dB, +/-12 dB range); the
 * filter is centred at 64 (bypass), below sweeps a low-pass down, above sweeps
 * a high-pass up.
 */
class DeckEq {

    private val low = Biquad()
    private val mid = Biquad()
    private val high = Biquad()
    private val filter = Biquad()

    private var lastLow = Int.MIN_VALUE
    private var lastMid = Int.MIN_VALUE
    private var lastHigh = Int.MIN_VALUE
    private var lastFilter = Int.MIN_VALUE
    private var filterActive = false

    fun reset() {
        low.reset()
        mid.reset()
        high.reset()
        filter.reset()
        lastLow = Int.MIN_VALUE
        lastMid = Int.MIN_VALUE
        lastHigh = Int.MIN_VALUE
        lastFilter = Int.MIN_VALUE
        filterActive = false
    }

    /** Recomputes coefficients when a control changed. Call once per block. */
    fun update(sampleRate: Int, lowValue: Int, midValue: Int, highValue: Int, filterValue: Int) {
        if (lowValue != lastLow) {
            low.setLowShelf(sampleRate, 200f, eqDb(lowValue))
            lastLow = lowValue
        }
        if (midValue != lastMid) {
            mid.setPeaking(sampleRate, 1200f, eqDb(midValue), 0.7f)
            lastMid = midValue
        }
        if (highValue != lastHigh) {
            high.setHighShelf(sampleRate, 4000f, eqDb(highValue))
            lastHigh = highValue
        }
        if (filterValue != lastFilter) {
            filterActive = filterValue != 64
            when {
                filterValue < 64 -> {
                    // Low-pass sweeping from ~16 kHz down to ~120 Hz.
                    val t = filterValue / 64f
                    val freq = 120f + (16000f - 120f) * t * t
                    filter.setLowPass(sampleRate, freq.coerceIn(80f, 18000f), 0.9f)
                }
                filterValue > 64 -> {
                    // High-pass sweeping from ~40 Hz up to ~6 kHz.
                    val t = (filterValue - 64) / 63f
                    val freq = 40f + (6000f - 40f) * t * t
                    filter.setHighPass(sampleRate, freq.coerceIn(30f, 12000f), 0.9f)
                }
                else -> filterActive = false
            }
            lastFilter = filterValue
        }
    }

    fun processLeft(x: Float): Float {
        var y = low.processLeft(x)
        y = mid.processLeft(y)
        y = high.processLeft(y)
        if (filterActive) y = filter.processLeft(y)
        return y
    }

    fun processRight(x: Float): Float {
        var y = low.processRight(x)
        y = mid.processRight(y)
        y = high.processRight(y)
        if (filterActive) y = filter.processRight(y)
        return y
    }

    private fun eqDb(value: Int): Float {
        val t = (value - 64) / 64f
        return (t * 12f).coerceIn(-12f, 12f)
    }
}
