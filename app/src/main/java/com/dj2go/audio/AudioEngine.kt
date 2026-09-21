package com.dj2go.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.dj2go.midi.ControlEvent
import com.dj2go.midi.ControlId
import com.dj2go.midi.Deck
import com.dj2go.midi.Mixer
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Software two-deck mixer.
 *
 * A dedicated render thread pulls frames from both decks, applies gain,
 * crossfader and tempo, builds a master bus and a cue (PFL) bus, and writes
 * them to [AudioOutput]. Cue is pre-crossfader, like a real DJ mixer.
 */
class AudioEngine(context: Context) {

    interface LoadCallback {
        fun onLoaded(deck: Deck, uri: Uri, durationMs: Long)
        fun onError(deck: Deck, message: String)
        fun onSampleLoaded(index: Int, name: String)
        fun onSampleError(index: Int, message: String)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val decoder = Executors.newSingleThreadExecutor()

    private val deckA = DeckPlayer()
    private val deckB = DeckPlayer()

    val sampler = Sampler()

    private val samplerL = FloatArray(BLOCK_FRAMES)
    private val samplerR = FloatArray(BLOCK_FRAMES)

    @Volatile private var crossfader = 64
    @Volatile private var cueMix = 0
    @Volatile private var masterGain = 100
    @Volatile private var headphoneGain = 100
    @Volatile private var crossfaderCurve = CrossfaderCurve.SMOOTH
    @Volatile private var tempoRange = 0.10f

    @Volatile
    var levelA = 0f
        private set

    @Volatile
    var levelB = 0f
        private set

    @Volatile
    var levelMaster = 0f
        private set

    private var masterDevice: AudioDeviceInfo? = null
    private var cueDevice: AudioDeviceInfo? = null
    private var output: AudioOutput? = null

    private var renderThread: Thread? = null

    @Volatile private var running = false
    private var loadCallback: LoadCallback? = null

    @Volatile private var recordingFlag = false
    private var recordFile: java.io.RandomAccessFile? = null
    private var recordDataBytes = 0L
    private val recordBuffer = ByteArray(BLOCK_FRAMES * 4)
    private var logSink: ((String) -> Unit)? = null

    @Volatile private var toneFrames = 0
    private var tonePhase = 0.0

    private val scratch = FloatArray(4)

    init {
        rebuildOutput()
        startRenderThread()
    }

    fun setLoadCallback(callback: LoadCallback?) {
        loadCallback = callback
    }

    fun applySettings(curve: CrossfaderCurve, range: Float) {
        crossfaderCurve = curve
        tempoRange = range
    }

    fun setLogSink(sink: ((String) -> Unit)?) {
        logSink = sink
    }

    private fun log(message: String) {
        mainHandler.post { logSink?.invoke(message) }
    }

    /** Plays a short 440 Hz tone so output routing can be checked by ear. */
    fun playTestTone() {
        tonePhase = 0.0
        toneFrames = SAMPLE_RATE
        log("test tone: playing")
    }

    fun deck(deck: Deck): DeckPlayer = if (deck == Deck.A) deckA else deckB

    fun outputChannels(): Int = output?.mainChannels ?: 0

    fun hasSeparateCue(): Boolean = output?.hasSeparateCue ?: false

    @Synchronized
    fun setOutputDevice(master: AudioDeviceInfo?, cue: AudioDeviceInfo?) {
        masterDevice = master
        cueDevice = cue
        stopRenderThread()
        output?.release()
        output = null
        rebuildOutput()
        startRenderThread()
    }

    fun load(deck: Deck, uri: Uri) {
        decoder.execute {
            try {
                val track = PcmDecoder.decode(appContext, uri)
                deck(deck).load(track)
                mainHandler.post { loadCallback?.onLoaded(deck, uri, track.durationMs) }
            } catch (t: Throwable) {
                mainHandler.post { loadCallback?.onError(deck, t.message ?: "decode failed") }
            }
        }
    }

    fun loadSample(index: Int, uri: Uri, name: String) {
        decoder.execute {
            try {
                val data = PcmDecoder.decodeToMemory(appContext, uri)
                sampler.assign(index, data, name)
                mainHandler.post { loadCallback?.onSampleLoaded(index, name) }
            } catch (t: Throwable) {
                mainHandler.post {
                    loadCallback?.onSampleError(index, t.message ?: "sample decode failed")
                }
            }
        }
    }

    fun samplerNames(): List<String> = sampler.names()

    fun samplerPlaying(): Set<Int> = sampler.playingIndices()

    // ---- mix recording ----

    fun isRecording(): Boolean = recordingFlag

    fun startRecording(): String? {
        if (recordingFlag) return null
        return try {
            val dir = java.io.File(appContext.getExternalFilesDir(null), "recordings")
            dir.mkdirs()
            val file = java.io.File(dir, "mix-${System.currentTimeMillis()}.wav")
            val raf = java.io.RandomAccessFile(file, "rw")
            raf.setLength(0)
            raf.write(wavHeader(0L))
            recordFile = raf
            recordDataBytes = 0L
            recordingFlag = true
            file.absolutePath
        } catch (t: Throwable) {
            recordFile = null
            null
        }
    }

    fun stopRecording() {
        if (!recordingFlag) return
        recordingFlag = false
        val raf = recordFile ?: return
        recordFile = null
        runCatching {
            raf.seek(4L)
            raf.write(intLe(36 + recordDataBytes))
            raf.seek(40L)
            raf.write(intLe(recordDataBytes))
            raf.close()
        }
    }

    private fun recordBlock(main: ShortArray, channels: Int) {
        val raf = recordFile ?: return
        var bi = 0
        var i = 0
        while (i < BLOCK_FRAMES) {
            val idx = i * channels
            val l = main[idx].toInt()
            val r = main[idx + 1].toInt()
            recordBuffer[bi++] = (l and 0xFF).toByte()
            recordBuffer[bi++] = ((l shr 8) and 0xFF).toByte()
            recordBuffer[bi++] = (r and 0xFF).toByte()
            recordBuffer[bi++] = ((r shr 8) and 0xFF).toByte()
            i++
        }
        runCatching { raf.write(recordBuffer, 0, bi) }
        recordDataBytes += bi
    }

    private fun wavHeader(dataBytes: Long): ByteArray {
        val header = ByteArray(44)
        val riff = 36 + dataBytes
        fun putString(offset: Int, text: String) {
            text.forEachIndexed { i, c -> header[offset + i] = c.code.toByte() }
        }
        fun putInt(offset: Int, value: Long) {
            header[offset] = (value and 0xFF).toByte()
            header[offset + 1] = ((value shr 8) and 0xFF).toByte()
            header[offset + 2] = ((value shr 16) and 0xFF).toByte()
            header[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
        fun putShort(offset: Int, value: Int) {
            header[offset] = (value and 0xFF).toByte()
            header[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }
        putString(0, "RIFF")
        putInt(4, riff)
        putString(8, "WAVE")
        putString(12, "fmt ")
        putInt(16, 16)
        putShort(20, 1)
        putShort(22, 2)
        putInt(24, SAMPLE_RATE.toLong())
        putInt(28, (SAMPLE_RATE * 2 * 2).toLong())
        putShort(32, 4)
        putShort(34, 16)
        putString(36, "data")
        putInt(40, dataBytes)
        return header
    }

    private fun intLe(value: Long): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    fun handle(event: ControlEvent, mixer: Mixer) {
        crossfader = mixer.crossfader
        cueMix = mixer.cueMix
        masterGain = mixer.masterGain
        headphoneGain = mixer.headphoneGain
        event.deck?.let { handleDeck(it, event, mixer) }
    }

    fun release() {
        stopRenderThread()
        output?.release()
        output = null
        decoder.shutdownNow()
    }

    // ---- control handling ----

    private fun handleDeck(deck: Deck, event: ControlEvent, mixer: Mixer) {
        val player = deck(deck)
        when (event.control) {
            ControlId.PLAY -> if (event.pressed) player.togglePlay()
            ControlId.CUE -> if (event.pressed) player.cue()
            ControlId.SYNC -> if (event.pressed) syncToOther(deck, mixer)
            ControlId.PFL -> if (event.pressed) player.pfl = mixer.deck(deck).pfl
            ControlId.RATE -> player.applySpeed(speedFromFader(event.value, tempoRange))
            ControlId.GAIN -> player.applyGain(event.value / 127f)
            ControlId.EQ_LOW -> player.eqLow = event.value
            ControlId.EQ_MID -> player.eqMid = event.value
            ControlId.EQ_HIGH -> player.eqHigh = event.value
            ControlId.FILTER -> player.filter = event.value
            ControlId.WHEEL_TOUCH -> if (event.pressed) player.wheelTouched = true else player.endScratch()
            ControlId.JOG -> {
                val sampleRate = player.track?.sampleRate ?: SAMPLE_RATE
                player.jogTick(event.delta, sampleRate * SCRATCH_SECONDS_PER_TICK, BLOCK_FRAMES)
            }
            ControlId.HOT_CUE -> if (event.pressed) player.hotCue(event.index)
            ControlId.LOOP_IN -> if (event.pressed) player.setLoopIn()
            ControlId.LOOP_OUT -> if (event.pressed) player.setLoopOut()
            ControlId.LOOP_TOGGLE -> if (event.pressed) player.toggleLoop()
            ControlId.RELOOP_STOP -> if (event.pressed) player.stopLoop()
            ControlId.BEAT_LOOP -> if (event.pressed) player.autoLoop(beatsForLoop(event.index))
            ControlId.BEAT_JUMP -> if (event.pressed) player.beatJump(event.value)
            ControlId.SAMPLER -> if (event.pressed) {
                val offset = if (deck == Deck.A) 0 else Sampler.SLOTS_PER_DECK
                sampler.trigger(offset + event.index)
            }
            else -> Unit
        }
    }

    private fun syncToOther(deck: Deck, mixer: Mixer) {
        val player = deck(deck)
        val other = if (deck == Deck.A) deckB else deckA
        val thisTrack = player.track
        val otherTrack = other.track

        val thisGrid = thisTrack?.beatGrid
        val otherGrid = otherTrack?.beatGrid
        val canBeatSync = thisGrid?.valid == true && otherGrid?.valid == true

        val targetSpeed = if (canBeatSync) {
            (otherGrid!!.bpm / thisGrid!!.bpm).coerceIn(1f - tempoRange, 1f + tempoRange)
        } else {
            other.speed
        }
        player.applySpeed(targetSpeed)

        if (canBeatSync) {
            val period = thisGrid.periodFrames
            val otherPhase = otherGrid.phase(other.positionFrames)
            val thisPhase = thisGrid.phase(player.positionFrames)
            var diff = (otherPhase - thisPhase) * period
            val half = period / 2.0
            if (diff > half) diff -= period
            if (diff < -half) diff += period
            player.seekRequest = (player.positionFrames + diff).toLong()
        }

        val fader = speedToFader(targetSpeed, tempoRange)
        if (deck == Deck.A) mixer.deckA.rate = fader else mixer.deckB.rate = fader
    }

    // ---- render thread ----

    private fun rebuildOutput() {
        output = AudioOutput(masterDevice, cueDevice, SAMPLE_RATE)
    }

    private fun startRenderThread() {
        running = true
        val thread = Thread({ renderLoop() }, "dj2go-mixer")
        thread.priority = Thread.MAX_PRIORITY
        renderThread = thread
        thread.start()
        log("mixer started: ${output?.mainChannels ?: 0}ch, ready=${output?.isReady}, cue=${output?.hasSeparateCue}")
    }

    private fun stopRenderThread() {
        running = false
        output?.pause()
        runCatching { renderThread?.join(500) }
        renderThread = null
    }

    private fun renderLoop() {
        val out = output
        if (out == null) {
            log("mixer: no output device")
            return
        }
        if (!out.isReady) {
            log("mixer: output not ready (AudioTrack failed)")
            return
        }

        val mainBuffer = ShortArray(BLOCK_FRAMES * out.mainChannels)
        val cueBuffer = if (out.hasSeparateCue) ShortArray(BLOCK_FRAMES * 2) else null

        while (running) {
            mixBlock(out.mainChannels, mainBuffer, cueBuffer)
            val written = out.writeMain(mainBuffer, BLOCK_FRAMES)
            if (written < 0) {
                log("mixer: write error $written")
                break
            }
            if (cueBuffer != null) out.writeCue(cueBuffer, BLOCK_FRAMES)
        }
        log("mixer stopped")
    }

    private fun mixBlock(channels: Int, main: ShortArray, cue: ShortArray?) {
        val master = masterGain / 127f
        val head = headphoneGain / 127f
        val cross = crossfader / 127f
        val (curveA, curveB) = when (crossfaderCurve) {
            CrossfaderCurve.SMOOTH -> {
                val angle = cross * (Math.PI / 2.0)
                cos(angle).toFloat() to sin(angle).toFloat()
            }
            CrossfaderCurve.LINEAR -> (1f - cross) to cross
            CrossfaderCurve.SHARP -> {
                val a = if (cross < 0.5f) 1f else (1f - cross) * 2f
                val b = if (cross > 0.5f) 1f else cross * 2f
                a to b
            }
        }
        val fadeA = curveA * master
        val fadeB = curveB * master
        val cueMixFactor = cueMix / 127f

        deckA.eq.update(SAMPLE_RATE, deckA.eqLow, deckA.eqMid, deckA.eqHigh, deckA.filter)
        deckB.eq.update(SAMPLE_RATE, deckB.eqLow, deckB.eqMid, deckB.eqHigh, deckB.filter)

        var peakA = 0f
        var peakB = 0f
        var peakMaster = 0f

        renderSamplers()
        renderTone()

        for (i in 0 until BLOCK_FRAMES) {
            sampleDeck(deckA, fadeA, head)
            peakA = maxOf(peakA, abs(scratch[0]), abs(scratch[1]))
            var mL = scratch[0]
            var mR = scratch[1]
            var cL = scratch[2]
            var cR = scratch[3]

            sampleDeck(deckB, fadeB, head)
            peakB = maxOf(peakB, abs(scratch[0]), abs(scratch[1]))
            mL += scratch[0]
            mR += scratch[1]
            cL += scratch[2]
            cR += scratch[3]

            mL += samplerL[i] * master
            mR += samplerR[i] * master

            peakMaster = maxOf(peakMaster, abs(mL), abs(mR))

            val idx = i * channels
            main[idx] = toShort(mL)
            main[idx + 1] = toShort(mR)

            // Cue output blends between the PFL bus and the master mix.
            val outCueL = cL + (mL - cL) * cueMixFactor
            val outCueR = cR + (mR - cR) * cueMixFactor
            if (channels == 4) {
                main[idx + 2] = toShort(outCueL)
                main[idx + 3] = toShort(outCueR)
            }
            if (cue != null) {
                cue[i * 2] = toShort(outCueL)
                cue[i * 2 + 1] = toShort(outCueR)
            }
        }

        levelA = peakA.coerceIn(0f, 1f)
        levelB = peakB.coerceIn(0f, 1f)
        levelMaster = peakMaster.coerceIn(0f, 1f)

        if (recordingFlag) recordBlock(main, channels)
    }

    private fun sampleDeck(deck: DeckPlayer, masterFactor: Float, headFactor: Float) {
        scratch[0] = 0f
        scratch[1] = 0f
        scratch[2] = 0f
        scratch[3] = 0f

        val track = deck.track ?: return

        val seek = deck.seekRequest
        if (seek >= 0) {
            deck.positionFrames = seek.toDouble()
            deck.seekRequest = DeckPlayer.UNSET
        }

        var pos = deck.positionFrames
        val lastFrame = (track.frameCount - 1).toDouble()

        val scratching = deck.wheelTouched
        val step: Double
        var scratchGain = 1f

        if (scratching) {
            val moving = System.nanoTime() - deck.scratchTickTime < SCRATCH_TIMEOUT_NS
            step = if (moving) deck.scratchVelocity else 0.0
            if (abs(step) < 0.0005) return // wheel held still: no output
            scratchGain = minOf(1.0, abs(step)).toFloat()
        } else {
            if (!deck.playing) return
            if (deck.loopActive && deck.loopInFrames >= 0 &&
                deck.loopOutFrames > deck.loopInFrames && pos >= deck.loopOutFrames
            ) {
                pos = deck.loopInFrames.toDouble()
            }
            step = (track.sampleRate.toDouble() / SAMPLE_RATE) * deck.speed
        }

        if (pos < 0.0) pos = 0.0
        if (pos > lastFrame) pos = lastFrame

        val i0 = pos.toInt()
        val frac = (pos - i0).toFloat()
        val i1 = if (i0 + 1 < track.frameCount) i0 + 1 else i0

        val l0 = track.left(i0)
        val r0 = track.right(i0)
        val l = deck.eq.processLeft((l0 + (track.left(i1) - l0) * frac) / 32768f)
        val r = deck.eq.processRight((r0 + (track.right(i1) - r0) * frac) / 32768f)

        var next = pos + step
        if (next < 0.0) next = 0.0
        if (next > lastFrame) next = lastFrame
        deck.positionFrames = next

        if (!scratching && next >= lastFrame) deck.playing = false

        val gain = deck.gain * scratchGain
        scratch[0] = l * gain * masterFactor
        scratch[1] = r * gain * masterFactor
        if (deck.pfl) {
            scratch[2] = l * gain * headFactor
            scratch[3] = r * gain * headFactor
        }
    }

    private fun renderSamplers() {
        java.util.Arrays.fill(samplerL, 0f)
        java.util.Arrays.fill(samplerR, 0f)
        for (slot in sampler.slots) {
            val data = slot.data ?: continue
            if (!slot.playing) continue
            var pos = slot.position
            val step = data.sampleRate.toDouble() / SAMPLE_RATE
            var i = 0
            while (i < BLOCK_FRAMES && pos < data.frameCount) {
                val f0 = pos.toInt()
                val frac = (pos - f0).toFloat()
                val f1 = if (f0 + 1 < data.frameCount) f0 + 1 else f0
                val l = (data.left(f0) + (data.left(f1) - data.left(f0)) * frac) / 32768f * slot.gain
                val r = (data.right(f0) + (data.right(f1) - data.right(f0)) * frac) / 32768f * slot.gain
                samplerL[i] += l
                samplerR[i] += r
                pos += step
                i++
            }
            if (pos >= data.frameCount) {
                slot.playing = false
                slot.position = 0.0
            } else {
                slot.position = pos
            }
        }
    }

    private fun renderTone() {
        if (toneFrames <= 0) return
        val increment = 2.0 * Math.PI * 440.0 / SAMPLE_RATE
        var i = 0
        while (i < BLOCK_FRAMES && toneFrames > 0) {
            val value = (Math.sin(tonePhase) * 0.25).toFloat()
            samplerL[i] += value
            samplerR[i] += value
            tonePhase += increment
            toneFrames--
            i++
        }
    }

    private fun toShort(value: Float): Short {
        val scaled = (value * 32767f).toInt()
        return when {
            scaled > 32767 -> Short.MAX_VALUE
            scaled < -32768 -> Short.MIN_VALUE
            else -> scaled.toShort()
        }
    }

    private fun beatsForLoop(index: Int): Int = when (index) {
        0 -> 1
        1 -> 2
        2 -> 4
        else -> 8
    }

    companion object {
        const val SAMPLE_RATE = 44100
        const val BLOCK_FRAMES = 1024
        private const val SCRATCH_SECONDS_PER_TICK = 0.03
        private const val SCRATCH_TIMEOUT_NS = 120_000_000L

        fun speedFromFader(value: Int, range: Float): Float = 1f + (value - 64) / 64f * range

        fun speedToFader(speed: Float, range: Float): Int =
            ((speed - 1f) / range * 64f + 64f).roundToInt().coerceIn(0, 127)
    }
}
