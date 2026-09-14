package fm.apakabar.readalign

import java.text.BreakIterator
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * The letters of a word as a reader sees them.
 *
 * A letter and the mark above it are one letter here, however the text spells them, and
 * counting the pieces instead would answer differently on whole writing systems: two
 * Devanagari words three edits apart out of six pass a bar that two out of three does not.
 */
fun letters(word: String): List<String> {
    val found = mutableListOf<String>()
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(word)
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        found.add(word.substring(start, end))
        start = end
        end = iterator.next()
    }
    return found
}

/**
 * What a recogniser drops or invents, taken off both sides before they are compared.
 *
 * A letter is anything Unicode calls alphabetic, which is wider than the letter
 * categories: a roman numeral is read aloud as a word and counts as one.
 */
fun normalize(word: String): String {
    val kept =
        letters(word.lowercase())
            .filter { it.isNotEmpty() && Character.isAlphabetic(it.codePointAt(0)) }
            .joinToString("")
    // Brought to one spelling, because the same word typed one way and pasted another is
    // two different strings here. Swift compares its strings by canonical equivalence and
    // needs no such line, which is exactly why a port that leaves it out passes its own
    // tests and disagrees with its sibling on a word carrying a mark.
    return Normalizer.normalize(kept, Normalizer.Form.NFC)
}

/**
 * How many words print writes this word as, which is the ceiling on how many heard words
 * it may be spread over. Marks at the edges and doubled marks are not parts.
 */
fun printedParts(word: String): Int = max(word.split("-").count { it.isNotEmpty() }, 1)

/**
 * The word with its marks taken off, so that likeness can be measured without them.
 *
 * Which marks come off is named in the rules rather than left to whatever this language
 * calls a diacritic: those tables disagree, and a port would then answer differently from
 * its sibling on whole writing systems.
 */
fun fold(word: String): String {
    val decomposed = Normalizer.normalize(word, Normalizer.Form.NFD)
    val lifted = decomposed.filterNot { Rules.shared.lifts(it.code) }
    return Normalizer
        .normalize(lifted, Normalizer.Form.NFC)
        .map { Rules.shared.foldedLetters[it.toString()]?.firstOrNull() ?: it }
        .joinToString("")
}

/** One for the same word, zero for nothing in common. */
fun similarity(
    left: String,
    right: String,
): Double {
    val written = fold(left)
    val said = fold(right)
    if (written == said) return 1.0
    if (written.isEmpty() || said.isEmpty()) return 0.0
    val writtenLetters = letters(written)
    val saidLetters = letters(said)
    val distance = editDistance(writtenLetters, saidLetters)
    return 1.0 - distance.toDouble() / max(writtenLetters.size, saidLetters.size)
}

private fun editDistance(
    left: List<String>,
    right: List<String>,
): Int {
    var previous = IntArray(right.size + 1) { it }
    var current = IntArray(right.size + 1)
    for (row in 1..left.size) {
        current[0] = row
        for (column in 1..right.size) {
            val substitution = previous[column - 1] + if (left[row - 1] == right[column - 1]) 0 else 1
            current[column] = min(substitution, min(previous[column] + 1, current[column - 1] + 1))
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[right.size]
}
