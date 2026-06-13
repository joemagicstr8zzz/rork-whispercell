package com.rork.whispercell.models

/** Multi-field payload used by confabulation routines. */
data class ConfabulationPayload(
    val place: String? = null,
    val person: String? = null,
    val date: String? = null,
    val objectValue: String? = null,
    val song: String? = null,
    val artist: String? = null,
    val number: String? = null,
    val color: String? = null,
    val summary: String
)
