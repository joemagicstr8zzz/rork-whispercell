package com.rork.whispercell.models

/** Small recorded audio segment used for chunk transcription. */
data class RecordedAudioChunk(
    val sequence: Int,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val sampleRateHz: Int,
    val wavBytes: ByteArray,
    val rms: Float
) {
    val durationMillis: Long = endedAtMillis - startedAtMillis
}
