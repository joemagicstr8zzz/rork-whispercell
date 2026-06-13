package com.rork.whispercell.models

enum class StartMode(val label: String) {
    Off("Off"),
    StartPhraseRequired("Start phrase required"),
    AlwaysCapturingAfterSessionStarts("Always capturing after session starts"),
    ManualStartOnly("Manual start only")
}

enum class StopMode(val label: String) {
    ManualOnly("Manual only"),
    StopPhraseEndsCapture("Stop phrase ends capture"),
    StopPhraseProcessesTranscript("Stop phrase ends capture and processes transcript"),
    StopPhrasePublishesAutomatically("Stop phrase ends capture and publishes automatically"),
    RequiredFieldsComplete("Required fields complete"),
    MaximumCaptureTime("Maximum capture time")
}

enum class RoutingBehavior(val label: String) {
    FirstValidMatch("Send first valid match"),
    BestConfidenceMatch("Send best confidence match"),
    AllActiveChannelOutputs("Send all active channel outputs")
}
