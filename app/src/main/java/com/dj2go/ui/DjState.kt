package com.dj2go.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dj2go.audio.AudioEngine
import com.dj2go.audio.BeatGrid
import com.dj2go.audio.DeckPlayer
import com.dj2go.audio.OutputDevices
import com.dj2go.audio.WaveformData
import com.dj2go.library.LibraryTrack
import com.dj2go.midi.Deck
import com.dj2go.midi.DeckState
import com.dj2go.midi.Mixer

/** What the big deck clock shows. */
enum class TimeMode { ELAPSED, REMAINING, BEATS }

/** Touch-adjustable knobs (used when there is no controller). */
enum class KnobId(val label: String) {
    GAIN_A("Deck 1 Gain"),
    GAIN_B("Deck 2 Gain"),
    EQ_LOW_A("Deck 1 Low"),
    EQ_MID_A("Deck 1 Mid"),
    EQ_HIGH_A("Deck 1 High"),
    FILTER_A("Deck 1 Filter"),
    EQ_LOW_B("Deck 2 Low"),
    EQ_MID_B("Deck 2 Mid"),
    EQ_HIGH_B("Deck 2 High"),
    FILTER_B("Deck 2 Filter"),
    MASTER("Master Gain"),
    CUE_MIX("Cue Mix")
}

fun TimeMode.next(): TimeMode = when (this) {
    TimeMode.ELAPSED -> TimeMode.REMAINING
    TimeMode.REMAINING -> TimeMode.BEATS
    TimeMode.BEATS -> TimeMode.ELAPSED
}

/** Immutable snapshot of one deck for the UI. */
data class DeckUi(
    val trackName: String = "",
    val playing: Boolean = false,
    val cue: Boolean = false,
    val sync: Boolean = false,
    val pfl: Boolean = false,
    val rate: Int = 64,
    val gain: Int = 100,
    val eqLow: Int = 64,
    val eqMid: Int = 64,
    val eqHigh: Int = 64,
    val filter: Int = 64,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val loopActive: Boolean = false,
    val heldPads: Set<String> = emptySet(),
    val waveform: WaveformData? = null,
    val beatGrid: BeatGrid? = null,
    val bpm: Float = 0f,
    val positionFrames: Double = 0.0,
    val sampleRate: Int = 44100,
    val level: Float = 0f,
    val barBeat: String = "--",
    val beatsToCue: Int = -1,
    val hotCues: LongArray = LongArray(0),
    val cuePositionFrames: Long = -1L,
    val loopInFrames: Long = -1L,
    val loopOutFrames: Long = -1L,
    val phase: Float = 0f
) {
    val totalFrames: Int get() = (waveform?.bucketCount ?: 0) * (waveform?.bucketFrames ?: 1)
}

/** Observable state consumed by the Compose UI. */
class DjState {
    var deckA by mutableStateOf(DeckUi())
    var deckB by mutableStateOf(DeckUi())
    var crossfader by mutableStateOf(64)
    var cueMix by mutableStateOf(0)
    var masterGain by mutableStateOf(100)
    var headphoneGain by mutableStateOf(100)
    var browse by mutableStateOf(0)
    var masterLevel by mutableStateOf(0f)

    var timeModeA by mutableStateOf(TimeMode.ELAPSED)
    var timeModeB by mutableStateOf(TimeMode.ELAPSED)
    var activeKnob by mutableStateOf<KnobId?>(null)
    var settings by mutableStateOf(DjSettings())
    var showSettings by mutableStateOf(false)

    var samplerNames by mutableStateOf<List<String>>(emptyList())
    var samplerPlaying by mutableStateOf<Set<Int>>(emptySet())
    var loadingSampler by mutableStateOf<Set<Int>>(emptySet())

    var loadingA by mutableStateOf(false)
    var loadingB by mutableStateOf(false)

    var connected by mutableStateOf(false)
    var deviceName by mutableStateOf("No controller")
    var outputInfo by mutableStateOf("")
    var lastEvent by mutableStateOf("")
    var logText by mutableStateOf("")
    var showLog by mutableStateOf(false)

    var masterOptions by mutableStateOf<List<OutputDevices.Entry>>(emptyList())
    var cueOptions by mutableStateOf<List<OutputDevices.Entry>>(emptyList())
    var selectedMaster by mutableStateOf<OutputDevices.Entry?>(null)
    var selectedCue by mutableStateOf<OutputDevices.Entry?>(null)

    var library by mutableStateOf<List<LibraryTrack>>(emptyList())
    var libraryIndex by mutableStateOf(0)
    var libraryFolderLabel by mutableStateOf("No folder selected")
    var libraryScanning by mutableStateOf(false)
    var showLibrary by mutableStateOf(false)
    var browseAngle by mutableStateOf(0f)

    var showUpdateDialog by mutableStateOf(false)
    var updateUrl by mutableStateOf("")
    var updateStatus by mutableStateOf("")
    var updateInfo by mutableStateOf<com.dj2go.update.UpdateChecker.UpdateInfo?>(null)

    fun refresh(mixer: Mixer, audio: AudioEngine) {
        deckA = mixer.deckA.toUi(audio.deck(Deck.A), audio.levelA)
        deckB = mixer.deckB.toUi(audio.deck(Deck.B), audio.levelB)
        crossfader = mixer.crossfader
        cueMix = mixer.cueMix
        masterGain = mixer.masterGain
        headphoneGain = mixer.headphoneGain
        browse = mixer.browse
        masterLevel = audio.levelMaster
        samplerNames = audio.samplerNames()
        samplerPlaying = audio.samplerPlaying()
    }
}

private fun DeckState.toUi(player: DeckPlayer, level: Float): DeckUi {
    val grid = player.track?.beatGrid
    val positionFrames = player.positionFrames

    val barBeat = if (grid?.valid == true) {
        val beatIndex =
            Math.floor((positionFrames - grid.firstBeatFrames) / grid.periodFrames).toLong()
        val bar = Math.floorDiv(beatIndex, 4L) + 1
        val beat = Math.floorMod(beatIndex, 4L) + 1
        "$bar.$beat"
    } else {
        "--"
    }

    val beatsToCue = if (grid?.valid == true) {
        val next = player.hotCuePositions()
            .filter { it >= 0 && it > positionFrames }
            .minOrNull()
        if (next != null) Math.ceil((next - positionFrames) / grid.periodFrames).toInt() else -1
    } else {
        -1
    }

    val phase = if (grid?.valid == true) grid.phase(positionFrames).toFloat() else 0f

    return DeckUi(
        trackName = trackName,
        playing = playing,
        cue = cue,
        sync = sync,
        pfl = pfl,
        rate = rate,
        gain = gain,
        eqLow = eqLow,
        eqMid = eqMid,
        eqHigh = eqHigh,
        filter = filter,
        positionMs = player.positionMs(),
        durationMs = player.durationMs(),
        loopActive = loopActive,
        heldPads = heldPads.toSet(),
        waveform = player.track?.waveform,
        beatGrid = grid,
        bpm = grid?.bpm ?: 0f,
        positionFrames = positionFrames,
        sampleRate = player.track?.sampleRate ?: 44100,
        level = level,
        barBeat = barBeat,
        beatsToCue = beatsToCue,
        hotCues = player.hotCuePositions(),
        cuePositionFrames = player.cuePositionFrames,
        loopInFrames = player.loopInFrames,
        loopOutFrames = player.loopOutFrames,
        phase = phase
    )
}
