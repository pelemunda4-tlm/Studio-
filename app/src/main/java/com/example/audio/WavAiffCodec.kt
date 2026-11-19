package com.example.audio

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

data class DecodedAudio(
    val left: FloatArray,
    val right: FloatArray,
    val sampleRate: Int,
    val formatLabel: String
)

object WavAiffCodec {

    fun decodeAudioStream(inputStream: InputStream): DecodedAudio? {
        val bytes = inputStream.readBytes()
        if (bytes.size < 12) return null
        val header = String(bytes, 0, 4, Charsets.US_ASCII)
        return when (header) {
            "RIFF" -> decodeWav(bytes)
            "FORM" -> decodeAiff(bytes)
            else -> null
        }
    }

    private fun decodeWav(bytes: ByteArray): DecodedAudio? {
        if (bytes.size < 44) return null
        val wave = String(bytes, 8, 4, Charsets.US_ASCII)
        if (wave != "WAVE") return null

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 12
        var audioFormat = 1
        var channels = 1
        var sampleRate = 48000
        var bitsPerSample = 16
        var dataStart = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = buf.getInt(offset + 4).coerceAtLeast(0)
            val payloadOffset = offset + 8
            if (chunkId == "fmt " && payloadOffset + 16 <= bytes.size) {
                audioFormat = buf.getShort(payloadOffset).toInt() and 0xFFFF
                channels = buf.getShort(payloadOffset + 2).toInt() and 0xFFFF
                sampleRate = buf.getInt(payloadOffset + 4).coerceIn(8000, 192000)
                bitsPerSample = buf.getShort(payloadOffset + 14).toInt() and 0xFFFF
            } else if (chunkId == "data") {
                dataStart = payloadOffset
                dataSize = min(chunkSize, bytes.size - payloadOffset)
                break
            }
            offset = payloadOffset + chunkSize + (chunkSize and 1)
        }

        if (dataStart < 0 || dataSize <= 0 || channels <= 0) return null
        val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
        val totalFrames = dataSize / (bytesPerSample * channels)
        if (totalFrames <= 0) return null

        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)

        var pos = dataStart
        for (i in 0 until totalFrames) {
            var l = 0f
            var r = 0f
            for (ch in 0 until channels) {
                val sample = when {
                    audioFormat == 3 && bitsPerSample == 32 && pos + 4 <= bytes.size -> {
                        buf.getFloat(pos).coerceIn(-1f, 1f)
                    }
                    bitsPerSample == 16 && pos + 2 <= bytes.size -> {
                        buf.getShort(pos) / 32768f
                    }
                    bitsPerSample == 24 && pos + 3 <= bytes.size -> {
                        val b0 = bytes[pos].toInt() and 0xFF
                        val b1 = bytes[pos + 1].toInt() and 0xFF
                        val b2 = bytes[pos + 2].toInt()
                        val raw = (b2 shl 16) or (b1 shl 8) or b0
                        raw / 8388608f
                    }
                    bitsPerSample == 32 && pos + 4 <= bytes.size -> {
                        buf.getInt(pos) / 2147483648f
                    }
                    bitsPerSample == 8 && pos < bytes.size -> {
                        ((bytes[pos].toInt() and 0xFF) - 128) / 128f
                    }
                    else -> 0f
                }
                if (ch == 0) l = sample
                if (ch == 1) r = sample
                pos += bytesPerSample
            }
            left[i] = l
            right[i] = if (channels == 1) l else r
        }

        val formatLabel = if (audioFormat == 3) "WAV 32f" else "WAV ${bitsPerSample}b"
        return DecodedAudio(left, right, sampleRate, formatLabel)
    }

    private fun decodeAiff(bytes: ByteArray): DecodedAudio? {
        if (bytes.size < 38) return null
        val formType = String(bytes, 8, 4, Charsets.US_ASCII)
        if (formType != "AIFF" && formType != "AIFC") return null

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        var offset = 12
        var channels = 1
        var numSampleFrames = 0
        var bitsPerSample = 16
        var sampleRate = 48000
        var ssndStart = -1

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = buf.getInt(offset + 4).coerceAtLeast(0)
            val payloadOffset = offset + 8
            if (chunkId == "COMM" && payloadOffset + 18 <= bytes.size) {
                channels = buf.getShort(payloadOffset).toInt() and 0xFFFF
                numSampleFrames = buf.getInt(payloadOffset + 2).coerceAtLeast(0)
                bitsPerSample = buf.getShort(payloadOffset + 6).toInt() and 0xFFFF
                sampleRate = decodeIeee80SampleRate(bytes, payloadOffset + 8).coerceIn(8000, 192000)
            } else if (chunkId == "SSND" && payloadOffset + 8 <= bytes.size) {
                val ssndOffset = buf.getInt(payloadOffset).coerceAtLeast(0)
                ssndStart = payloadOffset + 8 + ssndOffset
                break
            }
            offset = payloadOffset + chunkSize + (chunkSize and 1)
        }

        if (ssndStart < 0 || numSampleFrames <= 0 || channels <= 0) return null
        val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
        val maxAvailableFrames = max(0, (bytes.size - ssndStart) / (bytesPerSample * channels))
        val frames = min(numSampleFrames, maxAvailableFrames)
        if (frames <= 0) return null

        val left = FloatArray(frames)
        val right = FloatArray(frames)
        var pos = ssndStart

        for (i in 0 until frames) {
            var l = 0f
            var r = 0f
            for (ch in 0 until channels) {
                val sample = when {
                    bitsPerSample == 16 && pos + 2 <= bytes.size -> {
                        buf.getShort(pos) / 32768f
                    }
                    bitsPerSample == 24 && pos + 3 <= bytes.size -> {
                        val b0 = bytes[pos].toInt()
                        val b1 = bytes[pos + 1].toInt() and 0xFF
                        val b2 = bytes[pos + 2].toInt() and 0xFF
                        val raw = (b0 shl 16) or (b1 shl 8) or b2
                        raw / 8388608f
                    }
                    bitsPerSample == 8 && pos < bytes.size -> {
                        bytes[pos].toInt() / 128f
                    }
                    else -> 0f
                }
                if (ch == 0) l = sample
                if (ch == 1) r = sample
                pos += bytesPerSample
            }
            left[i] = l
            right[i] = if (channels == 1) l else r
        }

        return DecodedAudio(left, right, sampleRate, "AIFF ${bitsPerSample}b")
    }

    private fun decodeIeee80SampleRate(bytes: ByteArray, offset: Int): Int {
        if (offset + 10 > bytes.size) return 44100
        val expon = ((bytes[offset].toInt() and 0x7F) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
        var hiMant = 0L
        for (i in 2..5) {
            hiMant = (hiMant shl 8) or (bytes[offset + i].toLong() and 0xFFL)
        }
        val shift = expon - 16383 - 31
        return if (shift >= 0) {
            (hiMant shl shift.coerceAtMost(16)).toInt()
        } else {
            (hiMant ushr (-shift).coerceAtMost(31)).toInt()
        }.takeIf { it in 8000..192000 } ?: 44100
    }

    fun encodeWavBytes(
        left: FloatArray,
        right: FloatArray,
        sampleRate: Int,
        float32: Boolean = false
    ): ByteArray {
        val out = ByteArrayOutputStream()
        writeWavStream(out, left, right, sampleRate, float32)
        return out.toByteArray()
    }

    fun writeWavStream(
        outputStream: OutputStream,
        left: FloatArray,
        right: FloatArray,
        sampleRate: Int,
        float32: Boolean = false
    ) {
        val frames = min(left.size, right.size)
        val channels = 2
        val bitsPerSample = if (float32) 32 else 16
        val audioFormat = if (float32) 3 else 1
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val dataSize = frames * blockAlign
        val totalSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(audioFormat.toShort())
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)
        outputStream.write(header.array())

        val chunkBuf = ByteBuffer.allocate(min(4096, frames) * blockAlign).order(ByteOrder.LITTLE_ENDIAN)
        var i = 0
        while (i < frames) {
            chunkBuf.clear()
            while (i < frames && chunkBuf.remaining() >= blockAlign) {
                val l = left[i].coerceIn(-1f, 1f)
                val r = right[i].coerceIn(-1f, 1f)
                if (float32) {
                    chunkBuf.putFloat(l)
                    chunkBuf.putFloat(r)
                } else {
                    chunkBuf.putShort((l * 32767f).toInt().toShort())
                    chunkBuf.putShort((r * 32767f).toInt().toShort())
                }
                i++
            }
            outputStream.write(chunkBuf.array(), 0, chunkBuf.position())
        }
        outputStream.flush()
    }
}
