package com.dj2go.audio

import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Musical key plus its Camelot-wheel equivalent. */
class KeyResult(val name: String, val camelot: String)

/**
 * Estimates the musical key from the PCM: an FFT per frame folds energy into 12
 * chroma bins, which are then correlated against the Krumhansl-Kessler major
 * and minor profiles.
 */
object KeyDetector {

    private const val FFT_SIZE = 4096
    private const val MAX_FRAMES = 80

    private val MAJOR = doubleArrayOf(
        6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88
    )
    private val MINOR = doubleArrayOf(
        6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17
    )
    private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val MAJOR_CAMELOT = intArrayOf(8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1)
    private val MINOR_CAMELOT = intArrayOf(5, 12, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10)

    fun detect(buffer: MappedByteBuffer, frameCount: Int, sampleRate: Int): KeyResult? {
        if (frameCount < FFT_SIZE * 4) return null
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        val chroma = DoubleArray(12)
        val re = DoubleArray(FFT_SIZE)
        val im = DoubleArray(FFT_SIZE)
        val window = DoubleArray(FFT_SIZE) {
            0.5 - 0.5 * cos(2.0 * PI * it / (FFT_SIZE - 1))
        }

        val step = ((frameCount - FFT_SIZE) / MAX_FRAMES).coerceAtLeast(1)
        var frame = 0
        var used = 0
        while (frame + FFT_SIZE <= frameCount && used < MAX_FRAMES) {
            for (i in 0 until FFT_SIZE) {
                val index = (frame + i) shl 2
                val l = buffer.getShort(index).toInt()
                val r = buffer.getShort(index + 2).toInt()
                re[i] = ((l + r) * 0.5 / 32768.0) * window[i]
                im[i] = 0.0
            }
            fft(re, im)
            for (k in 1 until FFT_SIZE / 2) {
                val freq = k.toDouble() * sampleRate / FFT_SIZE
                if (freq < 65.0 || freq > 2000.0) continue
                val magnitude = sqrt(re[k] * re[k] + im[k] * im[k])
                val midi = 69.0 + 12.0 * log2(freq / 440.0)
                val pitchClass = ((midi.roundToInt() % 12) + 12) % 12
                chroma[pitchClass] += magnitude
            }
            used++
            frame += step
        }

        val max = chroma.maxOrNull() ?: 0.0
        if (max <= 0.0) return null
        for (i in 0 until 12) chroma[i] /= max

        var bestScore = -Double.MAX_VALUE
        var bestTonic = 0
        var bestMinor = false
        for (tonic in 0 until 12) {
            val major = correlate(chroma, MAJOR, tonic)
            val minor = correlate(chroma, MINOR, tonic)
            if (major > bestScore) {
                bestScore = major; bestTonic = tonic; bestMinor = false
            }
            if (minor > bestScore) {
                bestScore = minor; bestTonic = tonic; bestMinor = true
            }
        }

        val name = NAMES[bestTonic] + if (bestMinor) "m" else ""
        val camelot = if (bestMinor) "${MINOR_CAMELOT[bestTonic]}A" else "${MAJOR_CAMELOT[bestTonic]}B"
        return KeyResult(name, camelot)
    }

    private fun correlate(chroma: DoubleArray, profile: DoubleArray, tonic: Int): Double {
        var sum = 0.0
        for (i in 0 until 12) sum += chroma[(i + tonic) % 12] * profile[i]
        return sum
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wr = cos(angle)
            val wi = sin(angle)
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * wRe - im[i + k + len / 2] * wIm
                    val vIm = re[i + k + len / 2] * wIm + im[i + k + len / 2] * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nextRe = wRe * wr - wIm * wi
                    wIm = wRe * wi + wIm * wr
                    wRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
