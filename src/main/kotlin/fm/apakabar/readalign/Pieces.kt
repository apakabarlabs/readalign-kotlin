package fm.apakabar.readalign

/**
 * Cutting a recording into the pieces a recogniser is given one at a time.
 *
 * Every side that listens to the same reading has to cut it the same way. A piece boundary
 * changes what comes back near it, and a runtime left to cut on its own cuts elsewhere: a
 * forty-second reading handed whole to one recogniser and in fifteen-second windows to
 * another is two different questions, and the answers cannot be held against each other.
 * So the cut is made here, by rule, before any of them is asked.
 */
object Pieces {
    /**
     * Where the recording is quiet long enough that a cut there takes no word in half.
     *
     * The middle of each stretch of quiet, in samples. A stop inside a word is quiet too,
     * which is why a pause has a length to reach before it counts as one.
     */
    fun pauses(
        samples: FloatArray,
        sampleRate: Double,
    ): List<Int> {
        val frames = SilenceHold.energyFrames(samples, sampleRate)
        if (frames.isEmpty()) return emptyList()
        val threshold = SilenceHold.speechThreshold(frames)
        if (frames.none { it >= threshold }) return emptyList()

        val eachFrame = Rules.shared.frameSeconds
        val quietEnough = maxOf((Rules.shared.pauseSeconds / eachFrame).toInt(), 1)
        val found = mutableListOf<Int>()
        var quiet = 0
        for ((index, energy) in frames.withIndex()) {
            if (energy < threshold) {
                quiet += 1
                continue
            }
            if (quiet >= quietEnough) {
                found.add(((index - quiet / 2) * eachFrame * sampleRate).toInt())
            }
            quiet = 0
        }
        if (quiet >= quietEnough) {
            found.add(((frames.size - quiet / 2) * eachFrame * sampleRate).toInt())
        }
        return found
    }

    /**
     * The pieces the recording is asked in, in samples, each overlapping the one before.
     *
     * A piece ends at the last pause that leaves it long enough to carry a line and short
     * enough for the runtime to take whole; where no pause falls there, it ends on length
     * alone, because a piece that grows to find a pause is the very window this avoids.
     * The next piece begins one pause earlier than the last ended, so every word is heard
     * whole by at least one of them. Where that would leave no overlap, the next piece gets
     * the standing `edgeOverlap`. A floor that moved every shorter overlap was measured
     * worse.
     */
    fun cuts(
        samples: FloatArray,
        sampleRate: Double,
    ): List<IntRange> {
        val longest = (Rules.shared.pieceSeconds * sampleRate).toInt()
        if (samples.size <= longest || longest <= 0) return listOf(0 until samples.size)
        val shortest = (Rules.shared.pieceSeconds * Rules.shared.shortestPieceShare * sampleRate).toInt()
        val marks = pauses(samples, sampleRate)

        val pieces = mutableListOf<IntRange>()
        var start = 0
        while (samples.size - start > longest) {
            val pause = marks.lastOrNull { it > start + shortest && it < start + longest }
            val cut = pause ?: (start + longest)
            pieces.add(start until cut)
            start =
                marks.lastOrNull { it < cut && it >= start + shortest }
                    ?: maxOf(start, cut - (Rules.shared.edgeOverlap * sampleRate).toInt())
        }
        pieces.add(start until samples.size)
        return pieces
    }

    /**
     * One reading out of what each piece came back with.
     *
     * The pieces overlap, so the words at a seam arrive twice, and the second copy is
     * dropped by the text: the longest run the two pieces say alike inside the overlap is
     * found, everything the coming piece says up to the end of it comes off, and so does
     * whatever the piece before said past it. Placed where it falls in the whole recording,
     * so a caller hands over what it was given piece by piece and gets the reading back.
     *
     * By the text and not by the clock, because the clock is the one thing two builds of one
     * model do not share: the same word decoded in two pieces comes back a fifth of a second
     * apart on one runtime and differently again on the next, so a rule that asks how close
     * two marks are decides differently on each of them. The words agree where the marks do
     * not.
     *
     * A piece the recogniser had nothing to say about is an answer, not a failure: a
     * stretch of silence is transcribed as no words at all. A count of transcripts that
     * does not match the count of pieces is a failure, and is refused rather than quietly
     * paired off until the shorter of the two runs out.
     */
    fun joined(
        heard: List<List<RecognizedWord>>,
        pieces: List<IntRange>,
        sampleRate: Double,
    ): List<RecognizedWord> {
        if (heard.size != pieces.size) throw UnevenPiecesException(heard.size, pieces.size)
        val reading = mutableListOf<RecognizedWord>()
        var coveredTo = 0.0
        for ((words, piece) in heard.zip(pieces)) {
            val offset = piece.first / sampleRate
            val placed =
                words.map { word ->
                    RecognizedWord(
                        text = word.text,
                        start = word.start + offset,
                        end = word.end + offset,
                    )
                }
            val seam = agreement(reading, placed, offset, coveredTo)
            if (seam.insertionAt != null && seam.gapWord != null) {
                reading.add(seam.insertionAt, seam.gapWord)
            }
            repeat(seam.keptAfterIt) { reading.removeAt(reading.size - 1) }
            reading.addAll(placed.drop(seam.comingUpToIt))
            coveredTo = (piece.last + 1) / sampleRate
        }
        return reading
    }

    private data class Seam(
        val keptAfterIt: Int,
        val comingUpToIt: Int,
        val insertionAt: Int? = null,
        val gapWord: RecognizedWord? = null,
    )

    private fun agreement(
        kept: List<RecognizedWord>,
        coming: List<RecognizedWord>,
        overlapFrom: Double,
        coveredTo: Double,
    ): Seam {
        val nothing = Seam(0, 0)
        val tail = kept.dropWhile { it.start < overlapFrom }.map { normalize(it.text) }
        val head = coming.takeWhile { it.start < coveredTo }.map { normalize(it.text) }
        if (tail.isEmpty() || head.isEmpty()) return nothing

        var longest = 0
        var startsInTail = 0
        var startsInHead = 0
        var endsInTail = 0
        var endsInHead = 0
        for (first in tail.indices) {
            for (second in head.indices) {
                var run = 0
                while (first + run < tail.size &&
                    second + run < head.size &&
                    tail[first + run] == head[second + run]
                ) {
                    run++
                }
                if (run > longest) {
                    longest = run
                    startsInTail = first
                    startsInHead = second
                    endsInTail = first + run
                    endsInHead = second + run
                }
            }
        }
        return if (longest > 1 || longest == head.size) {
            val agreementAt = kept.size - tail.size + startsInTail
            val gapText = normalize(coming.first().text)
            val previousText = if (agreementAt > 0) normalize(kept[agreementAt - 1].text) else ""
            val hasOneWordInGap =
                startsInHead == 1 &&
                    agreementAt > 0 &&
                    coming.first().start >= kept[agreementAt - 1].end &&
                    (coming.first().start + coming.first().end) / 2 < kept[agreementAt].start &&
                    gapText.isNotEmpty() &&
                    !previousText.endsWith(gapText) &&
                    !gapText.endsWith(previousText)
            Seam(
                tail.size - endsInTail,
                endsInHead,
                if (hasOneWordInGap) agreementAt else null,
                if (hasOneWordInGap) coming.first() else null,
            )
        } else {
            nothing
        }
    }

    /**
     * What one piece comes back as, recovering an empty or prematurely stopped answer.
     *
     * Parakeet answers some pieces of ordinary speech with no words at all, and whether it
     * does turns on where the piece starts and how long it is together: the mel statistics
     * are taken over the piece, so its length moves them, and past some edge the decoder
     * predicts blank at every frame. Handing over a little less of the tail moves the piece
     * off that edge. Nothing here tells speech from silence, so a piece that is genuinely
     * silent pays for the whole list before answering nothing, which is why a piece shorter
     * than `shortest_worth_asking_again` is not asked again at all.
     *
     * A non-empty answer can also stop before speech resumes later in its audio. That tail
     * is asked again with already recognised context and accepted only when the two answers
     * share enough words to join without a duplicate.
     *
     * An empty answer won by trimming is missing whatever was said in the tail that was cut
     * off. Each piece overlaps the next, and that overlap is what covers it.
     */
    fun heard(
        piece: FloatArray,
        sampleRate: Double,
        asking: (FloatArray) -> List<RecognizedWord>,
    ): List<RecognizedWord> {
        val words = asking(piece)
        if (words.isNotEmpty()) {
            return recoveredTail(words, piece, sampleRate, asking)
        }
        if (piece.size / sampleRate < Rules.shared.shortestWorthAskingAgain) return words

        for (trim in Rules.shared.askAgainTrims) {
            val shorter = piece.size - (trim * sampleRate).toInt()
            if (shorter <= 0) break
            val again = asking(piece.copyOfRange(0, shorter))
            if (again.isNotEmpty()) return again
        }
        return words
    }

    private fun recoveredTail(
        words: List<RecognizedWord>,
        piece: FloatArray,
        sampleRate: Double,
        asking: (FloatArray) -> List<RecognizedWord>,
    ): List<RecognizedWord> {
        val frames = SilenceHold.energyFrames(piece, sampleRate)
        val threshold = SilenceHold.speechThreshold(frames)
        val firstFrame = (words.last().end / Rules.shared.frameSeconds).toInt()
        var wentQuiet = false
        var speechResumed = false
        for (energy in frames.drop(firstFrame)) {
            if (energy < threshold) {
                wentQuiet = true
            } else if (wentQuiet) {
                speechResumed = true
                break
            }
        }
        if (!speechResumed) return words

        val overlapFrom = maxOf(0.0, words.last().start - Rules.shared.partialAnswerOverlap)
        val start = (overlapFrom * sampleRate).toInt()
        val offset = start / sampleRate
        val coming =
            asking(piece.copyOfRange(start, piece.size)).map { word ->
                RecognizedWord(word.text, word.start + offset, word.end + offset)
            }
        var seam = agreement(words, coming, offset, piece.size / sampleRate)
        if (
            seam.comingUpToIt == 0 &&
            coming.isNotEmpty() &&
            normalize(words.last().text) == normalize(coming.first().text)
        ) {
            seam = Seam(keptAfterIt = 0, comingUpToIt = 1)
        }
        if (seam.comingUpToIt == 0) return words
        return words.dropLast(seam.keptAfterIt) + coming.drop(seam.comingUpToIt)
    }
}

class UnevenPiecesException(
    heard: Int,
    pieces: Int,
) : IllegalArgumentException("$heard transcripts for $pieces pieces")
