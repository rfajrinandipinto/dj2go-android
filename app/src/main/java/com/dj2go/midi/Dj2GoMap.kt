package com.dj2go.midi

/**
 * Maps raw Numark DJ2GO2 Touch MIDI messages to [ControlEvent]s.
 *
 * The DJ2GO2 Touch is channel based:
 *  - Deck A controls use MIDI channel 1 (0-based channel 0)
 *  - Deck B controls use MIDI channel 2 (0-based channel 1)
 *  - Global controls (browse, crossfader, master/headphone gain, load) use
 *    MIDI channel 16 (0-based channel 15)
 *
 * Performance pads live on channels 5 and 6 (0-based 4 and 5).
 * Control numbers mirror the official Mixxx mapping for this controller.
 */
object Dj2GoMap {

    private const val DECK_A = 0
    private const val DECK_B = 1
    private const val PAD_A = 4
    private const val PAD_B = 5
    private const val GLOBAL = 15

    private data class Key(val channel: Int, val number: Int)

    private data class Binding(
        val deck: Deck?,
        val control: ControlId,
        val index: Int = 0
    )

    private val ccBindings: Map<Key, Binding> = buildMap {
        put(Key(DECK_A, 0x09), Binding(Deck.A, ControlId.RATE))
        put(Key(DECK_A, 0x16), Binding(Deck.A, ControlId.GAIN))
        put(Key(DECK_A, 0x06), Binding(Deck.A, ControlId.JOG))
        put(Key(DECK_B, 0x09), Binding(Deck.B, ControlId.RATE))
        put(Key(DECK_B, 0x16), Binding(Deck.B, ControlId.GAIN))
        put(Key(DECK_B, 0x06), Binding(Deck.B, ControlId.JOG))
        put(Key(GLOBAL, 0x00), Binding(null, ControlId.BROWSE))
        put(Key(GLOBAL, 0x08), Binding(null, ControlId.CROSSFADER))
        put(Key(GLOBAL, 0x0A), Binding(null, ControlId.MASTER_GAIN))
        put(Key(GLOBAL, 0x0C), Binding(null, ControlId.HEADPHONE_GAIN))
    }

    private val noteBindings: Map<Key, Binding> = buildMap {
        // ---- Deck A transport ----
        put(Key(DECK_A, 0x00), Binding(Deck.A, ControlId.PLAY))
        put(Key(DECK_A, 0x01), Binding(Deck.A, ControlId.CUE))
        put(Key(DECK_A, 0x02), Binding(Deck.A, ControlId.SYNC))
        put(Key(DECK_A, 0x1B), Binding(Deck.A, ControlId.PFL))
        put(Key(DECK_A, 0x06), Binding(Deck.A, ControlId.WHEEL_TOUCH))
        put(Key(GLOBAL, 0x02), Binding(Deck.A, ControlId.LOAD))

        // ---- Deck B transport ----
        put(Key(DECK_B, 0x00), Binding(Deck.B, ControlId.PLAY))
        put(Key(DECK_B, 0x01), Binding(Deck.B, ControlId.CUE))
        put(Key(DECK_B, 0x02), Binding(Deck.B, ControlId.SYNC))
        put(Key(DECK_B, 0x1B), Binding(Deck.B, ControlId.PFL))
        put(Key(DECK_B, 0x06), Binding(Deck.B, ControlId.WHEEL_TOUCH))
        put(Key(GLOBAL, 0x03), Binding(Deck.B, ControlId.LOAD))

        // ---- Deck A pads / loops ----
        put(Key(PAD_A, 0x01), Binding(Deck.A, ControlId.HOT_CUE, 0))
        put(Key(PAD_A, 0x02), Binding(Deck.A, ControlId.HOT_CUE, 1))
        put(Key(PAD_A, 0x03), Binding(Deck.A, ControlId.HOT_CUE, 2))
        put(Key(PAD_A, 0x04), Binding(Deck.A, ControlId.HOT_CUE, 3))
        put(Key(PAD_A, 0x09), Binding(Deck.A, ControlId.HOT_CUE, 0))
        put(Key(PAD_A, 0x0A), Binding(Deck.A, ControlId.HOT_CUE, 1))
        put(Key(PAD_A, 0x0B), Binding(Deck.A, ControlId.HOT_CUE, 2))
        put(Key(PAD_A, 0x0C), Binding(Deck.A, ControlId.HOT_CUE, 3))
        put(Key(PAD_A, 0x11), Binding(Deck.A, ControlId.BEAT_LOOP, 0))
        put(Key(PAD_A, 0x12), Binding(Deck.A, ControlId.BEAT_LOOP, 1))
        put(Key(PAD_A, 0x13), Binding(Deck.A, ControlId.BEAT_LOOP, 2))
        put(Key(PAD_A, 0x14), Binding(Deck.A, ControlId.BEAT_LOOP, 3))
        put(Key(PAD_A, 0x21), Binding(Deck.A, ControlId.LOOP_IN))
        put(Key(PAD_A, 0x22), Binding(Deck.A, ControlId.LOOP_OUT))
        put(Key(PAD_A, 0x23), Binding(Deck.A, ControlId.LOOP_TOGGLE))
        put(Key(PAD_A, 0x24), Binding(Deck.A, ControlId.RELOOP_STOP))
        put(Key(PAD_A, 0x31), Binding(Deck.A, ControlId.SAMPLER, 0))
        put(Key(PAD_A, 0x32), Binding(Deck.A, ControlId.SAMPLER, 1))
        put(Key(PAD_A, 0x33), Binding(Deck.A, ControlId.SAMPLER, 2))
        put(Key(PAD_A, 0x34), Binding(Deck.A, ControlId.SAMPLER, 3))

        // ---- Deck B pads / loops ----
        put(Key(PAD_B, 0x01), Binding(Deck.B, ControlId.HOT_CUE, 0))
        put(Key(PAD_B, 0x02), Binding(Deck.B, ControlId.HOT_CUE, 1))
        put(Key(PAD_B, 0x03), Binding(Deck.B, ControlId.HOT_CUE, 2))
        put(Key(PAD_B, 0x04), Binding(Deck.B, ControlId.HOT_CUE, 3))
        put(Key(PAD_B, 0x09), Binding(Deck.B, ControlId.HOT_CUE, 0))
        put(Key(PAD_B, 0x0A), Binding(Deck.B, ControlId.HOT_CUE, 1))
        put(Key(PAD_B, 0x0B), Binding(Deck.B, ControlId.HOT_CUE, 2))
        put(Key(PAD_B, 0x0C), Binding(Deck.B, ControlId.HOT_CUE, 3))
        put(Key(PAD_B, 0x11), Binding(Deck.B, ControlId.BEAT_LOOP, 0))
        put(Key(PAD_B, 0x12), Binding(Deck.B, ControlId.BEAT_LOOP, 1))
        put(Key(PAD_B, 0x13), Binding(Deck.B, ControlId.BEAT_LOOP, 2))
        put(Key(PAD_B, 0x14), Binding(Deck.B, ControlId.BEAT_LOOP, 3))
        put(Key(PAD_B, 0x21), Binding(Deck.B, ControlId.LOOP_IN))
        put(Key(PAD_B, 0x22), Binding(Deck.B, ControlId.LOOP_OUT))
        put(Key(PAD_B, 0x23), Binding(Deck.B, ControlId.LOOP_TOGGLE))
        put(Key(PAD_B, 0x24), Binding(Deck.B, ControlId.RELOOP_STOP))
        put(Key(PAD_B, 0x31), Binding(Deck.B, ControlId.SAMPLER, 0))
        put(Key(PAD_B, 0x32), Binding(Deck.B, ControlId.SAMPLER, 1))
        put(Key(PAD_B, 0x33), Binding(Deck.B, ControlId.SAMPLER, 2))
        put(Key(PAD_B, 0x34), Binding(Deck.B, ControlId.SAMPLER, 3))

        // ---- Global ----
        put(Key(GLOBAL, 0x06), Binding(null, ControlId.BROWSE_PRESS))
    }

    /** Returns a decoded event, or null when the message is not a mapped control. */
    fun map(message: MidiMessage): ControlEvent? {
        val key = Key(message.channel, message.data1)
        return when (message.type) {
            MidiType.CONTROL_CHANGE -> {
                val binding = ccBindings[key] ?: return null
                val delta = if (binding.control.kind == ControlKind.ENCODER) {
                    relative(message.data2)
                } else {
                    0
                }
                ControlEvent(
                    deck = binding.deck,
                    control = binding.control,
                    value = message.data2,
                    delta = delta,
                    pressed = false,
                    index = binding.index,
                    message = message
                )
            }

            MidiType.NOTE_ON, MidiType.NOTE_OFF -> {
                val binding = noteBindings[key] ?: return null
                val pressed = message.type == MidiType.NOTE_ON && message.data2 > 0
                ControlEvent(
                    deck = binding.deck,
                    control = binding.control,
                    value = message.data2,
                    delta = 0,
                    pressed = pressed,
                    index = binding.index,
                    message = message
                )
            }

            else -> null
        }
    }

    /** DJ2GO2 Touch encoders send 1 = forward and 127 = backward (two's complement). */
    private fun relative(value: Int): Int = if (value < 64) value else value - 128
}
