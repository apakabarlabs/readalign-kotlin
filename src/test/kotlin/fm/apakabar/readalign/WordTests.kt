package fm.apakabar.readalign

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two word-level helpers everything else is built on, held to the cases every port
 * of this library shares. The file is copied from the leading port by `make sync-yaml`.
 */
class WordTests {
    @Serializable
    data class Cases(
        @SerialName("printed_parts") val printedParts: List<PartsCase>,
        val letters: List<LettersCase>,
        val normalize: List<NormalizeCase>,
        val similarity: List<SimilarityCase>,
        @SerialName("english_syllables") val englishSyllables: List<SyllableCase>,
        val spoken: List<SpokenCase>,
    )

    @Serializable
    data class TimedWord(
        val text: String,
        val start: Double,
        val end: Double,
    ) {
        val recognized: RecognizedWord get() = RecognizedWord(text, start, end)
    }

    @Serializable
    data class SpokenCase(
        val name: String,
        val tokens: List<TimedWord>,
        val equals: List<TimedWord>,
    )

    @Serializable
    data class PartsCase(
        val word: String,
        val parts: Int,
    )

    @Serializable
    data class LettersCase(
        val word: String,
        val want: List<String>,
    )

    @Serializable
    data class NormalizeCase(
        val word: String,
        val want: String,
    )

    @Serializable
    data class SimilarityCase(
        val left: String,
        val right: String,
        val equals: Double? = null,
        @SerialName("at_least") val atLeast: Double? = null,
        @SerialName("at_most") val atMost: Double? = null,
    )

    @Serializable
    data class SyllableCase(
        val word: String,
        val count: Int? = null,
        @SerialName("at_least") val atLeast: Int? = null,
    )

    private val cases: Cases by lazy {
        val text =
            checkNotNull(this::class.java.getResourceAsStream("/word_tests.yaml")) {
                "word_tests.yaml is missing: run `make sync-yaml`"
            }.use { it.readBytes().decodeToString() }
        Yaml.default.decodeFromString(Cases.serializer(), text)
    }

    @TestFactory
    fun printedParts(): List<DynamicTest> =
        cases.printedParts.map { case ->
            DynamicTest.dynamicTest("${case.word} is written in ${case.parts}") {
                assertEquals(case.parts, printedParts(case.word))
            }
        }

    @TestFactory
    fun cutsAWordIntoLetters(): List<DynamicTest> =
        cases.letters.map { case ->
            DynamicTest.dynamicTest("${case.word} is written in ${case.want.size}") {
                assertEquals(case.want, letters(case.word))
            }
        }

    @TestFactory
    fun normalize(): List<DynamicTest> =
        cases.normalize.map { case ->
            DynamicTest.dynamicTest("${case.word} -> ${case.want}") {
                assertEquals(case.want, normalize(case.word))
            }
        }

    @TestFactory
    fun similarity(): List<DynamicTest> =
        cases.similarity.map { case ->
            DynamicTest.dynamicTest("${case.left} against ${case.right}") {
                val likeness = similarity(case.left, case.right)
                case.equals?.let { assertEquals(it, likeness, 1e-9) }
                case.atLeast?.let { assertTrue(likeness >= it, "$likeness is under $it") }
                case.atMost?.let { assertTrue(likeness <= it, "$likeness is over $it") }
            }
        }

    @TestFactory
    fun englishSyllables(): List<DynamicTest> =
        cases.englishSyllables.map { case ->
            DynamicTest.dynamicTest("${case.word} is said in ${case.count ?: case.atLeast}") {
                val counted = EnglishSyllableWeighting().weight(case.word)
                case.count?.let { assertEquals(it.toDouble(), counted, 1e-9) }
                case.atLeast?.let { assertTrue(counted >= it, "$counted is under $it") }
            }
        }

    @TestFactory
    fun gathersTokensIntoTheWordsTheCorpusNames(): List<DynamicTest> =
        cases.spoken.map { case ->
            DynamicTest.dynamicTest(case.name) {
                val said = spoken(case.tokens.map { it.recognized })

                assertEquals(case.equals.map { it.recognized }, said, case.name)
            }
        }
}
