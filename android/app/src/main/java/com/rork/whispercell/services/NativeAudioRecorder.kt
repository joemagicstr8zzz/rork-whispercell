package com.rork.whispercell.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import com.rork.whispercell.models.RecordedAudioChunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/** Continuous native microphone recorder. It records until the app stops it. */
class NativeAudioRecorder(
    private val context: Context,
    private val sampleRateHz: Int = SAMPLE_RATE_HZ,
    private val chunkDurationMillis: Int = CHUNK_DURATION_MS
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var sequence: Int = 0
    private var chunkCallback: suspend (RecordedAudioChunk) -> Unit = {}
    private var errorCallback: (String) -> Unit = {}

    fun onChunk(callback: suspend (RecordedAudioChunk) -> Unit) { chunkCallback = callback }
    fun onError(callback: (String) -> Unit) { errorCallback = callback }

    fun start() {
        if (recordJob?.isActive == true) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            errorCallback("Microphone permission is missing.")
            return
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRateHz)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            errorCallback("Native recorder failed to initialize.")
            return
        }

        audioRecord = recorder
        attachAudioEffects(recorder.audioSessionId)
        sequence = 0
        recorder.startRecording()

        recordJob = scope.launch {
            val readBuffer = ByteArray(minBuffer)
            val chunkBytes = ByteArrayOutputStream()
            var chunkStartedAt = System.currentTimeMillis()
            val targetBytes = ((sampleRateHz * BYTES_PER_SAMPLE) * (chunkDurationMillis / 1000f)).toInt()

            while (isActive) {
                val read = recorder.read(readBuffer, 0, readBuffer.size)
                if (read > 0) {
                    chunkBytes.write(readBuffer, 0, read)
                    if (chunkBytes.size() >= targetBytes) {
                        val endedAt = System.currentTimeMillis()
                        val pcm = chunkBytes.toByteArray()
                        val rms = calculateRms(pcm)
                        chunkBytes.reset()
                        val chunk = RecordedAudioChunk(
                            sequence = sequence++,
                            startedAtMillis = chunkStartedAt,
                            endedAtMillis = endedAt,
                            sampleRateHz = sampleRateHz,
                            wavBytes = WavEncoder.pcm16MonoToWav(pcm, sampleRateHz),
                            rms = rms
                        )
                        chunkStartedAt = endedAt
                        if (rms >= MIN_RMS_FOR_TRANSCRIPTION) chunkCallback(chunk)
                    }
                }
            }
        }
    }

    fun stop() {
        recordJob?.cancel()
        recordJob = null
        runCatching { audioRecord?.stop() }
        releaseAudioEffects()
        audioRecord?.release()
        audioRecord = null
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        noiseSuppressor = if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(audioSessionId)?.apply { enabled = true } else null
        echoCanceler = if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true } else null
    }

    private fun releaseAudioEffects() {
        noiseSuppressor?.release()
        echoCanceler?.release()
        noiseSuppressor = null
        echoCanceler = null
    }

    private fun calculateRms(pcmBytes: ByteArray): Float {
        if (pcmBytes.size < 2) return 0f
        var sum = 0.0
        var count = 0
        var index = 0
        while (index + 1 < pcmBytes.size) {
            val sample = ((pcmBytes[index + 1].toInt() shl 8) or (pcmBytes[index].toInt() and 0xFF)).toShort().toInt()
            sum += sample * sample
            count++
            index += 2
        }
        return if (count == 0) 0f else sqrt(sum / count).toFloat()
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val CHUNK_DURATION_MS = 4_000
        const val BYTES_PER_SAMPLE = 2
        const val MIN_RMS_FOR_TRANSCRIPTION = 120f
    }
}
