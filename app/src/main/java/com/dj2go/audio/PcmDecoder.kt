package com.dj2go.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Decodes any audio file the platform can read into stereo 16-bit PCM.
 *
 * [decode] streams to a temporary file (memory mapped) for full tracks;
 * [decodeToMemory] keeps short clips on the heap for the sampler. Both run off
 * the main thread (see [AudioEngine]).
 */
object PcmDecoder {

    private const val TIMEOUT_US = 10_000L

    fun decode(context: Context, uri: Uri): PcmTrack {
        val tempFile = File.createTempFile("deck_", ".pcm", context.cacheDir)
        val result = try {
            BufferedOutputStream(FileOutputStream(tempFile)).use { sink ->
                runDecode(context, uri, sink, wantWaveform = true)
            }
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        }

        if (result.frames <= 0) {
            tempFile.delete()
            error("Decoded no audio")
        }

        val source = RandomAccessFile(tempFile, "r")
        val mapped = source.channel.map(FileChannel.MapMode.READ_ONLY, 0, source.length())
        mapped.order(ByteOrder.LITTLE_ENDIAN)
        val beatGrid = BeatDetector.detect(mapped, result.frames, result.sampleRate)
        val key = KeyDetector.detect(mapped, result.frames, result.sampleRate)
        val waveform = result.waveform ?: WaveformBuilder(result.sampleRate).finish()
        return PcmTrack(
            source,
            mapped,
            result.frames,
            result.sampleRate,
            waveform,
            beatGrid,
            key?.name ?: "",
            key?.camelot ?: ""
        )
    }

    fun decodeToMemory(context: Context, uri: Uri): PcmData {
        val bytes = ByteArrayOutputStream()
        val result = runDecode(context, uri, bytes, wantWaveform = false)
        if (result.frames <= 0) error("Decoded no audio")
        val raw = bytes.toByteArray()
        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val samples = ShortArray(raw.size / 2)
        buffer.asShortBuffer().get(samples)
        return PcmData(samples, result.sampleRate)
    }

    private fun runDecode(
        context: Context,
        uri: Uri,
        sink: OutputStream,
        wantWaveform: Boolean
    ): DecodeResult {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = selectAudioTrack(extractor)
            require(trackIndex >= 0) { "No audio track found" }
            extractor.selectTrack(trackIndex)

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("No MIME type")
            return transcode(extractor, inputFormat, mime, sink, wantWaveform)
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) return i
        }
        return -1
    }

    private fun transcode(
        extractor: MediaExtractor,
        inputFormat: MediaFormat,
        mime: String,
        sink: OutputStream,
        wantWaveform: Boolean
    ): DecodeResult {
        val codec = MediaCodec.createDecoderByType(mime)
        var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var encoding = AudioFormat.ENCODING_PCM_16BIT
        var totalFrames = 0
        val waveform = if (wantWaveform) WaveformBuilder(sampleRate) else null

        try {
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuffer = codec.getInputBuffer(inIndex)
                        val size = if (inBuffer != null) extractor.readSampleData(inBuffer, 0) else -1
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = codec.outputFormat
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            encoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> if (outIndex >= 0) {
                        val outBuffer = codec.getOutputBuffer(outIndex)
                        if (outBuffer != null && info.size > 0) {
                            outBuffer.position(info.offset)
                            outBuffer.limit(info.offset + info.size)
                            totalFrames += writeStereo(
                                outBuffer, info.size, channels, encoding, sink, waveform
                            )
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
        return DecodeResult(totalFrames, sampleRate, waveform?.finish())
    }

    /** Converts one decoder buffer to interleaved stereo 16-bit and writes it. */
    private fun writeStereo(
        buffer: ByteBuffer,
        size: Int,
        channels: Int,
        encoding: Int,
        sink: OutputStream,
        waveform: WaveformBuilder?
    ): Int {
        if (channels <= 0) return 0
        val bytesPerSample = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
        val frames = size / (bytesPerSample * channels)
        if (frames <= 0) return 0

        val target = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.order(ByteOrder.nativeOrder())

        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val floats = buffer.asFloatBuffer()
            for (frame in 0 until frames) {
                val base = frame * channels
                val l = toShort(floats.get(base))
                val r = if (channels > 1) toShort(floats.get(base + 1)) else l
                waveform?.add(l.toInt(), r.toInt())
                target.putShort(l)
                target.putShort(r)
            }
        } else {
            val shorts = buffer.asShortBuffer()
            for (frame in 0 until frames) {
                val base = frame * channels
                val l = shorts.get(base)
                val r = if (channels > 1) shorts.get(base + 1) else l
                waveform?.add(l.toInt(), r.toInt())
                target.putShort(l)
                target.putShort(r)
            }
        }

        val bytes = ByteArray(target.position())
        target.flip()
        target.get(bytes)
        sink.write(bytes)
        return frames
    }

    private fun toShort(value: Float): Short {
        val scaled = (value * 32767f).toInt()
        return when {
            scaled > 32767 -> Short.MAX_VALUE
            scaled < -32768 -> Short.MIN_VALUE
            else -> scaled.toShort()
        }
    }

    private class DecodeResult(
        val frames: Int,
        val sampleRate: Int,
        val waveform: WaveformData?
    )
}
