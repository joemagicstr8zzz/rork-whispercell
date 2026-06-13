package com.rork.whispercell.models

/** Best category-level values found after extraction. */
data class BestMatchSummary(
    val word: String? = null,
    val phrase: String? = null,
    val name: String? = null,
    val date: String? = null,
    val birthday: String? = null,
    val place: String? = null,
    val country: String? = null,
    val city: String? = null,
    val card: String? = null,
    val number: String? = null,
    val serial: String? = null,
    val song: String? = null,
    val artist: String? = null,
    val lyric: String? = null,
    val color: String? = null,
    val objectValue: String? = null,
    val zodiac: String? = null,
    val time: String? = null,
    val fullConfabulation: String? = null
)
