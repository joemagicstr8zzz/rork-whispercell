package com.rork.whispercell.models

/** Runtime state for a performer-side WhisperCell session. */
enum class SessionState(val label: String) {
    Idle("Idle"),
    BackgroundReady("Background Ready"),
    WaitingForStartPhrase("Waiting for Start Phrase"),
    Capturing("Capturing"),
    ThinkingPause("Thinking Pause"),
    ProcessingTranscript("Processing"),
    ExtractingData("Extracting"),
    MatchingChannel("Matching Channel"),
    PublishingToInject("Publishing to Inject"),
    Published("Published"),
    Error("Error"),
    Paused("Paused"),
    PanicStopped("Panic Stopped")
}
