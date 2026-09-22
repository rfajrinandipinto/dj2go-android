package com.dj2go.midi

/** How a physical control behaves. */
enum class ControlKind { BUTTON, FADER, ENCODER }

/** Which deck a control belongs to. Mixer / browse controls have no deck. */
enum class Deck { A, B }

/**
 * Every control the Numark DJ2GO2 Touch exposes.
 *
 * Control numbers come from the DJ2GO2 Touch MIDI chart (the official Mixxx
 * mapping). Deck A is MIDI channel 1, deck B is channel 2 and global controls
 * are on channel 16.
 */
enum class ControlId(val kind: ControlKind) {
    // Per-deck buttons
    PLAY(ControlKind.BUTTON),
    CUE(ControlKind.BUTTON),
    SYNC(ControlKind.BUTTON),
    PFL(ControlKind.BUTTON),
    LOAD(ControlKind.BUTTON),
    WHEEL_TOUCH(ControlKind.BUTTON),

    // Performance pads (index 0..3)
    HOT_CUE(ControlKind.BUTTON),
    BEAT_LOOP(ControlKind.BUTTON),
    SAMPLER(ControlKind.BUTTON),

    // Loop controls
    LOOP_IN(ControlKind.BUTTON),
    LOOP_OUT(ControlKind.BUTTON),
    LOOP_TOGGLE(ControlKind.BUTTON),
    RELOOP_STOP(ControlKind.BUTTON),

    // Per-deck continuous controls
    RATE(ControlKind.FADER),
    GAIN(ControlKind.FADER),
    JOG(ControlKind.ENCODER),

    // Per-deck EQ and filter
    EQ_LOW(ControlKind.FADER),
    EQ_MID(ControlKind.FADER),
    EQ_HIGH(ControlKind.FADER),
    FILTER(ControlKind.FADER),

    // Per-deck FX
    FX(ControlKind.BUTTON),
    FX_WET(ControlKind.FADER),

    // Beat tools (UI driven)
    BEAT_JUMP(ControlKind.BUTTON),
    KEY_LOCK(ControlKind.BUTTON),

    // Mixer
    CROSSFADER(ControlKind.FADER),
    CUE_MIX(ControlKind.FADER),
    MASTER_GAIN(ControlKind.FADER),
    HEADPHONE_GAIN(ControlKind.FADER),

    // Browser
    BROWSE(ControlKind.ENCODER),
    BROWSE_PRESS(ControlKind.BUTTON)
}
