package com.rork.whispercell.services

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavEncoder {
    fun pcm16MonoToWav(pcmBytes: ByteArray, sampleRateHz: Int): ByteArray {
        val byteRate = sampleRateHz * BYTES_PER_SAMPLE
        val dataSize = pcmBytes.size
        val totalSize = 36 + dataSize
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1.toShort())
        buffer.putShort(1.toShort())
        buffer.putInt(sampleRateHz)
        buffer.putInt(byteRate)
        buffer.putShort(BYTES_PER_SAMPLE.toShort())
        buffer.putShort(16.toShort())
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        buffer.put(pcmBytes)
        return buffer.array()
    }

    private const val BYTES_PER_SAMPLE = 2
}
