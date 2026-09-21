package com.dj2go.midi

enum class MidiType {
    NOTE_OFF,
    NOTE_ON,
    POLY_AFTERTOUCH,
    CONTROL_CHANGE,
    PROGRAM_CHANGE,
    CHANNEL_AFTERTOUCH,
    PITCH_BEND,
    UNKNOWN
}

/**
 * A single, fully decoded MIDI message.
 *
 * @param raw the original bytes (status, data1, data2)
 * @param channel 0-based channel (the DJ2GO uses channel 0)
 * @param data1 note number for note messages, controller number for CC
 * @param data2 velocity for note messages, value for CC
 */
data class MidiMessage(
    val raw: IntArray,
    val type: MidiType,
    val channel: Int,
    val data1: Int,
    val data2: Int
) {
    fun toHex(): String = raw.joinToString(" ") { "%02X".format(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MidiMessage) return false
        return type == other.type &&
            channel == other.channel &&
            data1 == other.data1 &&
            data2 == other.data2 &&
            raw.contentEquals(other.raw)
    }

    override fun hashCode(): Int {
        var result = raw.contentHashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + channel
        result = 31 * result + data1
        result = 31 * result + data2
        return result
    }

    companion object {
        /** Parses one complete MIDI message. Returns null if it is not valid. */
        fun parse(data: ByteArray, length: Int): MidiMessage? {
            if (length < 1) return null
            val status = data[0].toInt() and 0xFF
            if (status < 0x80) return null

            val type = when (status and 0xF0) {
                0x80 -> MidiType.NOTE_OFF
                0x90 -> MidiType.NOTE_ON
                0xA0 -> MidiType.POLY_AFTERTOUCH
                0xB0 -> MidiType.CONTROL_CHANGE
                0xC0 -> MidiType.PROGRAM_CHANGE
                0xD0 -> MidiType.CHANNEL_AFTERTOUCH
                0xE0 -> MidiType.PITCH_BEND
                else -> MidiType.UNKNOWN
            }

            val channel = status and 0x0F
            val data1 = if (length > 1) data[1].toInt() and 0xFF else 0
            val data2 = if (length > 2) data[2].toInt() and 0xFF else 0
            val raw = IntArray(length) { data[it].toInt() and 0xFF }
            return MidiMessage(raw, type, channel, data1, data2)
        }

        /** Number of bytes in a message given its status byte. */
        fun lengthOf(status: Int): Int = when (status and 0xF0) {
            0xC0, 0xD0 -> 2
            0xF0 -> 0 // system / sysex, handled separately
            else -> 3
        }
    }
}
