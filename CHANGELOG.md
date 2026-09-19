# Changelog

## 0.13.1

No behavioural changes. This keeps the three ports on the same release number while
the Python package corrects its reported distribution version.

## 0.13.0

### Added
- A work-level equivalence may join up to six printed or recognised words. Longer joins are impossible unless the caller explicitly vouches for the complete normalised phrase; similarity alone keeps the old two-word limit.

## 0.12.2

No runtime behaviour changes.

### Changed
- The documented JitPack coordinate now names the current release.
- CI runs the complete Makefile build, including generated documentation and comment policy checks.
- CI uses the current Node 24-based Gradle setup action.
- Releases are created by a manually dispatched GitHub Actions workflow after the full build passes.

## 0.12.1

### Fixed
- The count in the note about seams that share no ground. It said ten readings of 154 were cut that way and every one of them gained a word; counted rather than guessed, 57 seams of 631 meet edge to edge, in 51 of the readings, and ten of those visibly gain a word. And the floor that was measured lowered every seam under it, 223 of the 631, rather than only the 57 that need it, so what is disproved is that floor and not the idea.

## 0.12.0

`least_overlap`, which 0.11.0 added, is gone again. It was measured and cost more than it saved; use 0.10.0 or this, not 0.11.0.

### Removed
- `least_overlap` from `rules.yaml` and `Rules`, and the floor it put under the overlap between two pieces. Two pieces can again meet edge to edge where no pause offers itself to begin the next at.

  What the floor was for is real: pieces that meet exactly share no ground, the join has nothing to settle them by, and a word invented at the edge of one of them stands. Of 631 seams over 154 readings, 57 are cut that way, in 51 of the readings, and ten of those visibly gain a word on the side that pads its input to a fixed length.

  What it cost is larger, and the floor measured was the wrong one: it lowered the start of every seam under it, 223 of the 631, to mend 57. Moving the start of a piece changes the length of what the model is asked, and this model answers a different length with different words: between two of the three sides, 222 differences with no floor, 225 at two seconds, 248 at half a second. A floor that applies only where the overlap is nothing has not been measured.

## 0.11.0

`Pieces.cuts` never hands back two pieces that meet edge to edge. Nothing you call changes; where the pieces fall does. **Measured worse than 0.10.0 and undone in 0.12.0.**

### Changed
- The next piece begins one pause earlier than the last ended, as before, and now never less than `least_overlap` earlier. Where no pause offered itself the two pieces met exactly, shared no ground at all, and the join had nothing to settle them by — a recogniser invents a word while it is hearing the last of what it was given, and with nothing in common there was nothing to catch it against. Measured over 154 readings, ten of them were cut that way and every one of those gained a word on the side that pads its input to a fixed length; with the floor the invented word is gone from all ten and two of the readings come back word for word alike with the other side.
- Beginning the next piece earlier can leave more than a piece still to ask, and then there is one more piece than there would have been. The last of them is mostly ground already covered, which the join takes off.

## 0.10.0

Nothing you call has to change. If you turn a recogniser's tokens into words yourself, that is now a call.

### Added
- `spoken` gathers a recogniser's tokens into the words they spell. A recogniser answers in tokens, not in words — `be`, `aut`, `y's` — and where one word ends is the model's convention rather than the caller's. Written out by each side, one returns `beauty's` where another returns `beautys` and a third splits the word in two, and the three readings cannot be held against each other however alike they heard the sound. A token opening with the sentencepiece mark, a space or `|` opens a word; each word is timed from the token it opens with to the one it closes with; marks at either end of a word are the model's punctuation and are left off, while marks inside it stay.

## 0.9.0

`Pieces.joined` drops the second copy of a seam word by the text alone. Nothing you call changes; what comes back does, and `same_moment` is gone from `rules.yaml`.

### Changed
- The overlap is settled by the longest run the two pieces say alike inside it: everything the coming piece says up to the end of that run comes off. Before, a word was a second copy if it was marked within `same_moment` of one already kept and spelled the same. The clock is the one thing two builds of one model do not share: measured on a sonnet, the same word came back 0.20 s apart in two pieces of one recording, just outside the bar, and a different runtime puts it somewhere else again. So one build kept the word twice and another kept it once, from the same recording and the same cut. The words agree where the marks do not.
- The run is looked for anywhere inside the overlap rather than at its edges, because a recogniser drops or invents a word at the edge of what it was given: one piece ended `...by time decease we` where the other heard no `we` at all, and a run pinned to the edges finds nothing and leaves the whole overlap said twice. A run of a single word counts only when it is the whole of what the coming piece says in the overlap.
- Past the run the coming piece is believed and the piece before it is not: they cover the same seconds there, and the one that goes on past them heard them with what follows while the other was hearing the last of what it was given. So a word invented at the edge of a piece now goes rather than standing in the reading.
- A word the reading genuinely says twice at a seam is still kept twice, and pieces that disagree about the overlap still keep what each of them said.

### Removed
- `same_moment` from `rules.yaml` and `Rules`. Nothing reads it now, and a number left behind reads as one the code obeys.

## 0.8.0

Nothing you call has to change. If you hand a piece of a recording to a speech recogniser, there is a new call to hand it over through.

### Added
- `Pieces.heard` asks the recogniser through you, and asks again with less of the tail while nothing comes back. Parakeet answers some pieces of ordinary speech with no words at all, and whether it does turns on where the piece starts and how long it is together rather than on the speech in it: the mel statistics are taken over the piece, so its length moves them, and past some edge the decoder predicts blank at every frame. The lengths to take off and the order to try them are `ask_again_trims` in `rules.yaml`, and `shortest_worth_asking_again` is the length below which nothing heard is simply an answer. What comes back this way is missing whatever was said in the trimmed tail, which the overlap with the next piece covers.

## 0.7.0

Nothing you call has to change. If you cut a recording with `Pieces.cuts` and stitched the answers back together yourself, that part is now a call.

### Added
- `Pieces.joined` turns what each piece came back with into one reading, placed in the seconds of the whole recording. The pieces overlap, so a word at a seam arrives twice, and the second copy goes by time and text together, on `same_moment` in `rules.yaml`: how close two marks of one word have to be for the overlap to have said it once. Left to each caller, this is where two sides that cut a reading identically still end up with different transcripts.
- A piece the recogniser had nothing to say about adds nothing, which is an answer rather than a fault. A transcript missing for a piece, or one too many, raises `UnevenPiecesException` rather than being paired off until the shorter of the two runs out: every word after the missing one would be placed at the wrong moment, and the reading would come back looking whole.

## 0.6.0

Nothing you call has to change. There is a new call for anyone who hands a long recording to a speech recogniser.

### Added
- `Pieces.cuts` says where to cut a recording into the pieces a recogniser is asked one at a time, and `Pieces.pauses` says where it is quiet long enough to cut. A recogniser handed a long reading cuts it into windows of its own, and every runtime cuts differently: one took fifteen-second windows and dropped the last line of a forty-second reading, while another was handed the same reading whole. Cut it here first and every side is asked the same question.
- Four numbers in `rules.yaml` decide it: how long a piece may be, the earliest it may end, how long quiet has to last to count as a pause, and how close two marks of one word have to be for the overlap to say it once.

### Notes
- A reading with hardly any silence in it is cut on length alone. Quiet is measured against the quietest tenth of the recording itself, so where that tenth is already speech, no pause stands out from it.

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
