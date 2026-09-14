package fm.apakabar.readalign

import kotlin.math.max

/** How much of a pause a word is worth when a stretch has to be shared out. */
interface SpeechWeighting {
    fun weight(word: String): Double
}

/**
 * English syllables, counted by vowel groups less a silent final "e".
 *
 * Right enough to decide whether a long word gets more of a pause than a short one, and
 * wrong on plenty of words besides; the cases the count is held to are in the shared file.
 */
class EnglishSyllableWeighting : SpeechWeighting {
    override fun weight(word: String): Double = syllableCount(word).toDouble()

    fun syllableCount(word: String): Int {
        val lightest = Rules.shared.lightestWord.toInt()
        val vowels =
            Rules.shared.englishVowels
                .map(Char::toString)
                .toSet()
        val letters = letters(word)
        if (letters.isEmpty()) return lightest

        var count = 0
        var previousWasVowel = false
        for (letter in letters) {
            val isVowel = letter in vowels
            if (isVowel && !previousWasVowel) count += 1
            previousWasVowel = isVowel
        }
        val spelled = letters.joinToString("")
        val silent =
            letters.size >= Rules.shared.shortestWithASilentEnding &&
                spelled.endsWith(Rules.shared.silentEnding) &&
                !spelled.endsWith(Rules.shared.silentEndingExceptAfter) &&
                count > 1
        if (silent) count -= 1
        return max(count, lightest)
    }
}

/** Every word worth the same, for a language this library has no count for. */
class EvenWeighting : SpeechWeighting {
    override fun weight(word: String): Double = 1.0
}
