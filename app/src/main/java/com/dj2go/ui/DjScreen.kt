package com.dj2go.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dj2go.audio.OutputDevices
import com.dj2go.midi.ControlId
import com.dj2go.midi.Deck
import com.dj2go.midi.Mixer

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
    val onTestTone: () -> Unit,
    val onOpenUpdate: () -> Unit,
    val onUpdateUrlChange: (String) -> Unit,
    val onCheckUpdate: () -> Unit,
    val onInstallUpdate: () -> Unit
)

@Composable
fun DjScreen(state: DjState, actions: DjActions) {
    Box(Modifier.fillMaxSize().background(ScreenBackground)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(state, actions)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                DeckPanel(Deck.A, state.deckA, actions, Modifier.weight(1f))
                MixerPanel(state, actions, Modifier.width(210.dp).fillMaxHeight())
                DeckPanel(Deck.B, state.deckB, actions, Modifier.weight(1f))
            }
            LibraryPanel(state, actions, Modifier.fillMaxWidth().height(190.dp))
        }
        if (state.showLog) LogOverlay(state)
        if (state.showUpdateDialog) UpdateDialog(state, actions)
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
private fun DeckPanel(deck: Deck, ui: DeckUi, actions: DjActions, modifier: Modifier) {
    val accent = if (deck == Deck.A) DeckAAccent else DeckBAccent
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
            Text(Mixer.formatTime(ui.positionMs), color = PrimaryText, fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "-${Mixer.formatTime((ui.durationMs - ui.positionMs).coerceAtLeast(0L))}",
                color = MutedText,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(4.dp))
        OverviewWaveform(
            ui.waveform,
            ui.positionFrames,
            Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(4.dp))
        )

        Spacer(Modifier.height(4.dp))
        MainWaveform(
            ui.waveform,
            ui.beatGrid,
            ui.positionFrames,
            ui.sampleRate,
            6f,
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(4.dp))
        )

        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(150.dp)) {
            JogWheel(ui.positionFrames, ui.sampleRate, accent, ui.playing, Modifier.size(140.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                TransportRow(deck, ui, accent, actions)
                Spacer(Modifier.height(6.dp))
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
                Knob(ui.gain, accent, true, Modifier.size(34.dp))
                Text("GAIN", color = MutedText, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun TransportRow(deck: Deck, ui: DeckUi, accent: Color, actions: DjActions) {
    Row(Modifier.fillMaxWidth().height(32.dp)) {
        TransportButton(
            "CUE", ui.cue, accent,
            { actions.onTransport(deck, ControlId.CUE) },
            Modifier.weight(1f).fillMaxHeight()
        )
        Spacer(Modifier.width(5.dp))
        TransportButton(
            if (ui.playing) "PAUSE" else "PLAY", ui.playing, accent,
            { actions.onTransport(deck, ControlId.PLAY) },
            Modifier.weight(1f).fillMaxHeight()
        )
        Spacer(Modifier.width(5.dp))
        TransportButton(
            "SYNC", ui.sync, accent,
            { actions.onTransport(deck, ControlId.SYNC) },
            Modifier.weight(1f).fillMaxHeight()
        )
        Spacer(Modifier.width(5.dp))
        TransportButton(
            "PFL", ui.pfl, accent,
            { actions.onPfl(deck) },
            Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
private fun PadGrid(deck: Deck, ui: DeckUi, accent: Color, actions: DjActions, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        PadRow(deck, ui, accent, actions, ControlId.HOT_CUE, Modifier.weight(1f))
        Spacer(Modifier.height(5.dp))
        PadRow(deck, ui, accent, actions, ControlId.BEAT_LOOP, Modifier.weight(1f))
    }
}

@Composable
private fun PadRow(
    deck: Deck,
    ui: DeckUi,
    accent: Color,
    actions: DjActions,
    control: ControlId,
    modifier: Modifier
) {
    Row(modifier.fillMaxWidth()) {
        for (index in 0 until 4) {
            val active = "${control.name}:$index" in ui.heldPads
            Pad(
                label = "${index + 1}",
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
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ChannelStrip("1", DeckAAccent, state.deckA)
            ChannelStrip("2", DeckBAccent, state.deckB)
        }
        Spacer(Modifier.weight(1f))
        Text("MASTER", color = MutedText, fontSize = 9.sp)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Knob(state.masterGain, DeckAAccent, true, Modifier.size(36.dp))
            Spacer(Modifier.width(10.dp))
            VuMeter(state.masterLevel, Modifier.width(12.dp).fillMaxHeight())
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
private fun ChannelStrip(label: String, accent: Color, deck: DeckUi) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Knob(deck.gain, accent, true, Modifier.size(34.dp))
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
    Column(modifier.background(Color(0xFF0C0C14)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("LIBRARY", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                text = state.libraryFolderLabel,
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
            Spacer(Modifier.width(6.dp))
            TransportButton("LOAD 1", false, DeckAAccent, { actions.onLoad(Deck.A) }, Modifier.width(72.dp).height(26.dp))
            Spacer(Modifier.width(6.dp))
            TransportButton("LOAD 2", false, DeckBAccent, { actions.onLoad(Deck.B) }, Modifier.width(72.dp).height(26.dp))
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(state.library) { index, track ->
                val selected = index == state.libraryIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .background(if (selected) DeckAAccent.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable { actions.onLibrarySelect(index) }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        color = MutedText,
                        fontSize = 9.sp,
                        modifier = Modifier.width(30.dp)
                    )
                    Text(
                        text = track.name,
                        color = if (selected) PrimaryText else Color(0xFFB0B0C8),
                        fontSize = 10.sp,
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
                        fontSize = 10.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(34.dp)) {
            for (index in 0 until 8) {
                val name = state.samplerNames.getOrNull(index).orEmpty()
                SamplerPad(
                    label = if (name.isNotEmpty()) name.take(9) else "S${index + 1}",
                    active = index in state.samplerPlaying,
                    loaded = name.isNotEmpty(),
                    accent = if (index < 4) DeckAAccent else DeckBAccent,
                    onTrigger = { actions.onSamplerTrigger(index) },
                    onAssign = { actions.onSamplerAssign(index) },
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(state.lastEvent, color = MutedText, fontSize = 9.sp, maxLines = 1)
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
    AlertDialog(
        onDismissRequest = { state.showUpdateDialog = false },
        title = { Text("App updates") },
        text = {
            Column {
                Text("Manifest URL (version.json)", fontSize = 11.sp, color = MutedText)
                OutlinedTextField(
                    value = state.updateUrl,
                    onValueChange = actions.onUpdateUrlChange,
                    singleLine = true,
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
                        color = DeckAAccent,
                        fontWeight = FontWeight.Bold
                    )
                    if (info.notes.isNotEmpty()) {
                        Text(info.notes, fontSize = 11.sp, color = MutedText)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onCheckUpdate) { Text("Check") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = actions.onInstallUpdate,
                    enabled = state.updateInfo != null
                ) { Text("Install") }
                TextButton(onClick = { state.showUpdateDialog = false }) { Text("Close") }
            }
        }
    )
}
