package com.dj2go.midi

/** Live state of one deck. */
class DeckState {
    var trackName: String = ""
    var playing = false
    var cue = false
    var sync = false
    var pfl = false
    var keyLock = false
    var wheelTouched = false
    var loopIn = false
    var loopOut = false
    var loopActive = false
    var rate = 64
    var gain = 100
    var eqLow = 64
    var eqMid = 64
    var eqHigh = 64
    var filter = 64
    var jog = 0
    var positionMs = 0L
    var durationMs = 0L
    val heldPads = mutableSetOf<String>()

    fun line(label: String): String = buildString {
        append(label.padEnd(7))
        append(if (playing) "PLAY " else "stop ")
        append("| cue:${Mixer.onOff(cue)} sync:${Mixer.onOff(sync)} pfl:${Mixer.onOff(pfl)} ")
        append("| rate:${rate.toString().padStart(3)} gain:${gain.toString().padStart(3)} ")
        append("| loop:${if (loopActive) "ON" else if (loopIn || loopOut) "set" else "--"} ")
        append("| ${Mixer.formatTime(positionMs)}/${Mixer.formatTime(durationMs)} ")
        append("| ${trackName.ifEmpty { "(no track)" }}")
    }
}

/**
 * Applies [ControlEvent]s to a small model of the mixer.
 *
 * This is UI-free so the same state can drive the audio engine. [apply] returns
 * a short human readable description of what changed, or null when the event
 * did not change anything.
 */
class Mixer {
    val deckA = DeckState()
    val deckB = DeckState()

    var crossfader = 64
    var cueMix = 0
    var masterGain = 100
    var headphoneGain = 100
    var browse = 0

    private fun deckOf(deck: Deck): DeckState = if (deck == Deck.A) deckA else deckB

    fun deck(deck: Deck): DeckState = deckOf(deck)

    private fun padEvent(
        target: DeckState?,
        event: ControlEvent,
        where: String,
        label: String
    ): String? {
        if (target == null) return null
        val key = "${event.control.name}:${event.index}"
        return if (event.pressed) {
            target.heldPads.add(key)
            "$where $label ${event.index + 1}"
        } else {
            target.heldPads.remove(key)
            null
        }
    }

    fun apply(event: ControlEvent): String? {
        val deck = event.deck
        val target = deck?.let { deckOf(it) }
        val where = deck?.let { "Deck $it" } ?: "Mixer"

        return when (event.control) {
            ControlId.PLAY -> {
                if (!event.pressed || target == null) return null
                target.playing = !target.playing
                "$where PLAY -> ${onOff(target.playing)}"
            }

            ControlId.CUE -> {
                if (target == null) return null
                target.cue = event.pressed
                "$where CUE -> ${onOff(target.cue)}"
            }

            ControlId.SYNC -> {
                if (!event.pressed || target == null) return null
                target.sync = !target.sync
                "$where SYNC -> ${onOff(target.sync)}"
            }

            ControlId.PFL -> {
                if (!event.pressed || target == null) return null
                target.pfl = !target.pfl
                "$where PFL -> ${onOff(target.pfl)}"
            }

            ControlId.LOAD -> {
                if (!event.pressed || target == null) return null
                "$where LOAD"
            }

            ControlId.WHEEL_TOUCH -> {
                if (target == null) return null
                target.wheelTouched = event.pressed
                "$where wheel ${if (event.pressed) "touched" else "released"}"
            }

            ControlId.HOT_CUE -> padEvent(target, event, where, "HOT CUE")

            ControlId.BEAT_LOOP -> padEvent(target, event, where, "BEAT LOOP")

            ControlId.SAMPLER -> padEvent(target, event, where, "SAMPLER")

            ControlId.LOOP_IN -> {
                if (!event.pressed || target == null) return null
                target.loopIn = !target.loopIn
                "$where LOOP IN -> ${onOff(target.loopIn)}"
            }

            ControlId.LOOP_OUT -> {
                if (!event.pressed || target == null) return null
                target.loopOut = !target.loopOut
                "$where LOOP OUT -> ${onOff(target.loopOut)}"
            }

            ControlId.LOOP_TOGGLE -> {
                if (!event.pressed || target == null) return null
                target.loopActive = !target.loopActive
                "$where LOOP -> ${onOff(target.loopActive)}"
            }

            ControlId.RELOOP_STOP -> {
                if (!event.pressed || target == null) return null
                target.loopActive = false
                "$where RELOOP/STOP"
            }

            ControlId.RATE -> {
                if (target == null) return null
                target.rate = event.value
                "$where rate = ${event.value}"
            }

            ControlId.GAIN -> {
                if (target == null) return null
                target.gain = event.value
                "$where gain = ${event.value}"
            }

            ControlId.EQ_LOW -> {
                if (target == null) return null
                target.eqLow = event.value
                "$where low = ${event.value}"
            }

            ControlId.EQ_MID -> {
                if (target == null) return null
                target.eqMid = event.value
                "$where mid = ${event.value}"
            }

            ControlId.EQ_HIGH -> {
                if (target == null) return null
                target.eqHigh = event.value
                "$where high = ${event.value}"
            }

            ControlId.FILTER -> {
                if (target == null) return null
                target.filter = event.value
                "$where filter = ${event.value}"
            }

            ControlId.JOG -> {
                if (target == null) return null
                target.jog += event.delta
                "$where jog ${if (event.delta >= 0) "+" else ""}${event.delta}"
            }

            ControlId.CROSSFADER -> {
                crossfader = event.value
                "Crossfader = ${event.value}"
            }

            ControlId.MASTER_GAIN -> {
                masterGain = event.value
                "Master gain = ${event.value}"
            }

            ControlId.HEADPHONE_GAIN -> {
                headphoneGain = event.value
                "Headphones = ${event.value}"
            }

            ControlId.BROWSE -> {
                browse += event.delta
                "Browse ${if (event.delta >= 0) "+" else ""}${event.delta}"
            }

            ControlId.BROWSE_PRESS -> {
                if (!event.pressed) return null
                "Browse press"
            }

            ControlId.BEAT_JUMP -> {
                if (!event.pressed || target == null) return null
                "$where BEAT JUMP ${event.value}"
            }

            ControlId.KEY_LOCK -> {
                if (!event.pressed || target == null) return null
                target.keyLock = !target.keyLock
                "$where KEY LOCK -> ${onOff(target.keyLock)}"
            }

            ControlId.CUE_MIX -> {
                cueMix = event.value
                "Cue mix = ${event.value}"
            }
        }
    }

    fun snapshot(): String = buildString {
        appendLine(deckA.line("Deck A"))
        appendLine(deckB.line("Deck B"))
        appendLine(
            "Crossfader: ${crossfader.toString().padStart(3)}   " +
                "Master: ${masterGain.toString().padStart(3)}   " +
                "Headphones: ${headphoneGain.toString().padStart(3)}"
        )
        append("Browse: $browse")
    }

    companion object {
        fun onOff(value: Boolean): String = if (value) "on" else "off"

        fun formatTime(ms: Long): String {
            val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
    }
}
