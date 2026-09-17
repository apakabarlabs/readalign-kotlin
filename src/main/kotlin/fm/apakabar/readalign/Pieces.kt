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
}
