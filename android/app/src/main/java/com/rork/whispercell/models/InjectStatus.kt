package com.rork.whispercell.models

enum class InjectStatus(val label: String) {
    NotConfigured("Not configured"),
    Ready("Ready"),
    Testing("Testing"),
    Connected("Connected"),
    Publishing("Publishing"),
    Published("Published"),
    Error("Error"),
    RetryAvailable("Retry available")
}
