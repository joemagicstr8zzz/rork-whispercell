package com.rork.whispercell.models

/** Categories WhisperCell can extract from live performance speech. */
enum class DetectedCategory(val label: String) {
    Word("Word"),
    Phrase("Phrase"),
    Name("Name"),
    Date("Date"),
    Birthday("Birthday"),
    Place("Place"),
    Country("Country"),
    City("City"),
    Number("Number"),
    PlayingCard("Playing Card"),
    SerialNumber("Serial Number"),
    Song("Song"),
    Artist("Artist"),
    Lyric("Lyric"),
    Color("Color"),
    Object("Object"),
    Zodiac("Zodiac"),
    Emotion("Emotion"),
    Movie("Movie"),
    Celebrity("Celebrity"),
    Animal("Animal"),
    Time("Time"),
    FullConfabulation("Full Confabulation")
}
