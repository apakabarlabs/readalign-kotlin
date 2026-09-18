package fm.apakabar.readalign

import kotlin.math.max

/** A word a recogniser returned, and where in the recording it heard it. */
data class RecognizedWord(
    val text: String,
    val start: Double,
    val end: Double,
)

/** When one word of the text was spoken. */
data class WordSpan(
    val start: Double,
    val end: Double,
)

/**
 * Lines a recogniser's output up with the text that was read, and says when each word of
 * that text was spoken.
 *
 * Not transcription: the words are known in advance, and the recogniser is only asked
 * where they are. It gets some of them wrong, the more so the further the text is from
 * what it was trained on, so words are paired by how alike they look on paper, and a word
 * left unpaired has its time taken from the words around it.
 */
object TranscriptAligner {
    val matchThreshold: Double get() = Rules.shared.matchThreshold

    /**
     * One span per written word, in reading order.
     *
     * A recording nothing was heard in comes back empty rather than with a span per word,
     * because every span would be a guess dressed as a measurement.
     */
    fun align(
        expected: List<String>,
        heard: List<RecognizedWord>,
        duration: Double,
        weighting: SpeechWeighting = EnglishSyllableWeighting(),
        equivalent: ((String, String, String?) -> Boolean)? = null,
    ): List<WordSpan> {
        if (expected.isEmpty() || heard.isEmpty()) return emptyList()
        val pairs = match(expected, heard, weighting, equivalent)
        return fill(expected, pairs, duration, weighting)
    }

    /** Which written word came back as which heard word. */
    fun pair(
        expected: List<String>,
        heard: List<String>,
        threshold: Double,
        equivalent: ((String, String, String?) -> Boolean)? = null,
    ): List<WordMatch> {
        if (expected.isEmpty() || heard.isEmpty()) return emptyList()
        val alignment =
            Alignment(
                expected = expected.map(::normalize),
                heard = heard.map(::normalize),
                threshold = threshold,
                equivalent = equivalent,
                printedParts = expected.map(::printedParts),
            )
        return alignment.matches(alignment.scores())
    }

    internal fun match(
        expected: List<String>,
        heard: List<RecognizedWord>,
        weighting: SpeechWeighting = EnglishSyllableWeighting(),
        equivalent: ((String, String, String?) -> Boolean)? = null,
    ): Map<Int, RecognizedWord> {
        val placed = mutableMapOf<Int, RecognizedWord>()
        for (match in pair(expected, heard.map { it.text }, matchThreshold, equivalent)) {
            val start = heard[match.heard.first].start
            val end = heard[match.heard.last].end
            val said = heard[match.heard.first].text
            if (match.expected.count() == 1) {
                placed[match.expected.first] = RecognizedWord(said, start, end)
                continue
            }
            val weights = match.expected.map { speechWeight(expected[it], weighting) }
            val total = weights.sum()
            var cursor = start
            for ((index, weight) in match.expected.zip(weights)) {
                val length = (end - start) * weight / total
                placed[index] = RecognizedWord(said, cursor, cursor + length)
                cursor += length
            }
        }
        return placed
    }

    internal fun fill(
        expected: List<String>,
        pairs: Map<Int, RecognizedWord>,
        duration: Double,
        weighting: SpeechWeighting = EnglishSyllableWeighting(),
    ): List<WordSpan> {
        val timings = arrayOfNulls<WordSpan>(expected.size)
        for ((index, word) in pairs) timings[index] = WordSpan(word.start, word.end)

        var index = 0
        while (index < timings.size) {
            if (timings[index] != null) {
                index += 1
                continue
            }
            var runEnd = index
            while (runEnd < timings.size && timings[runEnd] == null) runEnd += 1
            var first = index
            var last = runEnd
            var runStart = if (index > 0) timings[index - 1]?.end ?: 0.0 else 0.0
            var runFinish = if (runEnd < timings.size) timings[runEnd]?.start ?: duration else duration

            if (runFinish - runStart < Rules.shared.roomEnough * (runEnd - index)) {
                val host = swallower(index until runEnd, timings, expected, weighting)
                val stretch = host?.let { timings[it] }
                if (host != null && stretch != null) {
                    if (host < index) {
                        first = host
                        runStart = stretch.start
                    } else {
                        last = host + 1
                        runFinish = stretch.end
                    }
                }
            }

            val weights = (first until last).map { speechWeight(expected[it], weighting) }
            val total = weights.sum()
            var cursor = runStart
            for ((offset, weight) in (first until last).zip(weights)) {
                val length = max(runFinish - runStart, 0.0) * weight / total
                timings[offset] = WordSpan(cursor, cursor + length)
                cursor += length
            }
            index = runEnd
        }
        return timings.filterNotNull()
    }

    private fun swallower(
        run: IntRange,
        timings: Array<WordSpan?>,
        expected: List<String>,
        weighting: SpeechWeighting,
    ): Int? {
        fun perSyllable(index: Int): Double? {
            val timing = timings.getOrNull(index) ?: return null
            return (timing.end - timing.start) / speechWeight(expected[index], weighting)
        }
        val before = perSyllable(run.first - 1)
        val after = perSyllable(run.last + 1)
        return when {
            before != null && after != null -> if (after >= before) run.last + 1 else run.first - 1
            before != null -> run.first - 1
            after != null -> run.last + 1
            else -> null
        }
    }

    private fun speechWeight(
        word: String,
        weighting: SpeechWeighting,
    ): Double {
        val weight = weighting.weight(word)
        val lightest = Rules.shared.lightestWord
        return if (weight.isFinite()) max(weight, lightest) else lightest
    }
}
