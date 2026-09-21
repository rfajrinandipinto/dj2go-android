package com.dj2go.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dj2go.audio.AudioEngine
import com.dj2go.audio.BeatGrid
import com.dj2go.audio.DeckPlayer
import com.dj2go.audio.OutputDevices
import com.dj2go.audio.WaveformData
import com.dj2go.midi.Deck
import com.dj2go.midi.DeckState
import com.dj2go.midi.Mixer
import com.dj2go.library.LibraryTrack
import com.dj2go.update.UpdateChecker

/** Immutable snapshot of one deck for the UI. */
data class DeckUi(
    val trackName: String = "",
    val playing: Boolean = false,
    val cue: Boolean = false,
    val sync: Boolean = false,
    val pfl: Boolean = false,
    val rate: Int = 64,
    val gain: Int = 100,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val loopActive: Boolean = false,
    val heldPads: Set<String> = emptySet(),
    val waveform: WaveformData? = null,
    val beatGrid: BeatGrid? = null,
    val bpm: Float = 0f,
    val positionFrames: Double = 0.0,
    val sampleRate: Int = 44100,
    val level: Float = 0f
) {
    val totalFrames: Int get() = (waveform?.bucketCount ?: 0) * (waveform?.bucketFrames ?: 1)
}

/** Observable state consumed by the Compose UI. */
class DjState {
    var deckA by mutableStateOf(DeckUi())
    var deckB by mutableStateOf(DeckUi())
    var crossfader by mutableStateOf(64)
    var masterGain by mutableStateOf(100)
    var headphoneGain by mutableStateOf(100)
    var browse by mutableStateOf(0)
    var masterLevel by mutableStateOf(0f)

    var samplerNames by mutableStateOf<List<String>>(emptyList())
    var samplerPlaying by mutableStateOf<Set<Int>>(emptySet())

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

    var showUpdateDialog by mutableStateOf(false)
    var updateUrl by mutableStateOf("")
    var updateStatus by mutableStateOf("")
    var updateInfo by mutableStateOf<UpdateChecker.UpdateInfo?>(null)

    fun refresh(mixer: Mixer, audio: AudioEngine) {
        deckA = mixer.deckA.toUi(audio.deck(Deck.A), audio.levelA)
        deckB = mixer.deckB.toUi(audio.deck(Deck.B), audio.levelB)
        crossfader = mixer.crossfader
        masterGain = mixer.masterGain
        headphoneGain = mixer.headphoneGain
        browse = mixer.browse
        masterLevel = audio.levelMaster
        samplerNames = audio.samplerNames()
        samplerPlaying = audio.samplerPlaying()
    }
}

private fun DeckState.toUi(player: DeckPlayer, level: Float): DeckUi = DeckUi(
    trackName = trackName,
    playing = playing,
    cue = cue,
    sync = sync,
    pfl = pfl,
    rate = rate,
    gain = gain,
    positionMs = player.positionMs(),
    durationMs = player.durationMs(),
    loopActive = loopActive,
    heldPads = heldPads.toSet(),
    waveform = player.track?.waveform,
    beatGrid = player.track?.beatGrid,
    bpm = player.track?.beatGrid?.bpm ?: 0f,
    positionFrames = player.positionFrames,
    sampleRate = player.track?.sampleRate ?: 44100,
    level = level
)
