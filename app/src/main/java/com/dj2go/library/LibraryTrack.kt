package com.dj2go.library

/** One track in the crate. */
data class LibraryTrack(
    val uri: String,
    val name: String
)

/** Cached BPM/key for a crate track, filled in once it has been analysed. */
data class TrackAnalysis(
    val bpm: Float,
    val key: String
)
