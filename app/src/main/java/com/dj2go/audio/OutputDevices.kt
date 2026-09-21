package com.dj2go.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Lists the audio outputs the system currently offers: built-in speaker, wired
 * headphones, USB (the DJ2GO2's own sound card) and Bluetooth (A2DP).
 *
 * Bluetooth output needs no extra permission: when an A2DP device is connected
 * it shows up here and Android routes media to it.
 */
object OutputDevices {

    data class Entry(val label: String, val device: AudioDeviceInfo?)

    fun list(context: Context): List<Entry> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val entries = mutableListOf(Entry("System default", null))
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).forEach { device ->
            entries.add(Entry(labelOf(device), device))
        }
        return entries
    }

    private fun labelOf(device: AudioDeviceInfo): String {
        val type = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth (A2DP)"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (SCO)"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
            else -> "Output type ${device.type}"
        }
        val name = device.productName?.toString().orEmpty()
        return if (name.isNotEmpty() && name != type) "$type: $name" else type
    }
}
