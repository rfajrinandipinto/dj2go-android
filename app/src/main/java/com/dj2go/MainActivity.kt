package com.dj2go

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.dj2go.audio.AudioEngine
import com.dj2go.audio.OutputDevices
import com.dj2go.library.LibraryStore
import com.dj2go.midi.ControlEvent
import com.dj2go.midi.ControlId
import com.dj2go.midi.Deck
import com.dj2go.midi.Dj2GoMap
import com.dj2go.midi.MidiInputManager
import com.dj2go.midi.MidiMessage
import com.dj2go.midi.Mixer
import com.dj2go.ui.Dj2GoTheme
import com.dj2go.ui.DjActions
import com.dj2go.ui.DjScreen
import com.dj2go.ui.DjState
import com.dj2go.ui.KnobId
import com.dj2go.ui.PadMode
import com.dj2go.ui.SettingsStore
import com.dj2go.ui.next
import com.dj2go.update.UpdateChecker
import java.io.File

/**
 * Wires the DJ2GO2 Touch and the software mixer to the Serato-style Compose UI.
 */
class MainActivity : AppCompatActivity(), MidiInputManager.Listener, AudioEngine.LoadCallback {

    private lateinit var midi: MidiInputManager
    private lateinit var audio: AudioEngine

    private val mixer = Mixer()
    private val state = DjState()
    private val log = StringBuilder()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var pendingDeck: Deck? = null
    private var pendingSampler: Int? = null

    private val tick = object : Runnable {
        override fun run() {
            state.refresh(mixer, audio)
            mainHandler.postDelayed(this, TICK_MS)
        }
    }

    private val actions: DjActions by lazy { buildActions() }

    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            audio.deck(Deck.A).playing = false
            audio.deck(Deck.B).playing = false
            mixer.deckA.playing = false
            mixer.deckB.playing = false
        }
    }

    private val pickTrack =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val deck = pendingDeck
            if (uri != null && deck != null) startLoad(deck, uri)
            pendingDeck = null
        }

    private val pickSample =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val index = pendingSampler
            if (uri != null && index != null) {
                state.loadingSampler = state.loadingSampler + index
                appendLog("-- decoding sampler slot ${index + 1}... --")
                audio.loadSample(index, uri, displayName(uri))
            }
            pendingSampler = null
        }

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                LibraryStore.saveFolder(this, uri.toString())
                state.libraryFolderLabel = uri.lastPathSegment ?: uri.toString()
                rescanLibrary()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        midi = MidiInputManager(this, this)
        audio = AudioEngine(this)
        audio.setLoadCallback(this)
        audio.setLogSink { message -> appendLog(message) }
        state.settings = SettingsStore.load(this)
        audio.applySettings(state.settings.crossfaderCurve, state.settings.tempoRange)
        refreshOutputs()
        loadLibraryFromPrefs()
        state.updateUrl = loadUpdateUrl()
        state.refresh(mixer, audio)

        setContent {
            Dj2GoTheme {
                DjScreen(state, actions)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching {
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        midi.start()
        mainHandler.post(tick)
    }

    override fun onStop() {
        super.onStop()
        runCatching { audioManager.abandonAudioFocus(focusListener) }
        mainHandler.removeCallbacks(tick)
        midi.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        audio.release()
    }

    private fun buildActions(): DjActions = DjActions(
        onTransport = { deck, control ->
            dispatch(ControlEvent(deck, control, 127, 0, true, 0, null))
        },
        onPad = { deck, control, index ->
            dispatch(ControlEvent(deck, control, 127, 0, true, index, null))
        },
        onCrossfader = { value ->
            dispatch(ControlEvent(null, ControlId.CROSSFADER, value, 0, false, 0, null))
        },
        onTempo = { deck, value ->
            dispatch(ControlEvent(deck, ControlId.RATE, value, 0, false, 0, null))
        },
        onGain = { deck, value ->
            dispatch(ControlEvent(deck, ControlId.GAIN, value, 0, false, 0, null))
        },
        onLoad = { deck -> loadForDeck(deck) },
        onPfl = { deck ->
            dispatch(ControlEvent(deck, ControlId.PFL, 127, 0, true, 0, null))
        },
        onSelectMaster = { entry ->
            state.selectedMaster = entry
            applyOutputs()
        },
        onSelectCue = { entry ->
            state.selectedCue = entry
            applyOutputs()
        },
        onRefreshOutputs = { refreshOutputs() },
        onSamplerTrigger = { index ->
            val deck = if (index < 4) Deck.A else Deck.B
            dispatch(ControlEvent(deck, ControlId.SAMPLER, 127, 0, true, index % 4, null))
        },
        onSamplerAssign = { index ->
            pendingSampler = index
            pickSample.launch(arrayOf("audio/*"))
        },
        onPickFolder = { pickFolder.launch(null) },
        onRescan = { rescanLibrary() },
        onLibrarySelect = { index -> state.libraryIndex = index },
        onToggleLibrary = { state.showLibrary = !state.showLibrary },
        onBrowseScroll = { delta -> moveSelection(delta) },
        onTestTone = { audio.playTestTone() },
        onOpenUpdate = { state.showUpdateDialog = true },
        onUpdateUrlChange = { url ->
            state.updateUrl = url
            saveUpdateUrl(url)
        },
        onCheckUpdate = { checkForUpdate() },
        onInstallUpdate = { installUpdate() },
        onCycleTime = { deck ->
            if (deck == Deck.A) state.timeModeA = state.timeModeA.next()
            else state.timeModeB = state.timeModeB.next()
        },
        onBeatJump = { deck, beats ->
            dispatch(ControlEvent(deck, ControlId.BEAT_JUMP, beats, 0, true, 0, null))
        },
        onCueMix = { value ->
            dispatch(ControlEvent(null, ControlId.CUE_MIX, value, 0, false, 0, null))
        },
        onKnobTap = { knob -> state.activeKnob = knob },
        onKnobChange = { knob, value ->
            val event = when (knob) {
                KnobId.GAIN_A -> ControlEvent(Deck.A, ControlId.GAIN, value, 0, false, 0, null)
                KnobId.GAIN_B -> ControlEvent(Deck.B, ControlId.GAIN, value, 0, false, 0, null)
                KnobId.EQ_LOW_A -> ControlEvent(Deck.A, ControlId.EQ_LOW, value, 0, false, 0, null)
                KnobId.EQ_MID_A -> ControlEvent(Deck.A, ControlId.EQ_MID, value, 0, false, 0, null)
                KnobId.EQ_HIGH_A -> ControlEvent(Deck.A, ControlId.EQ_HIGH, value, 0, false, 0, null)
                KnobId.FILTER_A -> ControlEvent(Deck.A, ControlId.FILTER, value, 0, false, 0, null)
                KnobId.EQ_LOW_B -> ControlEvent(Deck.B, ControlId.EQ_LOW, value, 0, false, 0, null)
                KnobId.EQ_MID_B -> ControlEvent(Deck.B, ControlId.EQ_MID, value, 0, false, 0, null)
                KnobId.EQ_HIGH_B -> ControlEvent(Deck.B, ControlId.EQ_HIGH, value, 0, false, 0, null)
                KnobId.FILTER_B -> ControlEvent(Deck.B, ControlId.FILTER, value, 0, false, 0, null)
                KnobId.MASTER ->
                    ControlEvent(null, ControlId.MASTER_GAIN, value, 0, false, 0, null)
                KnobId.CUE_MIX ->
                    ControlEvent(null, ControlId.CUE_MIX, value, 0, false, 0, null)
            }
            // Update the mixer + audio without spamming the log while dragging.
            mixer.apply(event)
            audio.handle(event, mixer)
        },
        onOpenSettings = { state.showSettings = true },
        onSettingsChange = { settings ->
            state.settings = settings
            SettingsStore.save(this, settings)
            audio.applySettings(settings.crossfaderCurve, settings.tempoRange)
        },
        onToggleRecord = {
            if (state.recording) {
                audio.stopRecording()
                state.recording = false
                appendLog("-- recording stopped --")
            } else {
                val path = audio.startRecording()
                if (path != null) {
                    state.recording = true
                    appendLog("-- recording to $path --")
                } else {
                    appendLog("!! could not start recording")
                }
            }
        },
        onSeek = { deck, fraction -> audio.seekToFraction(deck, fraction) },
        onPadMode = { deck, mode ->
            if (deck == Deck.A) state.padModeA = mode else state.padModeB = mode
        }
    )

    // ---- MIDI ----

    override fun onDeviceList(devices: List<String>) {
        if (!state.connected) {
            state.deviceName = if (devices.isEmpty()) "No controller" else devices.joinToString(", ")
        }
    }

    override fun onConnected(deviceName: String) {
        state.connected = true
        state.deviceName = deviceName
        appendLog("-- connected to $deviceName --")
    }

    override fun onDisconnected() {
        state.connected = false
        state.deviceName = "Disconnected"
        appendLog("-- disconnected --")
    }

    override fun onMessage(message: MidiMessage) {
        val event = Dj2GoMap.map(message)
        if (event == null) {
            val line = rawLine(message)
            state.lastEvent = line
            appendLog(line)
        } else {
            dispatch(event, message.toHex())
        }
    }

    override fun onError(message: String) {
        state.deviceName = message
        appendLog("!! $message")
    }

    private fun dispatch(event: ControlEvent, raw: String? = null) {
        val change = mixer.apply(event)
        audio.handle(event, mixer)
        handleLibraryEvent(event)

        val label = describe(event)
        val line = buildString {
            if (raw != null) append(raw).append("  ")
            append(label)
            if (change != null) append("  ->  ").append(change)
        }
        state.lastEvent = line
        appendLog(line)
    }

    /** Browse knob scrolls the crate; Load / browse-press load the selected track. */
    private fun handleLibraryEvent(event: ControlEvent) {
        when (event.control) {
            ControlId.LOAD -> if (event.pressed) event.deck?.let { loadForDeck(it) }
            ControlId.BROWSE -> if (event.delta != 0) moveSelection(event.delta)
            ControlId.BROWSE_PRESS -> if (event.pressed) state.showLibrary = !state.showLibrary
            else -> Unit
        }
    }

    // ---- Library / crate ----

    private fun loadLibraryFromPrefs() {
        state.library = LibraryStore.tracks(this)
        LibraryStore.folder(this)?.let { folder ->
            state.libraryFolderLabel = Uri.parse(folder).lastPathSegment ?: folder
            if (state.library.isEmpty()) rescanLibrary()
        }
    }

    private fun rescanLibrary() {
        val folder = LibraryStore.folder(this)
        if (folder == null) {
            appendLog("-- no music folder selected --")
            return
        }
        state.libraryScanning = true
        appendLog("-- scanning library... --")
        Thread {
            val tracks = LibraryStore.scan(this, Uri.parse(folder))
            LibraryStore.saveTracks(this, tracks)
            mainHandler.post {
                state.library = tracks
                state.libraryIndex =
                    state.libraryIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
                state.libraryScanning = false
                appendLog("-- library: ${tracks.size} tracks --")
            }
        }.start()
    }

    private fun moveSelection(delta: Int) {
        state.browseAngle += delta * 24f
        if (state.library.isEmpty()) return
        state.libraryIndex = (state.libraryIndex + delta).coerceIn(0, state.library.size - 1)
    }

    private fun loadForDeck(deck: Deck) {
        state.showLibrary = false
        val track = state.library.getOrNull(state.libraryIndex)
        if (track != null) {
            markLoading(deck, true)
            appendLog("-- decoding '${track.name}' for Deck $deck... --")
            audio.load(deck, Uri.parse(track.uri))
        } else {
            chooseTrack(deck)
        }
    }

    private fun markLoading(deck: Deck, loading: Boolean) {
        if (deck == Deck.A) state.loadingA = loading else state.loadingB = loading
    }

    // ---- Self-hosted updates ----

    private fun checkForUpdate() {
        val url = state.updateUrl.trim()
        if (url.isEmpty()) {
            state.updateStatus = "Set a manifest URL first."
            return
        }
        saveUpdateUrl(url)
        state.updateStatus = "Checking..."
        state.updateInfo = null
        Thread {
            try {
                val info = UpdateChecker.fetch(url)
                mainHandler.post {
                    if (info.versionCode > BuildConfig.VERSION_CODE) {
                        state.updateInfo = info
                        state.updateStatus = "Update available."
                    } else {
                        state.updateInfo = null
                        state.updateStatus = "Up to date (v${BuildConfig.VERSION_NAME})."
                    }
                }
            } catch (t: Throwable) {
                mainHandler.post { state.updateStatus = "Check failed: ${t.message}" }
            }
        }.start()
    }

    private fun installUpdate() {
        val info = state.updateInfo ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            state.updateStatus = "Allow installs from this app, then tap Install again."
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                )
            }
            return
        }

        state.updateStatus = "Downloading..."
        Thread {
            try {
                val destination = File(cacheDir, "updates/dj2go-update.apk")
                UpdateChecker.download(info.apkUrl, destination)
                if (!UpdateChecker.hasMatchingSignature(this, destination)) {
                    mainHandler.post {
                        state.updateStatus = "Signature mismatch - refusing to install."
                    }
                    return@Thread
                }
                mainHandler.post {
                    state.updateStatus = "Downloaded. Starting installer..."
                    launchInstaller(destination)
                }
            } catch (t: Throwable) {
                mainHandler.post { state.updateStatus = "Download failed: ${t.message}" }
            }
        }.start()
    }

    private fun launchInstaller(apkFile: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }.onFailure {
            state.updateStatus = "No installer available: ${it.message}"
        }
    }

    private fun updatePrefs() = getSharedPreferences("dj2go_update", MODE_PRIVATE)

    private fun saveUpdateUrl(url: String) {
        updatePrefs().edit().putString("url", url).apply()
    }

    private fun loadUpdateUrl(): String = updatePrefs().getString("url", "").orEmpty()

    // ---- Audio loading ----

    override fun onLoaded(deck: Deck, uri: Uri, durationMs: Long) {
        markLoading(deck, false)
        val name = displayName(uri)
        val deckState = mixer.deck(deck)
        deckState.trackName = name
        deckState.durationMs = durationMs
        appendLog("-- loaded $name into Deck $deck --")
        state.refresh(mixer, audio)
    }

    override fun onError(deck: Deck, message: String) {
        markLoading(deck, false)
        appendLog("!! Deck $deck load failed: $message")
    }

    override fun onSampleLoaded(index: Int, name: String) {
        state.loadingSampler = state.loadingSampler - index
        appendLog("-- sampler ${index + 1}: $name --")
        state.refresh(mixer, audio)
    }

    override fun onSampleError(index: Int, message: String) {
        state.loadingSampler = state.loadingSampler - index
        appendLog("!! sampler ${index + 1} failed: $message")
    }

    private fun startLoad(deck: Deck, uri: Uri) {
        markLoading(deck, true)
        appendLog("-- decoding for Deck $deck... --")
        audio.load(deck, uri)
    }

    private fun chooseTrack(deck: Deck) {
        pendingDeck = deck
        pickTrack.launch(arrayOf("audio/*"))
    }

    private fun displayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "track"
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) name = cursor.getString(index) ?: name
            }
        }
        return name
    }

    // ---- Output routing ----

    private fun refreshOutputs() {
        val master = OutputDevices.list(this)
        val cue = OutputDevices.list(this)
        state.masterOptions = master
        state.cueOptions = cue
        if (state.selectedMaster == null) {
            val speakerIndex = master.indexOfFirst {
                it.device?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            state.selectedMaster = if (speakerIndex >= 0) master[speakerIndex] else master.firstOrNull()
        }
        if (state.selectedCue == null) state.selectedCue = cue.firstOrNull()
        applyOutputs()
    }

    private fun applyOutputs() {
        audio.setOutputDevice(state.selectedMaster?.device, state.selectedCue?.device)
        val mode = when {
            audio.outputChannels() == 4 -> "master+cue on one 4-channel output"
            audio.hasSeparateCue() -> "separate cue output"
            else -> "stereo master only"
        }
        state.outputInfo = mode
        appendLog(
            "-- output: master=${state.selectedMaster?.label ?: "default"}, " +
                "cue=${state.selectedCue?.label ?: "none"} ($mode) --"
        )
    }

    // ---- helpers ----

    private fun rawLine(message: MidiMessage): String =
        "${message.toHex()}  ${message.type} ch${message.channel + 1} " +
            "d1=${message.data1} d2=${message.data2}"

    private fun describe(event: ControlEvent): String {
        val prefix = event.deck?.let { "Deck $it " } ?: ""
        val pad = when (event.control) {
            ControlId.HOT_CUE, ControlId.BEAT_LOOP, ControlId.SAMPLER -> " ${event.index + 1}"
            else -> ""
        }
        return "$prefix${event.control}$pad"
    }

    private fun appendLog(line: String) {
        log.append(line).append('\n')
        if (log.length > MAX_LOG_CHARS) {
            log.delete(0, log.length - MAX_LOG_CHARS)
        }
        state.logText = log.toString()
    }

    companion object {
        private const val MAX_LOG_CHARS = 8000
        private const val TICK_MS = 16L
    }
}
