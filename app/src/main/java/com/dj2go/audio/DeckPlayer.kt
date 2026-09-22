package com.dj2go.audio

/**
 * Pure state for one deck. The render thread in [AudioEngine] reads the
 * volatile fields; the UI/MIDI thread writes them.
 *
 * Position is expressed in (possibly fractional) source frames and is owned by
 * the render thread. Requests from other threads are queued through
 * [seekRequest] to avoid torn reads.
 */
class DeckPlayer {

    @Volatile
    var track: PcmTrack? = null
        private set

    @Volatile
    var playing = false

    @Volatile
    var speed = 1f

    @Volatile
    var gain = 1f

    @Volatile
    var eqLow = 64

    @Volatile
    var eqMid = 64

    @Volatile
    var eqHigh = 64

    @Volatile
    var filter = 64

    /** Render-thread only. */
    val eq = DeckEq()

    @Volatile
    var keyLock = false

    /** Render-thread only: key-lock (WSOLA) block generation. */
    val stretcher = TimeStretcher()
    val blockL = FloatArray(AudioEngine.BLOCK_FRAMES)
    val blockR = FloatArray(AudioEngine.BLOCK_FRAMES)
    var blockReady = false

    @Volatile
    var pfl = false

    @Volatile
    var positionFrames = 0.0

    /** Pending seek in source frames, or [UNSET]. Written by UI, cleared by render. */
    @Volatile
    var seekRequest = UNSET

    @Volatile
    var loopActive = false

    @Volatile
    var loopInFrames = UNSET

    @Volatile
    var loopOutFrames = UNSET

    /** True while the capacitive jog wheel is being touched (scratch mode). */
    @Volatile
    var wheelTouched = false

    /** Scratch speed in source frames per output frame. */
    @Volatile
    var scratchVelocity = 0.0

    /** nanoTime of the last jog tick, used to detect "wheel stopped". */
    @Volatile
    var scratchTickTime = 0L

    private var cuePointFrames = 0L
    private val hotCues = LongArray(HOT_CUE_COUNT) { UNSET }

    fun load(track: PcmTrack) {
        playing = false
        this.track = track
        eq.reset()
        stretcher.reset()
        blockReady = false
        hotCues.fill(UNSET)
        loopActive = false
        loopInFrames = UNSET
        loopOutFrames = UNSET
        cuePointFrames = 0L
        positionFrames = 0.0
        seekRequest = 0L
    }

    fun togglePlay() {
        if (track != null) playing = !playing
    }

    fun cue() {
        playing = false
        seekRequest = cuePointFrames
    }

    fun applySpeed(value: Float) {
        speed = value
    }

    fun applyGain(value: Float) {
        gain = value.coerceIn(0f, 1f)
    }

    fun seekBy(deltaMs: Long) {
        seekRequest = positionFrames.toLong() + msToFrames(deltaMs)
    }

    /** A jog tick: scratch while the wheel is held, otherwise nudge the track. */
    fun jogTick(delta: Int, framesPerTick: Double, blockFrames: Int) {
        if (wheelTouched) {
            scratchVelocity = delta * framesPerTick / blockFrames
            scratchTickTime = System.nanoTime()
        } else {
            seekBy(delta * NON_TOUCH_SCRUB_MS)
        }
    }

    fun endScratch() {
        wheelTouched = false
        scratchVelocity = 0.0
    }

    fun hotCue(index: Int) {
        if (index !in hotCues.indices) return
        val mark = hotCues[index]
        if (mark == UNSET) {
            hotCues[index] = positionFrames.toLong()
        } else {
            seekRequest = mark
        }
    }

    fun setLoopIn() {
        loopInFrames = positionFrames.toLong()
    }

    fun setLoopOut() {
        loopOutFrames = positionFrames.toLong()
        if (loopInFrames != UNSET && loopOutFrames > loopInFrames) loopActive = true
    }

    fun toggleLoop() {
        if (loopInFrames != UNSET && loopOutFrames != UNSET) loopActive = !loopActive
    }

    fun stopLoop() {
        loopActive = false
        loopInFrames = UNSET
        loopOutFrames = UNSET
    }

    fun hotCuePositions(): LongArray = hotCues.copyOf()

    val cuePositionFrames: Long get() = cuePointFrames

    /** Set an auto-loop of [beats] beats starting at the next beat. */
    fun autoLoop(beats: Int) {
        val grid = track?.beatGrid ?: return
        if (!grid.valid) return
        val start = grid.nextBeat(positionFrames).toDouble()
        loopInFrames = start.toLong()
        loopOutFrames = (start + beats * grid.periodFrames).toLong()
        loopActive = true
    }

    /** Jump forward/back by [beats] beats (negative = backwards). */
    fun beatJump(beats: Int) {
        val grid = track?.beatGrid
        if (grid == null || !grid.valid) {
            seekBy(beats * 500L)
            return
        }
        seekRequest = (positionFrames + beats * grid.periodFrames).toLong()
    }

    fun durationMs(): Long = track?.durationMs ?: 0L

    fun positionMs(): Long {
        val current = track ?: return 0L
        return (positionFrames * 1000.0 / current.sampleRate).toLong()
    }

    private fun msToFrames(ms: Long): Long {
        val current = track ?: return 0L
        return ms * current.sampleRate / 1000L
    }

    companion object {
        const val UNSET = -1L
        const val HOT_CUE_COUNT = 8
        private const val NON_TOUCH_SCRUB_MS = 180L
    }
}
