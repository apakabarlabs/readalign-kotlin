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
        // Quiet is only quiet between speech. Where nothing stands above the threshold
        // there is no voice to pause, and the whole recording would otherwise read as one
        // long pause and offer its own middle as a place to cut.
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
     * whole by at least one of them.
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
            val cut = marks.lastOrNull { it > start + shortest && it < start + longest } ?: (start + longest)
            pieces.add(start until cut)
            // One pause back, but never back past half a piece: the overlap is there to
            // carry the words at the seam, and a pause near the start of this piece would
            // hand the next one almost the same range, over and over.
            start = marks.lastOrNull { it < cut && it >= start + shortest } ?: cut
        }
        pieces.add(start until samples.size)
        return pieces
    }

    /**
     * One reading out of what each piece came back with.
     *
     * The pieces overlap, so the words at a seam arrive twice, and the second copy is
     * dropped by the text: the longest run of words the piece before already said is taken
     * off the front of the one coming. Placed where it falls in the whole recording, so a
     * caller hands over what it was given piece by piece and gets the reading back.
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
            reading.addAll(placed.drop(saidAlready(reading, placed, coveredTo)))
            coveredTo = (piece.last + 1) / sampleRate
        }
        return reading
    }

    /**
     * How many of the coming piece's first words the piece before it has already said.
     *
     * Only the words that fall in the ground both pieces cover can be a second copy, so the
     * search stops where the piece before ended: a word the reading genuinely says twice,
     * further along, is out of reach of this and stays.
     */
    private fun saidAlready(
        kept: List<RecognizedWord>,
        coming: List<RecognizedWord>,
        coveredTo: Double,
    ): Int {
        val reach = minOf(coming.takeWhile { it.start < coveredTo }.size, kept.size)
        var said = 0
        for (length in 1..reach) {
            val alike =
                kept.takeLast(length).zip(coming.take(length)).all { (earlier, later) ->
                    normalize(earlier.text) == normalize(later.text)
                }
            if (alike) said = length
        }
        return said
    }

    /**
     * What one piece comes back as, asking again with less of its tail while nothing comes.
     *
     * Parakeet answers some pieces of ordinary speech with no words at all, and whether it
     * does turns on where the piece starts and how long it is together: the mel statistics
     * are taken over the piece, so its length moves them, and past some edge the decoder
     * predicts blank at every frame. Handing over a little less of the tail moves the piece
     * off that edge. Nothing here tells speech from silence, so a piece that is genuinely
     * silent pays for the whole list before answering nothing, which is why a piece shorter
     * than `shortest_worth_asking_again` is not asked again at all.
     *
     * An answer won this way is missing whatever was said in the tail that was cut off. Each
     * piece the recording is cut into overlaps the next, and that overlap is what covers it.
     */
    fun heard(
        piece: FloatArray,
        sampleRate: Double,
        asking: (FloatArray) -> List<RecognizedWord>,
    ): List<RecognizedWord> {
        val words = asking(piece)
        if (words.isNotEmpty() || piece.size / sampleRate < Rules.shared.shortestWorthAskingAgain) return words

        for (trim in Rules.shared.askAgainTrims) {
            val shorter = piece.size - (trim * sampleRate).toInt()
            if (shorter <= 0) break
            val again = asking(piece.copyOfRange(0, shorter))
            if (again.isNotEmpty()) return again
        }
        return words
    }
}

class UnevenPiecesException(
    heard: Int,
    pieces: Int,
) : IllegalArgumentException("$heard transcripts for $pieces pieces")
