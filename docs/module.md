# ReadAlign for Kotlin

ReadAlign matches a speech recogniser's output against a known text and assigns
timings to the written words. `TranscriptAligner` is the main entry point;
`SilenceHold` carries word endings through the quiet that follows them.
