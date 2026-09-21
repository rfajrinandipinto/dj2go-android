package com.dj2go.audio

/**
 * Eight sampler slots (four per deck on the DJ2GO2 Touch). Samples are short
 * clips held in memory and mixed straight into the master bus.
 */
class Sampler {

    class Slot {
        @Volatile var data: PcmData? = null
        @Volatile var name: String = ""
        @Volatile var playing = false
        @Volatile var position = 0.0
        @Volatile var gain = 0.9f
    }

    val slots = Array(SLOT_COUNT) { Slot() }

    fun assign(index: Int, data: PcmData, name: String) {
        val slot = slots.getOrNull(index) ?: return
        slot.data = data
        slot.name = name
        slot.playing = false
        slot.position = 0.0
    }

    fun trigger(index: Int) {
        val slot = slots.getOrNull(index) ?: return
        if (slot.data == null) return
        slot.position = 0.0
        slot.playing = true
    }

    fun stop(index: Int) {
        slots.getOrNull(index)?.playing = false
    }

    fun names(): List<String> = slots.map { it.name }

    fun playingIndices(): Set<Int> =
        slots.indices.filter { slots[it].playing }.toSet()

    companion object {
        const val SLOT_COUNT = 8
        const val SLOTS_PER_DECK = 4
    }
}
