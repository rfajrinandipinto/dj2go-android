package com.dj2go.audio

import kotlin.math.PI
import kotlin.math.cos

/**
 * WSOLA (waveform-similarity overlap-add) time-stretcher: changes tempo while
 * preserving pitch, so the tempo fader behaves like key lock.
 *
 * Render-thread only. The caller owns the read position and calls [generate]
 * once per audio block.
 */
class TimeStretcher {

    private val window = FloatArray(FRAME) {
        0.5f - 0.5f * cos(2.0 * PI * it / (FRAME - 1)).toFloat()
    }
    private val olaL = FloatArray(FRAME)
    private val olaR = FloatArray(FRAME)
    private val frameL = FloatArray(FRAME)
    private val frameR = FloatArray(FRAME)

    private var naturalPos = 0.0
    private var primed = false

    fun reset() {
        primed = false
        olaL.fill(0f)
        olaR.fill(0f)
    }

    /**
     * Fills [outL]/[outR][0 until count) with tempo-shifted audio (pitch kept)
     * and returns the new source read position.
     */
    fun generate(
        track: PcmTrack,
        count: Int,
        speed: Float,
        position: Double,
        loopIn: Long,
        loopOut: Long,
        loopActive: Boolean,
        outL: FloatArray,
        outR: FloatArray
    ): Double {
        if (!primed) {
            naturalPos = position
            primed = true
            olaL.fill(0f)
            olaR.fill(0f)
        }
        var produced = 0
        val analysisHop = HOP * speed
        val lastFrame = track.frameCount - 1
        while (produced < count) {
            val search = searchOffset(track, naturalPos)
            val readPos = naturalPos + search
            readFrame(track, readPos, frameL, frameR)
            for (i in 0 until FRAME) {
                olaL[i] += frameL[i] * window[i]
                olaR[i] += frameR[i] * window[i]
            }
            val n = minOf(HOP, count - produced)
            for (i in 0 until n) {
                outL[produced + i] = olaL[i]
                outR[produced + i] = olaR[i]
            }
            produced += n

            System.arraycopy(olaL, HOP, olaL, 0, FRAME - HOP)
            System.arraycopy(olaR, HOP, olaR, 0, FRAME - HOP)
            java.util.Arrays.fill(olaL, FRAME - HOP, FRAME, 0f)
            java.util.Arrays.fill(olaR, FRAME - HOP, FRAME, 0f)

            naturalPos += analysisHop
            if (loopActive && loopIn >= 0 && loopOut > loopIn && naturalPos >= loopOut) {
                naturalPos = loopIn.toDouble()
                olaL.fill(0f)
                olaR.fill(0f)
            }
            if (naturalPos >= lastFrame) {
                naturalPos = lastFrame.toDouble()
                break
            }
        }
        for (i in produced until count) {
            outL[i] = 0f
            outR[i] = 0f
        }
        return naturalPos
    }

    private fun readFrame(track: PcmTrack, position: Double, outL: FloatArray, outR: FloatArray) {
        for (i in 0 until FRAME) {
            val p = position + i
            if (p < 0.0 || p >= track.frameCount) {
                outL[i] = 0f
                outR[i] = 0f
                continue
            }
            val i0 = p.toInt()
            val frac = (p - i0).toFloat()
            val i1 = if (i0 + 1 < track.frameCount) i0 + 1 else i0
            outL[i] = (track.left(i0) + (track.left(i1) - track.left(i0)) * frac) / 32768f
            outR[i] = (track.right(i0) + (track.right(i1) - track.right(i0)) * frac) / 32768f
        }
    }

    /** Small cross-correlation search to align the next frame with the previous tail. */
    private fun searchOffset(track: PcmTrack, base: Double): Double {
        var bestCorrelation = -Float.MAX_VALUE
        var bestOffset = 0.0
        var offset = -SEARCH
        while (offset <= SEARCH) {
            var correlation = 0f
            for (i in 0 until CORR_LEN) {
                val p = base + offset + i
                if (p < 0.0 || p >= track.frameCount) continue
                val i0 = p.toInt()
                val frac = (p - i0).toFloat()
                val i1 = if (i0 + 1 < track.frameCount) i0 + 1 else i0
                val sample = (track.left(i0) + (track.left(i1) - track.left(i0)) * frac) / 32768f
                correlation += olaL[i] * sample
            }
            if (correlation > bestCorrelation) {
                bestCorrelation = correlation
                bestOffset = offset.toDouble()
            }
            offset += SEARCH_STEP
        }
        return bestOffset
    }

    companion object {
        const val FRAME = 1024
        private const val HOP = FRAME / 2
        private const val SEARCH = 192
        private const val SEARCH_STEP = 6
        private const val CORR_LEN = 256
    }
}
