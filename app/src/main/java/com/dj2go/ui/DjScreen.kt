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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.State
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
    val onSettingsChange: (DjSettings) -> Unit,
    val onToggleRecord: () -> Unit,
    val onSeek: (Deck, Float) -> Unit,
    val onPadMode: (Deck, PadMode) -> Unit,
    val onAnalyze: () -> Unit
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
            LibraryPanel(state, actions, Modifier.fillMaxWidth().height(76.dp))
        }
        if (state.showLibrary) {
            LibraryOverlay(
                state,
                actions,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp)
                    .fillMaxWidth()
                    .height(320.dp)
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
        Spacer(Modifier.width(12.dp))
        SettingsIconButton(onClick = actions.onOpenSettings)
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
    val posState = if (deck == Deck.A) state.deckAPos else state.deckBPos
    val timeText = when (timeMode) {
        TimeMode.ELAPSED -> Mixer.formatTime(ui.positionMs)
        TimeMode.REMAINING ->
            "-" + Mixer.formatTime((ui.durationMs - ui.positionMs).coerceAtLeast(0L))
        TimeMode.BEATS -> ui.barBeat
    }
    val nearEnd = ui.durationMs > 0L && ui.durationMs - ui.positionMs < 20_000L
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
            Text(
                text = if (ui.key.isNotEmpty()) ui.key else "--",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(" KEY", color = MutedText, fontSize = 9.sp)
            Spacer(Modifier.width(8.dp))
            Text(ui.barBeat, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                text = timeText,
                color = if (nearEnd) Color(0xFFFF3B30) else PrimaryText,
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
                posState,
                accent,
                settings.colorMode,
                onSeek = { fraction -> actions.onSeek(deck, fraction) },
                modifier = Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(4.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            MainWaveform(
                ui.waveform,
                ui.beatGrid,
                posState,
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
            JogWheel(posState, ui.sampleRate, accent, ui.playing, Modifier.size(140.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                TransportRow(deck, ui, accent, actions)
                Spacer(Modifier.height(4.dp))
                BeatJumpRow(deck, ui, accent, actions)
                Spacer(Modifier.height(4.dp))
                PadGrid(deck, ui, state, accent, actions, Modifier.weight(1f))
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
private fun BeatJumpRow(deck: Deck, ui: DeckUi, accent: Color, actions: DjActions) {
    Row(Modifier.fillMaxWidth().height(24.dp)) {
        TransportButton(
            "-4", false, accent,
            { actions.onBeatJump(deck, -4) },
            Modifier.weight(1f).fillMaxHeight()
        )
        Spacer(Modifier.width(4.dp))
        TransportButton(
            "+4", false, accent,
            { actions.onBeatJump(deck, 4) },
            Modifier.weight(1f).fillMaxHeight()
        )
        Spacer(Modifier.width(4.dp))
        TransportButton(
            "KEY LOCK", ui.keyLock, accent,
            { actions.onTransport(deck, ControlId.KEY_LOCK) },
            Modifier.weight(1.7f).fillMaxHeight()
        )
    }
}

@Composable
private fun PadGrid(
    deck: Deck,
    ui: DeckUi,
    state: DjState,
    accent: Color,
    actions: DjActions,
    modifier: Modifier
) {
    val mode = if (deck == Deck.A) state.padModeA else state.padModeB
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(16.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            ModeButton("CUE", mode == PadMode.CUE, accent, { actions.onPadMode(deck, PadMode.CUE) }, Modifier.weight(1f))
            ModeButton("LOOP", mode == PadMode.LOOP, accent, { actions.onPadMode(deck, PadMode.LOOP) }, Modifier.weight(1f))
            ModeButton("SAMPLE", mode == PadMode.SAMPLE, accent, { actions.onPadMode(deck, PadMode.SAMPLE) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        PadRow(deck, ui, state, mode, accent, actions, 0, Modifier.weight(1f))
        Spacer(Modifier.height(4.dp))
        PadRow(deck, ui, state, mode, accent, actions, 4, Modifier.weight(1f))
    }
}

@Composable
private fun ModeButton(
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) accent else Color(0xFF1B1B26))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) Color.Black else MutedText,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PadRow(
    deck: Deck,
    ui: DeckUi,
    state: DjState,
    mode: PadMode,
    accent: Color,
    actions: DjActions,
    base: Int,
    modifier: Modifier
) {
    Row(modifier.fillMaxWidth()) {
        for (offset in 0 until 4) {
            val index = base + offset
            val label = when (mode) {
                PadMode.CUE -> "${index + 1}"
                PadMode.LOOP -> when (index) {
                    0 -> "1"
                    1 -> "2"
                    2 -> "4"
                    3 -> "8"
                    4 -> "IN"
                    5 -> "OUT"
                    6 -> "TGL"
                    else -> "R/S"
                }
                PadMode.SAMPLE -> "S${index + 1}"
            }
            val active = when (mode) {
                PadMode.CUE -> "HOT_CUE:$index" in ui.heldPads
                PadMode.LOOP -> when (index) {
                    0, 1, 2, 3 -> "BEAT_LOOP:$index" in ui.heldPads
                    4 -> "LOOP_IN:0" in ui.heldPads
                    5 -> "LOOP_OUT:0" in ui.heldPads
                    6 -> "LOOP_TOGGLE:0" in ui.heldPads
                    else -> "RELOOP_STOP:0" in ui.heldPads
                }
                PadMode.SAMPLE -> index in state.samplerPlaying
            }
            Pad(
                label = label,
                active = active,
                accent = accent,
                onClick = {
                    when (mode) {
                        PadMode.CUE -> actions.onPad(deck, ControlId.HOT_CUE, index)
                        PadMode.LOOP -> when (index) {
                            0, 1, 2, 3 -> actions.onPad(deck, ControlId.BEAT_LOOP, index)
                            4 -> actions.onPad(deck, ControlId.LOOP_IN, 0)
                            5 -> actions.onPad(deck, ControlId.LOOP_OUT, 0)
                            6 -> actions.onPad(deck, ControlId.LOOP_TOGGLE, 0)
                            else -> actions.onPad(deck, ControlId.RELOOP_STOP, 0)
                        }
                        PadMode.SAMPLE -> actions.onSamplerTrigger(index)
                    }
                },
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
            ChannelStrip("1", Color(state.settings.deckAColor), state.deckA, state.deckALevel, Deck.A, actions)
            ChannelStrip("2", Color(state.settings.deckBColor), state.deckB, state.deckBLevel, Deck.B, actions)
        }
        Spacer(Modifier.weight(1f))
        Text("PHASE", color = MutedText, fontSize = 9.sp)
        Spacer(Modifier.height(2.dp))
        PhaseMeter(
            state.deckAPhase,
            state.deckBPhase,
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
            VuMeter(state.masterLevelState, Modifier.width(12.dp).height(48.dp))
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
    ui: DeckUi,
    level: State<Float>,
    deck: Deck,
    actions: DjActions
) {
    val gainKnob = if (deck == Deck.A) KnobId.GAIN_A else KnobId.GAIN_B
    val lowKnob = if (deck == Deck.A) KnobId.EQ_LOW_A else KnobId.EQ_LOW_B
    val midKnob = if (deck == Deck.A) KnobId.EQ_MID_A else KnobId.EQ_MID_B
    val highKnob = if (deck == Deck.A) KnobId.EQ_HIGH_A else KnobId.EQ_HIGH_B
    val filterKnob = if (deck == Deck.A) KnobId.FILTER_A else KnobId.FILTER_B
    val fxWetKnob = if (deck == Deck.A) KnobId.FX_WET_A else KnobId.FX_WET_B
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Knob(
            ui.gain, accent, true, Modifier.size(32.dp),
            onClick = { actions.onKnobTap(gainKnob) },
            onChange = { actions.onKnobChange(gainKnob, it) }
        )
        Spacer(Modifier.height(2.dp))
        Text("TRIM", color = MutedText, fontSize = 8.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            SmallEqKnob("H", ui.eqHigh, accent, highKnob, actions)
            SmallEqKnob("M", ui.eqMid, accent, midKnob, actions)
            SmallEqKnob("L", ui.eqLow, accent, lowKnob, actions)
            SmallEqKnob("F", ui.filter, accent, filterKnob, actions)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (ui.fxOn) accent else Color(0xFF1B1B26))
                    .clickable { actions.onPad(deck, ControlId.FX, 0) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "FX",
                    color = if (ui.fxOn) Color.Black else MutedText,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            SmallEqKnob("W", ui.fxWet, accent, fxWetKnob, actions)
        }
        Spacer(Modifier.height(8.dp))
        VuMeter(level, Modifier.width(10.dp).height(60.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (ui.pfl) "CUE" else "—",
            color = if (ui.pfl) accent else MutedText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SmallEqKnob(
    label: String,
    value: Int,
    accent: Color,
    knobId: KnobId,
    actions: DjActions
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Knob(
            value, accent, true, Modifier.size(20.dp),
            onClick = { actions.onKnobTap(knobId) },
            onChange = { actions.onKnobChange(knobId, it) }
        )
        Text(label, color = MutedText, fontSize = 7.sp)
    }
}

@Composable
private fun LibraryPanel(state: DjState, actions: DjActions, modifier: Modifier) {
    Column(
        modifier.background(Color(0xFF0C0C14)).padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
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
                maxLines = 1
            )
            if (state.libraryScanning) {
                Spacer(Modifier.width(8.dp))
                Text("scanning...", color = DeckAAccent, fontSize = 9.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(state.lastEvent, color = MutedText, fontSize = 9.sp, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().height(28.dp)) {
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
    }
}

@Composable
private fun LibraryOverlay(state: DjState, actions: DjActions, modifier: Modifier) {
    val listState = rememberLazyListState()
    val shown = remember(state.library, state.libraryQuery, state.librarySortDesc) {
        val base = if (state.libraryQuery.isBlank()) {
            state.library
        } else {
            state.library.filter { it.name.contains(state.libraryQuery, ignoreCase = true) }
        }
        if (state.librarySortDesc) base.sortedByDescending { it.name.lowercase() }
        else base.sortedBy { it.name.lowercase() }
    }
    val selectedUri = state.library.getOrNull(state.libraryIndex)?.uri
    LaunchedEffect(state.libraryIndex, shown) {
        val position = shown.indexOfFirst { it.uri == selectedUri }
        if (position >= 0) listState.scrollToItem(position)
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
            TransportButton("FOLDER", false, DeckAAccent, actions.onPickFolder, Modifier.width(70.dp).height(26.dp))
            Spacer(Modifier.width(6.dp))
            TransportButton("RESCAN", false, Color(0xFF3A3A4C), actions.onRescan, Modifier.width(70.dp).height(26.dp))
            Spacer(Modifier.width(6.dp))
            TransportButton(
                if (state.analysisRunning) "${state.analysisDone}/${state.analysisTotal}" else "ANALYZE",
                state.analysisRunning,
                Color(0xFFB0B0C8),
                actions.onAnalyze,
                Modifier.width(88.dp).height(26.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.libraryQuery,
                onValueChange = { state.libraryQuery = it },
                singleLine = true,
                placeholder = { Text("Search", fontSize = 11.sp, color = MutedText) },
                textStyle = TextStyle(color = PrimaryText, fontSize = 12.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DeckAAccent,
                    unfocusedBorderColor = Color(0xFF3A3A4C),
                    cursorColor = DeckAAccent
                ),
                modifier = Modifier.weight(1f).height(52.dp)
            )
            Spacer(Modifier.width(6.dp))
            DialogButton(
                if (state.librarySortDesc) "Z-A" else "A-Z",
                { state.librarySortDesc = !state.librarySortDesc },
                Modifier.width(54.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
            items(shown) { track ->
                val originalIndex = state.library.indexOf(track)
                val selected = originalIndex == state.libraryIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(if (selected) DeckAAccent.copy(alpha = 0.25f) else Color.Transparent)
                        .clickable { actions.onLibrarySelect(originalIndex) }
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${originalIndex + 1}",
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
                    state.libraryAnalysis[track.uri]?.let { info ->
                        Text(
                            text = "%.0f  %s".format(info.bpm, info.key),
                            color = MutedText,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            if (shown.isEmpty()) {
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
                SectionLabel("BEAT")
                ToggleRow("Quantize (snap cues / loops / jumps)", s.quantize) {
                    actions.onSettingsChange(s.copy(quantize = it))
                }
                ToggleRow("Snap to bar (not beat)", s.quantizeToBar) {
                    actions.onSettingsChange(s.copy(quantizeToBar = it))
                }
                SectionLabel("DECK COLOURS")
                ColorRow("Deck 1", s.deckAColor) {
                    actions.onSettingsChange(s.copy(deckAColor = it))
                }
                ColorRow("Deck 2", s.deckBColor) {
                    actions.onSettingsChange(s.copy(deckBColor = it))
                }
                SectionLabel("TOOLS")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DialogButton("TEST TONE", { actions.onTestTone() }, Modifier.weight(1f))
                    DialogButton(
                        if (state.recording) "STOP REC" else "RECORD",
                        { actions.onToggleRecord() },
                        Modifier.weight(1f)
                    )
                    DialogButton(
                        "UPDATE",
                        {
                            state.showSettings = false
                            actions.onOpenUpdate()
                        },
                        Modifier.weight(1f)
                    )
                    DialogButton(
                        "LOG",
                        {
                            state.showSettings = false
                            state.showLog = true
                        },
                        Modifier.weight(1f)
                    )
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
    KnobId.EQ_LOW_A -> state.deckA.eqLow
    KnobId.EQ_MID_A -> state.deckA.eqMid
    KnobId.EQ_HIGH_A -> state.deckA.eqHigh
    KnobId.FILTER_A -> state.deckA.filter
    KnobId.FX_WET_A -> state.deckA.fxWet
    KnobId.EQ_LOW_B -> state.deckB.eqLow
    KnobId.EQ_MID_B -> state.deckB.eqMid
    KnobId.EQ_HIGH_B -> state.deckB.eqHigh
    KnobId.FILTER_B -> state.deckB.filter
    KnobId.FX_WET_B -> state.deckB.fxWet
    KnobId.MASTER -> state.masterGain
    KnobId.CUE_MIX -> state.cueMix
}

@Composable
private fun KnobDialog(state: DjState, actions: DjActions) {
    val knob = state.activeKnob ?: return
    val value = knobValue(state, knob)
    val accent = when (knob) {
        KnobId.GAIN_A, KnobId.EQ_LOW_A, KnobId.EQ_MID_A, KnobId.EQ_HIGH_A, KnobId.FILTER_A,
        KnobId.FX_WET_A -> Color(state.settings.deckAColor)
        KnobId.GAIN_B, KnobId.EQ_LOW_B, KnobId.EQ_MID_B, KnobId.EQ_HIGH_B, KnobId.FILTER_B,
        KnobId.FX_WET_B -> Color(state.settings.deckBColor)
        KnobId.MASTER -> Color(state.settings.deckAColor)
        KnobId.CUE_MIX -> Color(0xFFB0B0C8)
    }
    val haptics = LocalHapticFeedback.current
    var snapped by remember { mutableStateOf(false) }
    fun applyValue(raw: Int) {
        var next = raw.coerceIn(0, 127)
        if (abs(next - 64) <= 3) {
            next = 64
            if (!snapped) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                snapped = true
            }
        } else {
            snapped = false
        }
        actions.onKnobChange(knob, next)
    }
    DjDialog(
        title = knob.label,
        accent = accent,
        onDismiss = { state.activeKnob = null }
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BigKnob(
                value = value,
                accent = accent,
                onDelta = { delta -> applyValue(knobValue(state, knob) + delta) },
                modifier = Modifier.size(140.dp)
            )
            Spacer(Modifier.width(22.dp))
            VerticalFader(
                value = value,
                accent = accent,
                onChange = { applyValue(it) },
                modifier = Modifier.width(34.dp).height(140.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DialogButton("-10", { applyValue(value - 10) }, Modifier.weight(1f))
            DialogButton("-1", { applyValue(value - 1) }, Modifier.weight(1f))
            DialogButton("+1", { applyValue(value + 1) }, Modifier.weight(1f))
            DialogButton("+10", { applyValue(value + 10) }, Modifier.weight(1f))
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
