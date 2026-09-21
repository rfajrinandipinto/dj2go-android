package com.dj2go.midi

/**
 * A decoded MIDI message that has been matched against the DJ2GO2 Touch map,
 * or an event synthesised by the on-screen UI.
 *
 * @param value raw 0..127 value (fader/CC value or note velocity)
 * @param delta signed step for encoders (+1 / -1 for jog wheel and browse knob)
 * @param pressed true when a button is being pushed, false when released
 * @param index 0-based pad index for [ControlId.HOT_CUE], [ControlId.BEAT_LOOP]
 *              and [ControlId.SAMPLER]
 * @param message the source MIDI message, or null for UI-originated events
 */
data class ControlEvent(
    val deck: Deck?,
    val control: ControlId,
    val value: Int,
    val delta: Int,
    val pressed: Boolean,
    val index: Int,
    val message: MidiMessage?
)
