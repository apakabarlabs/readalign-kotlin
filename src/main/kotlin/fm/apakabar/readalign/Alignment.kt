package fm.apakabar.readalign

import kotlin.math.max

/** One written word lined up with one heard word, or with the two that stood in for it. */
data class WordMatch(
    val expected: IntRange,
    val heard: IntRange,
)

/**
 * The table that decides which written word came back as which heard word.
 *
 * Both sides are walked at once, and a step may take one word from each, join two heard
 * words into one written word, split one heard word across two written ones, or pass a
 * word over. A gap costs less than a bad pairing, so a false start is stepped over rather
 * than pushed into a neighbour.
 */
internal class Alignment(
    private val expected: List<String>,
    private val heard: List<String>,
    private val threshold: Double,
    private val equivalent: ((String, String, String?) -> Boolean)?,
    private val printedParts: List<Int>,
) {
    private val gapPenalty = Rules.shared.gapPenalty
    private val mismatchPenalty = Rules.shared.mismatchPenalty

    private val joinThreshold: Double get() = max(threshold, Rules.shared.joinFloor)

    private fun joinable(words: List<String>): Boolean = words.all { it.isNotEmpty() }

    private fun before(row: Int): String? = if (row > 0) expected[row - 1] else null

    fun straight(
        row: Int,
        column: Int,
    ): Double {
        if (equivalent?.invoke(expected[row], heard[column], before(row)) == true) return 1.0
        return worth(similarity(expected[row], heard[column]), threshold)
    }

    fun joinedHeard(
        row: Int,
        column: Int,
        span: Int,
    ): Double {
        val parts = heard.subList(column - span + 1, column + 1)
        if (!joinable(parts) || expected[row].isEmpty()) return mismatchPenalty
        val joined = parts.joinToString("")
        if (equivalent?.invoke(expected[row], joined, before(row)) == true) return 1.0
        return worth(similarity(expected[row], joined), joinThreshold)
    }

    fun spansForExpectedAt(row: Int): Int = max(Rules.shared.joinSpan, printedParts[row])

    fun joinedExpected(
        row: Int,
        column: Int,
    ): Double {
        if (!joinable(expected.subList(row - 1, row + 1)) || heard[column].isEmpty()) return mismatchPenalty
        val joined = expected[row - 1] + expected[row]
        if (equivalent?.invoke(joined, heard[column], if (row > 1) expected[row - PAIR] else null) == true) {
            return 1.0
        }
        return worth(similarity(joined, heard[column]), joinThreshold)
    }

    fun joinedPair(
        row: Int,
        column: Int,
    ): Double {
        if (!joinable(expected.subList(row - 1, row + 1)) || !joinable(heard.subList(column - 1, column + 1))) {
            return mismatchPenalty
        }
        val written = expected[row - 1] + expected[row]
        val said = heard[column - 1] + heard[column]
        if (equivalent?.invoke(written, said, if (row > 1) expected[row - PAIR] else null) == true) return 1.0
        return worth(similarity(written, said), joinThreshold)
    }

    private fun worth(
        similarity: Double,
        bar: Double,
    ): Double = if (similarity >= bar) similarity else mismatchPenalty

    fun scores(): Array<DoubleArray> {
        val score = Array(expected.size + 1) { DoubleArray(heard.size + 1) }
        for (row in 1..expected.size) score[row][0] = row * gapPenalty
        for (column in 1..heard.size) score[0][column] = column * gapPenalty
        for (row in 1..expected.size) {
            for (column in 1..heard.size) {
                var best = score[row - 1][column - 1] + straight(row - 1, column - 1)
                best = max(best, score[row - 1][column] + gapPenalty)
                best = max(best, score[row][column - 1] + gapPenalty)
                for (span in PAIR..spansForExpectedAt(row - 1)) {
                    if (column >= span) {
                        best = max(best, score[row - 1][column - span] + joinedHeard(row - 1, column - 1, span))
                    }
                }
                if (row >= PAIR) {
                    best = max(best, score[row - PAIR][column - 1] + joinedExpected(row - 1, column - 1))
                }
                if (row >= PAIR && column >= PAIR) {
                    best = max(best, score[row - PAIR][column - PAIR] + joinedPair(row - 1, column - 1))
                }
                score[row][column] = best
            }
        }
        return score
    }

    fun matches(score: Array<DoubleArray>): List<WordMatch> {
        val matches = mutableListOf<WordMatch>()
        var row = expected.size
        var column = heard.size
        while (row > 0 && column > 0) {
            val cell = score[row][column]
            val straight = straight(row - 1, column - 1)
            val span =
                if (cell == score[row - 1][column - 1] + straight) {
                    null
                } else {
                    (PAIR..spansForExpectedAt(row - 1)).firstOrNull { span ->
                        column >= span &&
                            cell == score[row - 1][column - span] + joinedHeard(row - 1, column - 1, span)
                    }
                }
            when {
                cell == score[row - 1][column - 1] + straight -> {
                    if (straight >= threshold) {
                        matches.add(WordMatch(row - 1 until row, column - 1 until column))
                    }
                    row -= 1
                    column -= 1
                }
                span != null -> {
                    if (joinedHeard(row - 1, column - 1, span) >= joinThreshold) {
                        matches.add(WordMatch(row - 1 until row, column - span until column))
                    }
                    row -= 1
                    column -= span
                }
                row >= PAIR &&
                    column >= PAIR &&
                    cell == score[row - PAIR][column - PAIR] + joinedPair(row - 1, column - 1) -> {
                    if (joinedPair(row - 1, column - 1) >= joinThreshold) {
                        matches.add(WordMatch(row - PAIR until row, column - PAIR until column))
                    }
                    row -= PAIR
                    column -= PAIR
                }
                row >= PAIR && cell == score[row - PAIR][column - 1] + joinedExpected(row - 1, column - 1) -> {
                    if (joinedExpected(row - 1, column - 1) >= joinThreshold) {
                        matches.add(WordMatch(row - PAIR until row, column - 1 until column))
                    }
                    row -= PAIR
                    column -= 1
                }
                cell == score[row - 1][column] + gapPenalty -> row -= 1
                else -> column -= 1
            }
        }
        return matches.reversed()
    }

    companion object {
        /**
         * Two rows or columns of the table, which is what a join looks back at. Not a
         * tuned value: a pair is two by arithmetic. How far a join may reach on the heard
         * side is `join_span` in the rules.
         */
        const val PAIR = 2
    }
}
