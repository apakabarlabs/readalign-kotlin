# Changelog

## 0.5.0

Nothing you call has to change, and the library stops being fourteen megabytes heavier than it needs to be.

### Changed
- Where a word is cut into letters is now decided by the rules this library shares rather than by any table the platform carries. The marks that join one consonant to the next are named in `rules.yaml`, and the letters of eleven writing systems are pinned letter by letter in the cases.

### Removed
- `com.ibm.icu:icu4j`. It was brought in because neither `java.text.BreakIterator` nor the `\X` of `java.util.regex` cuts a word the way the sibling ports do, and each is wrong in a different place. Writing the cut down and sharing it answers that without the fourteen megabytes, which on a phone were paid for a table the operating system already has.

## 0.4.0

First release of this library in Kotlin. Nothing to migrate from.

It answers the same question, and to the same cases, as the Swift and Python libraries: all three read one set of tuned numbers and one set of cases, so they cannot quietly come to disagree. The version starts at 0.4.0 rather than 0.1.0 because those numbers and cases are shared, and a shared change moves every library to the same version in the same release.

### Added

Three calls, for the case where you already hold the text that was read aloud.

- `TranscriptAligner.align` takes the words of that text, the words a speech recogniser returned with the start and end it heard each at, and the length of the recording. It returns one start and one end per word of your text, in seconds, in reading order. A recording nothing was heard in returns nothing rather than a span per word.
- `TranscriptAligner.pair` answers the same question without times: which written word came back as which heard word. Its `threshold` is how alike two words must be to count as the same word; its optional `equivalent` lets you vouch for a pair a particular recogniser always gets wrong, such as `heir` heard as `air`.
- `SilenceHold.held` is the one call that needs the recording. A recogniser marks where a word stops being audible, not where the voice has finished with it, so a passage played to the mark stops a hair short of itself; this carries each mark into the quiet behind it, and never into the word that follows.

Plus `EnglishSyllableWeighting` and `EvenWeighting`, which decide how the recording is shared out among words the recogniser did not return at all, so that a long word does not get the same slice of a pause as `a`.

The alignment tolerates what recognisers do to a text they were not given: a misspelt word keeps its own time, a word written without its diacritics is still the word, a word boundary put in the wrong place is matched across both words at once, a hyphenated compound heard as three words is timed across all three, two written words heard as one share the stretch between them, and a word nobody said is passed over instead of being pushed onto a written word.

### Notes for a reader coming from another port

Two differences are the language's, not the library's.

- Comparing two spellings brings them to one Unicode spelling first. Swift compares its strings by canonical equivalence and needs no such step; Kotlin compares them by code unit, so a port leaving it out would pass its own tests and disagree with its siblings on any word carrying a mark.
- Words are counted in the letters a reader sees rather than in the units the JVM stores a string in, so `İstanbul` does not gain a syllable and `Ångström` does not lose its ring. Where those letters are cut apart is decided by rules this library shares with its siblings rather than by anything the JVM carries: `java.text.BreakIterator` cuts a zero-width joiner away from the word it joins, and the `\X` of `java.util.regex` breaks a Devanagari conjunct in two, so either would have this library read a Persian or Hindi word as something its siblings do not read it as.
