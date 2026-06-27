package com.joemagicstr8zzz.screenvault.data

fun MatchGroupCollection.getOrNull(index: Int): MatchGroup? {
    return if (index >= 0 && index < size) this[index] else null
}
