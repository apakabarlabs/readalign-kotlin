package fm.apakabar.readalign

import com.ibm.icu.lang.UCharacter
import com.ibm.icu.lang.UCharacterCategory
import com.ibm.icu.text.BreakIterator
import com.ibm.icu.util.ULocale
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * The pieces of text a reader sees as one character each.
 *
 * Cut by ICU rather than by anything the JVM carries of its own. `java.text.BreakIterator`
 * and the `\X` of `java.util.regex` each answer by an older definition than the one Unicode
 * now gives, and by different older ones: the first cuts a zero-width joiner away from the
 * word it joins, the second breaks a Devanagari conjunct in two.
 */
internal fun clusters(word: String): List<String> {
    val found = mutableListOf<String>()
    val iterator = BreakIterator.getCharacterInstance(ULocale.ROOT)
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

/** A letter, or a number written as letters are: a roman numeral is read aloud as a word. */
private fun isLetter(cluster: String): Boolean {
    val first = cluster.codePointAt(0)
    return UCharacter.isLetter(first) ||
        UCharacter.getType(first) == UCharacterCategory.LETTER_NUMBER.toInt()
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
