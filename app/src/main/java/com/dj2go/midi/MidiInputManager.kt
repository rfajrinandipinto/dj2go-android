package com.dj2go.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper

/**
 * Discovers USB MIDI controllers, opens the first one that can send MIDI and
 * streams decoded [MidiMessage]s to a [Listener].
 *
 * To receive MIDI from a device you open the device's *output* port and connect
 * a [MidiReceiver] to it. Opening a USB MIDI device through [MidiManager] also
 * triggers the system USB permission dialog, so no extra USB plumbing is needed.
 */
class MidiInputManager(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onDeviceList(devices: List<String>)
        fun onConnected(deviceName: String)
        fun onDisconnected()
        fun onMessage(message: MidiMessage)
        fun onError(message: String)
    }

    private val midiManager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var midiDevice: MidiDevice? = null
    private var outputPort: MidiOutputPort? = null
    private var openInfo: MidiDeviceInfo? = null
    private var started = false

    private val receiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            // Called on a binder thread: copy and hop to the main thread so the
            // rest of the app only ever touches state from one thread.
            val copy = msg.copyOfRange(offset, offset + count)
            mainHandler.post { parseMessages(copy, 0, copy.size) }
        }

        override fun onFlush() = Unit
    }

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            publishDeviceList()
            openFirstAvailable()
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            if (device.id == openInfo?.id) {
                closeDevice()
                listener.onDisconnected()
            }
            publishDeviceList()
        }
    }

    fun start() {
        val manager = midiManager
        if (manager == null) {
            listener.onError("Android MIDI service not available on this device.")
            return
        }
        if (started) return
        started = true
        manager.registerDeviceCallback(deviceCallback, mainHandler)
        publishDeviceList()
        openFirstAvailable()
    }

    fun stop() {
        val manager = midiManager ?: return
        if (!started) return
        started = false
        manager.unregisterDeviceCallback(deviceCallback)
        closeDevice()
    }

    fun refresh() {
        publishDeviceList()
        openFirstAvailable()
    }

    private fun publishDeviceList() {
        val manager = midiManager ?: return
        val names = manager.devices
            .filter { it.outputPortCount > 0 }
            .map { nameOf(it) }
        listener.onDeviceList(names)
    }

    private fun openFirstAvailable() {
        if (midiDevice != null) return
        val manager = midiManager ?: return
        val info = manager.devices.firstOrNull { it.outputPortCount > 0 } ?: return
        open(info)
    }

    private fun open(info: MidiDeviceInfo) {
        val manager = midiManager ?: return
        val openListener = MidiManager.OnDeviceOpenedListener { device ->
            if (device == null) {
                listener.onError("Could not open ${nameOf(info)} (permission denied?)")
                return@OnDeviceOpenedListener
            }

            val port = info.ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
            if (port == null) {
                device.close()
                listener.onError("No MIDI output port on ${nameOf(info)}")
                return@OnDeviceOpenedListener
            }

            val output = device.openOutputPort(port.portNumber)
            if (output == null) {
                device.close()
                listener.onError("Could not open the output port on ${nameOf(info)}")
                return@OnDeviceOpenedListener
            }

            output.connect(receiver)
            midiDevice = device
            outputPort = output
            openInfo = info
            listener.onConnected(nameOf(info))
        }
        manager.openDevice(info, openListener, mainHandler)
    }

    private fun closeDevice() {
        runCatching { outputPort?.disconnect(receiver) }
        runCatching { outputPort?.close() }
        runCatching { midiDevice?.close() }
        outputPort = null
        midiDevice = null
        openInfo = null
    }

    private fun nameOf(info: MidiDeviceInfo): String {
        val properties = info.properties
        val manufacturer = properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER).orEmpty()
        val product = properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT).orEmpty()
        val name = "$manufacturer $product".trim()
        return name.ifEmpty { "MIDI device ${info.id}" }
    }

    /**
     * A single USB MIDI packet may contain several messages, so walk the buffer
     * message by message.
     */
    private fun parseMessages(data: ByteArray, offset: Int, count: Int) {
        var i = offset
        val end = offset + count
        while (i < end) {
            val status = data[i].toInt() and 0xFF
            if (status < 0x80) {
                i++ // stray data byte, skip
                continue
            }
            if (status == 0xF0) {
                break // sysex: ignore for now
            }
            val length = MidiMessage.lengthOf(status)
            if (length == 0 || i + length > end) break
            val slice = data.copyOfRange(i, i + length)
            MidiMessage.parse(slice, length)?.let { listener.onMessage(it) }
            i += length
        }
    }
}
