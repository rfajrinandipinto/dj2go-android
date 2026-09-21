package com.dj2go.audio

/**
 * Beat grid for a track: tempo plus the position of the first beat.
 *
 * [firstBeatFrames] is the frame index of a beat; every beat is
 * [periodFrames] later. An invalid grid has [bpm] == 0.
 */
class BeatGrid(
    val bpm: Float,
    val firstBeatFrames: Long,
    val periodFrames: Double
) {
    val valid: Boolean get() = bpm > 0f && periodFrames > 0.0

    /** Frames from [frame] to the next beat at or after it. */
    fun nextBeat(frame: Double): Long {
        if (!valid) return frame.toLong()
        val beats = Math.ceil((frame - firstBeatFrames) / periodFrames)
        return (firstBeatFrames + beats * periodFrames).toLong()
    }

    /** 0..1 position within the current beat. */
    fun phase(frame: Double): Double {
        if (!valid) return 0.0
        var p = (frame - firstBeatFrames) % periodFrames
        if (p < 0) p += periodFrames
        return p / periodFrames
    }

    companion object {
        val EMPTY = BeatGrid(0f, 0L, 0.0)
    }
}
