package com.dj2go.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.dj2go.audio.BeatGrid
import com.dj2go.audio.OutputDevices
import com.dj2go.audio.WaveformData
import com.dj2go.library.LibraryTrack
import kotlin.math.sin

/**
 * Design-time previews. Open this file in Android Studio and use the Preview
 * pane to iterate on the UI without deploying to the tablet.
 */
@Preview(name = "DJ2GO landscape", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun DjScreenPreview() {
    Dj2GoTheme {
        DjScreen(state = previewState(), actions = previewActions())
    }
}

@Preview(name = "DJ2GO small", widthDp = 960, heightDp = 600, showBackground = true)
@Composable
private fun DjScreenSmallPreview() {
    Dj2GoTheme {
        DjScreen(state = previewState(), actions = previewActions())
    }
}

private fun previewState(): DjState = DjState().apply {
    val wave = sampleWaveform()
    val posA = 74_000.0 / 1000.0 * 44100.0
    val posB = 12_000.0 / 1000.0 * 44100.0
    deckA = DeckUi(
        trackName = "Midnight Drive",
        playing = true,
        cue = false,
        sync = true,
        pfl = false,
        rate = 64,
        gain = 100,
        positionMs = 74_000L,
        durationMs = 243_000L,
        waveform = wave,
        beatGrid = BeatGrid(124f, 0L, 44100.0 * 60.0 / 124.0),
        bpm = 124f,
        positionFrames = posA,
        sampleRate = 44100,
        level = 0.62f,
        barBeat = "33.2",
        beatsToCue = 12,
        hotCues = longArrayOf(
            (posA - 60_000).toLong(),
            (posA + 20_000).toLong(),
            (posA + 90_000).toLong(),
            -1L
        ),
        cuePositionFrames = (posA - 40_000).toLong(),
        loopInFrames = (posA - 30_000).toLong(),
        loopOutFrames = (posA + 40_000).toLong(),
        phase = 0.12f
    )
    deckB = DeckUi(
        trackName = "Neon Skyline",
        playing = false,
        cue = true,
        sync = false,
        pfl = true,
        rate = 70,
        gain = 112,
        positionMs = 12_000L,
        durationMs = 198_000L,
        waveform = wave,
        beatGrid = BeatGrid(128f, 22050L, 44100.0 * 60.0 / 128.0),
        bpm = 128f,
        positionFrames = posB,
        sampleRate = 44100,
        level = 0.35f,
        barBeat = "9.4",
        beatsToCue = 4,
        hotCues = longArrayOf((posB + 40_000).toLong(), -1L, -1L, -1L),
        cuePositionFrames = (posB - 20_000).toLong(),
        loopInFrames = -1L,
        loopOutFrames = -1L,
        phase = 0.44f
    )
    crossfader = 42
    cueMix = 30
    masterGain = 100
    headphoneGain = 90
    masterLevel = 0.7f
    connected = true
    deviceName = "Numark DJ2GO2 Touch"
    outputInfo = "master+cue on one 4-channel output"

    val options = listOf(
        OutputDevices.Entry("System default", null),
        OutputDevices.Entry("Bluetooth (A2DP)", null),
        OutputDevices.Entry("USB", null)
    )
    masterOptions = options
    cueOptions = options
    selectedMaster = options[0]
    selectedCue = options[0]

    library = listOf(
        LibraryTrack("uri:1", "A1 - Midnight Drive.mp3"),
        LibraryTrack("uri:2", "A2 - Neon Skyline.mp3"),
        LibraryTrack("uri:3", "B1 - Afterglow.mp3"),
        LibraryTrack("uri:4", "B2 - Deep End.mp3"),
        LibraryTrack("uri:5", "C1 - Warehouse.mp3")
    )
    libraryIndex = 1
    libraryFolderLabel = "Music/DJ"
    samplerNames = listOf("Kick", "Snare", "Clap", "", "", "", "", "")
    samplerPlaying = setOf(0)
    lastEvent = "Deck A PLAY -> on"
    logText = "-- connected to Numark DJ2GO2 Touch --\n-- library: 5 tracks --"
}

private fun previewActions(): DjActions = DjActions(
    onTransport = { _, _ -> },
    onPad = { _, _, _ -> },
    onCrossfader = {},
    onTempo = { _, _ -> },
    onGain = { _, _ -> },
    onLoad = {},
    onPfl = {},
    onSelectMaster = {},
    onSelectCue = {},
    onRefreshOutputs = {},
    onSamplerTrigger = {},
    onSamplerAssign = {},
    onPickFolder = {},
    onRescan = {},
    onLibrarySelect = {},
    onTestTone = {},
    onOpenUpdate = {},
    onUpdateUrlChange = {},
    onCheckUpdate = {},
    onInstallUpdate = {},
    onCycleTime = {},
    onBeatJump = { _, _ -> },
    onCueMix = {}
)

private fun sampleWaveform(): WaveformData {
    val count = 400
    val low = ByteArray(count)
    val mid = ByteArray(count)
    val high = ByteArray(count)
    for (i in 0 until count) {
        val envelope = (0.5 + 0.5 * sin(i / 7.0)) * (0.4 + 0.6 * ((i * 37 % 100) / 100.0))
        low[i] = (envelope * 220).toInt().coerceIn(0, 255).toByte()
        mid[i] = (envelope * 150).toInt().coerceIn(0, 255).toByte()
        high[i] = (envelope * 110).toInt().coerceIn(0, 255).toByte()
    }
    return WaveformData(512, low, mid, high)
}
