package fm.apakabar.readalign

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * The pieces of text a reader sees as one character each.
 *
 * Cut by the rules written below rather than by whatever the platform carries, because
 * every platform carries a different answer and a different vintage of it: one cuts a
 * zero-width joiner away from the word it joins, another breaks a joined pair of
 * consonants in two. Sharing the rules is what keeps the ports reading one word.
 */
internal fun clusters(word: String): List<String> {
    val found = mutableListOf<String>()
    val letter = StringBuilder()
    var index = 0
    while (index < word.length) {
        val code = word.codePointAt(index)
        if (letter.isNotEmpty() && !joinsOn(code, letter)) {
            found.add(letter.toString())
            letter.setLength(0)
        }
        letter.appendCodePoint(code)
        index += Character.charCount(code)
    }
    if (letter.isNotEmpty()) found.add(letter.toString())
    return found
}

/** A mark written above, below or beside a letter, which belongs to that letter. */
private fun isMark(code: Int): Boolean =
    when (Character.getType(code)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        -> true

        else -> false
    }

/** Whether this belongs to the letter being read rather than starting the next one. */
private fun joinsOn(
    code: Int,
    letter: StringBuilder,
): Boolean {
    if (isMark(code) || code == ZERO_WIDTH_NON_JOINER || code == ZERO_WIDTH_JOINER) return true
    val last = letter.codePointBefore(letter.length)
    return Rules.shared.joins(last) && Character.isLetter(code)
}

/** Written inside a word to keep two letters from joining up, or to make them. */
private const val ZERO_WIDTH_NON_JOINER = 0x200C
private const val ZERO_WIDTH_JOINER = 0x200D

/** A letter, or a number written as letters are: a roman numeral is read aloud as a word. */
private fun isLetter(cluster: String): Boolean {
    val first = cluster.codePointAt(0)
    return Character.isLetter(first) || Character.getType(first) == Character.LETTER_NUMBER.toInt()
}

/**
 * The letters of a word as a reader sees them, lowercased and brought to one spelling.
 *
 * A letter and the mark above it are one letter here, however the text spells them, and
 * counting the pieces instead would answer differently on whole writing systems: two
 * Devanagari words three edits apart out of six pass a bar that two out of three does not.
 */
internal fun letters(word: String): List<String> = clusters(Normalizer.normalize(word, Normalizer.Form.NFC).lowercase()).filter(::isLetter)

/** What a recogniser drops or invents, taken off both sides before they are compared. */
fun normalize(word: String): String = letters(word).joinToString("")

/**
 * How many words print writes this word as, which is the ceiling on how many heard words
 * it may be spread over. Marks at the edges and doubled marks are not parts.
 */
internal fun printedParts(word: String): Int = max(word.split("-").count { it.isNotEmpty() }, 1)

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
        .map { Rules.shared.foldedLetters[it.toString()] ?: it.toString() }
        .joinToString("")
}

/** One for the same word, zero for nothing in common. */
internal fun similarity(
    left: String,
    right: String,
): Double {
    val written = fold(left)
    val said = fold(right)
    if (written == said) return 1.0
    if (written.isEmpty() || said.isEmpty()) return 0.0
    val writtenLetters = clusters(written)
    val saidLetters = clusters(said)
    val distance = editDistance(writtenLetters, saidLetters)
    return 1 - distance.toDouble() / max(writtenLetters.size, saidLetters.size)
}

/** How many letters have to change to turn one word into the other. */
internal fun editDistance(
    left: List<String>,
    right: List<String>,
): Int {
    var previous = IntArray(right.size + 1) { it }
    for (row in 1..left.size) {
        val current = IntArray(right.size + 1)
        current[0] = row
        for (column in 1..right.size) {
            val substitution = previous[column - 1] + if (left[row - 1] == right[column - 1]) 0 else 1
            current[column] = min(min(previous[column] + 1, current[column - 1] + 1), substitution)
        }
        previous = current
    }
    return previous[right.size]
}
