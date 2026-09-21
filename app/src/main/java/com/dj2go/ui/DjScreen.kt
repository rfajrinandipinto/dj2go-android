package com.dj2go.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dj2go.audio.CrossfaderCurve
import com.dj2go.audio.OutputDevices
import com.dj2go.midi.ControlId
import com.dj2go.midi.Deck
import com.dj2go.midi.Mixer
import kotlin.math.abs
import kotlin.math.roundToInt

/** Callbacks from the on-screen UI back into the mixer. */
class DjActions(
    val onTransport: (Deck, ControlId) -> Unit,
    val onPad: (Deck, ControlId, Int) -> Unit,
    val onCrossfader: (Int) -> Unit,
    val onTempo: (Deck, Int) -> Unit,
    val onGain: (Deck, Int) -> Unit,
    val onLoad: (Deck) -> Unit,
    val onPfl: (Deck) -> Unit,
    val onSelectMaster: (OutputDevices.Entry) -> Unit,
    val onSelectCue: (OutputDevices.Entry) -> Unit,
    val onRefreshOutputs: () -> Unit,
    val onSamplerTrigger: (Int) -> Unit,
    val onSamplerAssign: (Int) -> Unit,
    val onPickFolder: () -> Unit,
    val onRescan: () -> Unit,
    val onLibrarySelect: (Int) -> Unit,
    val onToggleLibrary: () -> Unit,
    val onBrowseScroll: (Int) -> Unit,
    val onTestTone: () -> Unit,
    val onOpenUpdate: () -> Unit,
    val onUpdateUrlChange: (String) -> Unit,
    val onCheckUpdate: () -> Unit,
    val onInstallUpdate: () -> Unit,
    val onCycleTime: (Deck) -> Unit,
    val onBeatJump: (Deck, Int) -> Unit,
    val onCueMix: (Int) -> Unit,
    val onKnobTap: (KnobId) -> Unit,
    val onKnobChange: (KnobId, Int) -> Unit,
    val onOpenSettings: () -> Unit,
    val onSettingsChange: (DjSettings) -> Unit
)

@Composable
fun DjScreen(state: DjState, actions: DjActions) {
    Box(Modifier.fillMaxSize().background(ScreenBackground)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(state, actions)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                DeckPanel(Deck.A, state.deckA, state, actions, Modifier.weight(1f))
                MixerPanel(state, actions, Modifier.width(210.dp).fillMaxHeight())
                DeckPanel(Deck.B, state.deckB, state, actions, Modifier.weight(1f))
            }
            LibraryPanel(state, actions, Modifier.fillMaxWidth().height(92.dp))
        }
        if (state.showLibrary) {
            LibraryOverlay(
                state,
                actions,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 92.dp)
                    .fillMaxWidth()
                    .height(260.dp)
            )
        }
        if (state.showLog) LogOverlay(state)
        if (state.showUpdateDialog) UpdateDialog(state, actions)
        if (state.showSettings) SettingsDialog(state, actions)
        state.activeKnob?.let { KnobDialog(state, actions) }
    }
}

@Composable
private fun TopBar(state: DjState, actions: DjActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(Color(0xFF101018))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("DJ2GO", color = DeckAAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (state.connected) state.deviceName else "No controller",
            color = MutedText,
            fontSize = 11.sp
        )
        Spacer(Modifier.weight(1f))
        Text("MASTER", color = MutedText, fontSize = 9.sp)
        Dropdown("Master", state.masterOptions, state.selectedMaster, { it.label }, actions.onSelectMaster)
        Spacer(Modifier.width(6.dp))
        Text("CUE", color = MutedText, fontSize = 9.sp)
        Dropdown("Cue", state.cueOptions, state.selectedCue, { it.label }, actions.onSelectCue)
        Spacer(Modifier.width(6.dp))
        TransportButton(
            "TEST", false, Color(0xFF3A3A4C),
            actions.onTestTone,
            Modifier.width(58.dp).height(26.dp)
        )
        Spacer(Modifier.width(6.dp))
        TransportButton(
            "UPDATE", false, Color(0xFF3A3A4C),
            actions.onOpenUpdate,
            Modifier.width(70.dp).height(26.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "LOG",
            color = if (state.showLog) DeckAAccent else MutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { state.showLog = !state.showLog }
                .padding(6.dp)
        )
    }
}

@Composable
private fun DeckPanel(
    deck: Deck,
    ui: DeckUi,
    state: DjState,
    actions: DjActions,
    modifier: Modifier
) {
    val settings = state.settings
    val accent = Color(if (deck == Deck.A) settings.deckAColor else settings.deckBColor)
    val timeMode = if (deck == Deck.A) state.timeModeA else state.timeModeB
    val loading = if (deck == Deck.A) state.loadingA else state.loadingB
    val timeText = when (timeMode) {
        TimeMode.ELAPSED -> Mixer.formatTime(ui.positionMs)
        TimeMode.REMAINING ->
            "-" + Mixer.formatTime((ui.durationMs - ui.positionMs).coerceAtLeast(0L))
        TimeMode.BEATS -> ui.barBeat
    }
    Column(modifier.fillMaxHeight().padding(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "DECK ${if (deck == Deck.A) "1" else "2"}",
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = ui.trackName.ifEmpty { "—" },
                color = PrimaryText,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (ui.bpm > 0f) "%.1f".format(ui.bpm) else "--",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(" BPM", color = MutedText, fontSize = 9.sp)
            Spacer(Modifier.width(8.dp))
            Text(ui.barBeat, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                text = timeText,
                color = PrimaryText,
                fontSize = 12.sp,
                modifier = Modifier.clickable { actions.onCycleTime(deck) }
            )
            if (ui.beatsToCue >= 0) {
                Spacer(Modifier.width(6.dp))
                Text("→${ui.beatsToCue}", color = MutedText, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(4.dp))
        if (settings.showOverview) {
            OverviewWaveform(
                ui.waveform,
                ui.positionFrames,
                accent,
                settings.colorMode,
                Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(4.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            MainWaveform(
                ui.waveform,
                ui.beatGrid,
                ui.positionFrames,
                ui.sampleRate,
                settings.secondsVisible,
                ui.hotCues,
                ui.cuePositionFrames,
                ui.loopInFrames,
                ui.loopOutFrames,
                accent,
                settings.colorMode,
                settings.amplitudeScale,
                settings.showBeatGrid,
                settings.showCueMarkers,
                settings.showBarBeat,
                Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp))
            )
            if (ui.beatsToCue >= 0) {
                Text(
                    text = "→ ${ui.beatsToCue}",
                    color = accent,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                )
            }
            SettingsIconButton(
                onClick = actions.onOpenSettings,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
            )
            if (loading) {
                Box(
                    Modifier.fillMaxSize().background(Color(0xCC07070B)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = accent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "DECODING",
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(150.dp)) {
            JogWheel(ui.positionFrames, ui.sampleRate, accent, ui.playing, Modifier.size(140.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                TransportRow(deck, ui, accent, actions)
                Spacer(Modifier.height(4.dp))
                BeatJumpRow(deck, accent, actions)
                Spacer(Modifier.height(4.dp))
                PadGrid(deck, ui, accent, actions, Modifier.weight(1f))
            }
            Spacer(Modifier.width(8.dp))
            Column(
                Modifier.width(44.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                VerticalFader(
                    ui.rate,
                    accent,
                    { actions.onTempo(deck, it) },
                    Modifier.weight(1f).width(28.dp)
                )
                Text("TEMPO", color = MutedText, fontSize = 8.sp)
            }
            Spacer(Modifier.width(6.dp))
            Column(
                Modifier.width(44.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Knob(
                    ui.gain, accent, true, Modifier.size(34.dp),
                    onClick = {
                        actions.onKnobTap(if (deck == Deck.A) KnobId.GAIN_A else KnobId.GAIN_B)
                    },
                    onChange = {
                        actions.onKnobChange(
                            if (deck == Deck.A) KnobId.GAIN_A else KnobId.GAIN_B, it
                        )
                    }
                )
                Text("GAIN", color = MutedText, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun TransportRow(deck: Deck, ui: DeckUi, accent: Color, actions: DjActions) {
    Row(Modifier.fillMaxWidth().height(32.dp)) {
        TransportIconButton(
            TransportIcon.CUE, ui.cue, accent,
            { actions.onTransport(deck, ControlId.CUE) },
            Modifier.weight(1f).fillMaxHeight()
        )
        Spacer(Modifier.width(5.dp))
        PlayPauseButton(
            ui.playing, accent,
            { actions.onTransport(deck, ControlId.PLAY) },
            Modifier.weight(1f).fillMaxHeight()
        )
        Spacer(Modifier.width(5.dp))
        TransportIconButton(
            TransportIcon.SYNC, ui.sync, accent,
            { actions.onTransport(deck, ControlId.SYNC) },
            Modifier.weight(1f).fillMaxHeight()
        )
        Spacer(Modifier.width(5.dp))
        TransportIconButton(
            TransportIcon.HEADPHONE, ui.pfl, accent,
            { actions.onPfl(deck) },
            Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
private fun BeatJumpRow(deck: Deck, accent: Color, actions: DjActions) {
    Row(Modifier.fillMaxWidth().height(24.dp)) {
        TransportButton(
            "-4 BEAT", false, accent,
            { actions.onBeatJump(deck, -4) },
            Modifier.weight(1f).fillMaxHeight()
        )
        Spacer(Modifier.width(5.dp))
        TransportButton(
            "+4 BEAT", false, accent,
            { actions.onBeatJump(deck, 4) },
            Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
private fun PadGrid(deck: Deck, ui: DeckUi, accent: Color, actions: DjActions, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        PadRow(
            deck, ui, accent, actions, ControlId.HOT_CUE,
            listOf("1", "2", "3", "4"), Modifier.weight(1f)
        )
        Spacer(Modifier.height(4.dp))
        PadRow(
            deck, ui, accent, actions, ControlId.BEAT_LOOP,
            listOf("1", "2", "4", "8"), Modifier.weight(1f)
        )
    }
}

@Composable
private fun PadRow(
    deck: Deck,
    ui: DeckUi,
    accent: Color,
    actions: DjActions,
    control: ControlId,
    labels: List<String>,
    modifier: Modifier
) {
    Row(modifier.fillMaxWidth()) {
        for (index in 0 until 4) {
            val active = "${control.name}:$index" in ui.heldPads
            Pad(
                label = labels.getOrElse(index) { "${index + 1}" },
                active = active,
                accent = accent,
                onClick = { actions.onPad(deck, control, index) },
                modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 3.dp)
            )
        }
    }
}

@Composable
private fun MixerPanel(state: DjState, actions: DjActions, modifier: Modifier) {
    Column(
        modifier.background(Color(0xFF0E0E16)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MIXER", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LoadBrowseRow(state, actions)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ChannelStrip("1", Color(state.settings.deckAColor), state.deckA, KnobId.GAIN_A, actions)
            ChannelStrip("2", Color(state.settings.deckBColor), state.deckB, KnobId.GAIN_B, actions)
        }
        Spacer(Modifier.weight(1f))
        Text("PHASE", color = MutedText, fontSize = 9.sp)
        Spacer(Modifier.height(2.dp))
        PhaseMeter(
            state.deckA.phase,
            state.deckB.phase,
            Modifier.fillMaxWidth().height(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Knob(
                    state.cueMix, Color(0xFFB0B0C8), true, Modifier.size(32.dp),
                    onClick = { actions.onKnobTap(KnobId.CUE_MIX) },
                    onChange = { actions.onKnobChange(KnobId.CUE_MIX, it) }
                )
                Text("CUE MIX", color = MutedText, fontSize = 8.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Knob(
                    state.masterGain, Color(state.settings.deckAColor), true, Modifier.size(32.dp),
                    onClick = { actions.onKnobTap(KnobId.MASTER) },
                    onChange = { actions.onKnobChange(KnobId.MASTER, it) }
                )
                Text("MASTER", color = MutedText, fontSize = 8.sp)
            }
            VuMeter(state.masterLevel, Modifier.width(12.dp).height(48.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("CROSSFADER", color = MutedText, fontSize = 9.sp)
        Spacer(Modifier.height(2.dp))
        HorizontalFader(
            state.crossfader,
            Color(0xFFB0B0C8),
            { actions.onCrossfader(it) },
            Modifier.fillMaxWidth().height(32.dp)
        )
    }
}

@Composable
private fun LoadBrowseRow(state: DjState, actions: DjActions) {
    val accentA = Color(state.settings.deckAColor)
    val accentB = Color(state.settings.deckBColor)
    val browseAngle by animateFloatAsState(
        targetValue = state.browseAngle,
        animationSpec = tween(durationMillis = 140),
        label = "browseAngle"
    )
    val infinite = rememberInfiniteTransition(label = "cratePulse")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val pulseA = if (state.showLibrary) lerp(accentA.copy(alpha = 0.3f), accentA, pulse) else accentA
    val pulseB = if (state.showLibrary) lerp(accentB.copy(alpha = 0.3f), accentB, pulse) else accentB
    val browseColor = if (state.showLibrary) {
        lerp(accentA.copy(alpha = 0.35f), accentA, pulse)
    } else {
        Color(0xFFB0B0C8)
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        OutlineButton(
            "1", pulseA,
            { actions.onLoad(Deck.A) },
            Modifier.weight(1f).height(42.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(42.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { actions.onToggleLibrary() }
                    .pointerInput(Unit) {
                        var accumulated = 0f
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            accumulated += dragAmount
                            val steps = (accumulated / 8f).toInt()
                            if (steps != 0) {
                                actions.onBrowseScroll(steps)
                                accumulated -= steps * 8f
                            }
                        }
                    }
            ) {
                RotaryKnob(browseAngle, browseColor, Modifier.fillMaxSize())
            }
            Text(
                "BROWSE",
                color = if (state.showLibrary) pulseA else MutedText,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlineButton(
            "2", pulseB,
            { actions.onLoad(Deck.B) },
            Modifier.weight(1f).height(42.dp)
        )
    }
}

@Composable
private fun ChannelStrip(
    label: String,
    accent: Color,
    deck: DeckUi,
    knobId: KnobId,
    actions: DjActions
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Knob(
            deck.gain, accent, true, Modifier.size(34.dp),
            onClick = { actions.onKnobTap(knobId) },
            onChange = { actions.onKnobChange(knobId, it) }
        )
        Spacer(Modifier.height(3.dp))
        Text("TRIM", color = MutedText, fontSize = 8.sp)
        Spacer(Modifier.height(10.dp))
        VuMeter(deck.level, Modifier.width(12.dp).height(86.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (deck.pfl) "CUE" else "—",
            color = if (deck.pfl) accent else MutedText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun LibraryPanel(state: DjState, actions: DjActions, modifier: Modifier) {
    Column(modifier.background(Color(0xFF0C0C14)).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("LIBRARY", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (state.library.isEmpty()) {
                    state.libraryFolderLabel
                } else {
                    "${state.libraryFolderLabel}  •  ${state.library.size} tracks"
                },
                color = if (state.library.isEmpty()) MutedText else Color(0xFFB0B0C8),
                fontSize = 10.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (state.libraryScanning) {
                Text("scanning...", color = DeckAAccent, fontSize = 9.sp)
                Spacer(Modifier.width(8.dp))
            }
            TransportButton("FOLDER", false, DeckAAccent, actions.onPickFolder, Modifier.width(72.dp).height(26.dp))
            Spacer(Modifier.width(6.dp))
            TransportButton("RESCAN", false, Color(0xFF3A3A4C), actions.onRescan, Modifier.width(72.dp).height(26.dp))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().height(30.dp)) {
            for (index in 0 until 8) {
                val name = state.samplerNames.getOrNull(index).orEmpty()
                SamplerPad(
                    label = if (name.isNotEmpty()) name.take(9) else "S${index + 1}",
                    active = index in state.samplerPlaying,
                    loaded = name.isNotEmpty(),
                    loading = index in state.loadingSampler,
                    accent = if (index < 4) DeckAAccent else DeckBAccent,
                    onTrigger = { actions.onSamplerTrigger(index) },
                    onAssign = { actions.onSamplerAssign(index) },
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(state.lastEvent, color = MutedText, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun LibraryOverlay(state: DjState, actions: DjActions, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.libraryIndex) {
        if (state.library.isNotEmpty()) {
            listState.scrollToItem(state.libraryIndex.coerceIn(0, state.library.size - 1))
        }
    }
    Column(
        modifier
            .background(Color(0xF00E0E16))
            .padding(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("BROWSE", color = DeckAAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = state.library.getOrNull(state.libraryIndex)?.name ?: "No track selected",
                color = PrimaryText,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text("BROWSE to close  •  LOAD to load", color = MutedText, fontSize = 9.sp)
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
            itemsIndexed(state.library) { index, track ->
                val selected = index == state.libraryIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(if (selected) DeckAAccent.copy(alpha = 0.25f) else Color.Transparent)
                        .clickable { actions.onLibrarySelect(index) }
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        color = MutedText,
                        fontSize = 10.sp,
                        modifier = Modifier.width(34.dp)
                    )
                    Text(
                        text = track.name,
                        color = if (selected) PrimaryText else Color(0xFFB0B0C8),
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (state.library.isEmpty()) {
                item {
                    Text(
                        text = "No tracks. Tap FOLDER to choose your music folder.",
                        color = MutedText,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(state: DjState, actions: DjActions) {
    val s = state.settings
    DjDialog(
        title = "Settings",
        accent = Color(0xFFB0B0C8),
        onDismiss = { state.showSettings = false }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 430.dp)
                .verticalScroll(rememberScrollState())
        ) {
                SectionLabel("WAVEFORM")
                OptionRow("Zoom", listOf(4f, 6f, 8f, 12f), s.secondsVisible, { "${it.toInt()}s" }) {
                    actions.onSettingsChange(s.copy(secondsVisible = it))
                }
                OptionRow("Size", listOf(0.7f, 1f, 1.4f), s.amplitudeScale, {
                    when (it) {
                        0.7f -> "Small"
                        1f -> "Normal"
                        else -> "Large"
                    }
                }) { actions.onSettingsChange(s.copy(amplitudeScale = it)) }
                OptionRow("Colour", WaveColorMode.values().toList(), s.colorMode, { it.label }) {
                    actions.onSettingsChange(s.copy(colorMode = it))
                }
                ToggleRow("Beat grid", s.showBeatGrid) {
                    actions.onSettingsChange(s.copy(showBeatGrid = it))
                }
                ToggleRow("Overview waveform", s.showOverview) {
                    actions.onSettingsChange(s.copy(showOverview = it))
                }
                ToggleRow("Cue & loop markers", s.showCueMarkers) {
                    actions.onSettingsChange(s.copy(showCueMarkers = it))
                }
                ToggleRow("Bar / beat overlay", s.showBarBeat) {
                    actions.onSettingsChange(s.copy(showBarBeat = it))
                }
                SectionLabel("MIXER")
                OptionRow(
                    "Crossfader curve",
                    CrossfaderCurve.values().toList(),
                    s.crossfaderCurve,
                    { it.label }
                ) { actions.onSettingsChange(s.copy(crossfaderCurve = it)) }
                OptionRow(
                    "Tempo range",
                    listOf(0.08f, 0.10f, 0.16f),
                    s.tempoRange,
                    { "±${(it * 100).toInt()}%" }
                ) { actions.onSettingsChange(s.copy(tempoRange = it)) }
                SectionLabel("DECK COLOURS")
                ColorRow("Deck 1", s.deckAColor) {
                    actions.onSettingsChange(s.copy(deckAColor = it))
                }
                ColorRow("Deck 2", s.deckBColor) {
                    actions.onSettingsChange(s.copy(deckBColor = it))
                }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, color = DeckAAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun <T> OptionRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = MutedText, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                val active = option == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) DeckAAccent else Color(0xFF23232F))
                        .clickable { onSelect(option) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = optionLabel(option),
                        color = if (active) Color.Black else Color(0xFFD0D0E0),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = PrimaryText, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ColorRow(label: String, selected: Long, onSelect: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = MutedText, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsStore.DECK_COLORS.forEach { colorLong ->
                val active = colorLong == selected
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(colorLong))
                        .then(
                            if (active) Modifier.border(2.dp, Color.White, CircleShape)
                            else Modifier
                        )
                        .clickable { onSelect(colorLong) }
                )
            }
        }
    }
}

private fun knobValue(state: DjState, knob: KnobId): Int = when (knob) {
    KnobId.GAIN_A -> state.deckA.gain
    KnobId.GAIN_B -> state.deckB.gain
    KnobId.MASTER -> state.masterGain
    KnobId.CUE_MIX -> state.cueMix
}

@Composable
private fun KnobDialog(state: DjState, actions: DjActions) {
    val knob = state.activeKnob ?: return
    val value = knobValue(state, knob)
    val accent = when (knob) {
        KnobId.GAIN_A -> Color(state.settings.deckAColor)
        KnobId.GAIN_B -> Color(state.settings.deckBColor)
        KnobId.MASTER -> Color(state.settings.deckAColor)
        KnobId.CUE_MIX -> Color(0xFFB0B0C8)
    }
    val haptics = LocalHapticFeedback.current
    var sliderSnapped by remember { mutableStateOf(false) }
    DjDialog(
        title = knob.label,
        accent = accent,
        onDismiss = { state.activeKnob = null }
    ) {
        Text(
            text = "${(value * 100f / 127f).roundToInt()}%",
            color = PrimaryText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = {
                var next = it.roundToInt().coerceIn(0, 127)
                if (abs(next - 64) <= 3) {
                    next = 64
                    if (!sliderSnapped) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        sliderSnapped = true
                    }
                } else {
                    sliderSnapped = false
                }
                actions.onKnobChange(knob, next)
            },
            valueRange = 0f..127f,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Color(0xFF2A2A3A)
            )
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DialogButton("-10", { actions.onKnobChange(knob, (value - 10).coerceIn(0, 127)) }, Modifier.weight(1f))
            DialogButton("-1", { actions.onKnobChange(knob, (value - 1).coerceIn(0, 127)) }, Modifier.weight(1f))
            DialogButton("+1", { actions.onKnobChange(knob, (value + 1).coerceIn(0, 127)) }, Modifier.weight(1f))
            DialogButton("+10", { actions.onKnobChange(knob, (value + 10).coerceIn(0, 127)) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LogOverlay(state: DjState) {
    Box(Modifier.fillMaxSize().background(Color(0xEE000000)).padding(12.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("MIDI MONITOR", color = DeckAAccent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "CLOSE",
                    color = MutedText,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { state.showLog = false }
                        .padding(6.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxSize().background(Color(0xFF08080E)).padding(6.dp)) {
                Text(
                    state.logText,
                    color = Color(0xFFB0B0C8),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun UpdateDialog(state: DjState, actions: DjActions) {
    val accent = Color(0xFFB0B0C8)
    DjDialog(
        title = "App updates",
        accent = accent,
        onDismiss = { state.showUpdateDialog = false },
        confirmLabel = null,
        extraActions = {
            TextButton(onClick = actions.onCheckUpdate) { Text("Check", color = accent) }
            TextButton(
                onClick = actions.onInstallUpdate,
                enabled = state.updateInfo != null
            ) { Text("Install", color = if (state.updateInfo != null) accent else MutedText) }
            TextButton(onClick = { state.showUpdateDialog = false }) {
                Text("Close", color = MutedText)
            }
        }
    ) {
        Text("Manifest URL (update.json)", fontSize = 11.sp, color = MutedText)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = state.updateUrl,
            onValueChange = actions.onUpdateUrlChange,
            singleLine = true,
            textStyle = TextStyle(color = PrimaryText, fontSize = 12.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = Color(0xFF3A3A4C),
                cursorColor = accent
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.updateStatus.ifEmpty { "Not checked yet." },
            fontSize = 11.sp,
            color = PrimaryText
        )
        state.updateInfo?.let { info ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Available: v${info.versionName} (${info.versionCode})",
                fontSize = 12.sp,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            if (info.notes.isNotEmpty()) {
                Text(info.notes, fontSize = 11.sp, color = MutedText)
            }
        }
    }
}
