package com.dj2go.audio

/**
 * Per-deck beat-synced echo. The delay time follows the track BPM, so it stays
 * locked to the beat. Render-thread only.
 */
class DeckFx {

    private val size = 88_200 // 2 seconds at 44.1 kHz
    private val lineL = FloatArray(size)
    private val lineR = FloatArray(size)
    private var writePos = 0
    private var delay = 22_050.0
    private var wet = 0f
    private var enabled = false
    private val feedback = 0.45f

    fun reset() {
        lineL.fill(0f)
        lineR.fill(0f)
        writePos = 0
    }

    fun configure(sampleRate: Int, bpm: Float, beats: Float, wetValue: Int, on: Boolean) {
        val beatSeconds = if (bpm > 0f) 60.0 / bpm else 0.5
        delay = (beatSeconds * beats * sampleRate).coerceIn(1.0, (size - 2).toDouble())
        wet = (wetValue / 127f).coerceIn(0f, 1f)
        enabled = on && wet > 0f
    }

    fun process(left: Float, right: Float, out: FloatArray) {
        if (!enabled) {
            out[0] = left
            out[1] = right
            return
        }
        val read = writePos - delay
        val readPos = if (read < 0.0) read + size else read
        val i0 = readPos.toInt()
        val frac = (readPos - i0).toFloat()
        val i1 = if (i0 + 1 < size) i0 + 1 else 0
        val delayedL = lineL[i0] + (lineL[i1] - lineL[i0]) * frac
        val delayedR = lineR[i0] + (lineR[i1] - lineR[i0]) * frac

        lineL[writePos] = left + delayedL * feedback
        lineR[writePos] = right + delayedR * feedback
        out[0] = left + delayedL * wet * 0.8f
        out[1] = right + delayedR * wet * 0.8f

        writePos++
        if (writePos >= size) writePos = 0
    }

    companion object {
        /** Echo division in beats (1/2 beat = a classic 1/8 echo). */
        const val BEATS = 0.5f
    }
}
