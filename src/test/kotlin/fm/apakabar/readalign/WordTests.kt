package fm.apakabar.readalign

import com.charleskorn.kaml.Yaml
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
        @Suppress("ktlint:standard:property-naming")
        val printed_parts: List<PartsCase>,
        val normalize: List<NormalizeCase>,
        val similarity: List<SimilarityCase>,
        @Suppress("ktlint:standard:property-naming")
        val english_syllables: List<SyllableCase>,
    )

    @Serializable
    data class PartsCase(val word: String, val parts: Int)

    @Serializable
    data class NormalizeCase(val word: String, val want: String)

    @Serializable
    data class SimilarityCase(
        val left: String,
        val right: String,
        val equals: Double? = null,
        @Suppress("ktlint:standard:property-naming")
        val at_least: Double? = null,
        @Suppress("ktlint:standard:property-naming")
        val at_most: Double? = null,
    )

    @Serializable
    data class SyllableCase(
        val word: String,
        val count: Int? = null,
        @Suppress("ktlint:standard:property-naming")
        val at_least: Int? = null,
    )

    private val cases: Cases by lazy {
        val text =
            requireNotNull(this::class.java.getResourceAsStream("/word_tests.yaml")) {
                "word_tests.yaml is missing: run `make sync-yaml`"
            }.use { it.readBytes().decodeToString() }
        Yaml.default.decodeFromString(Cases.serializer(), text)
    }

    @TestFactory
    fun printedParts(): List<DynamicTest> =
        cases.printed_parts.map { case ->
            DynamicTest.dynamicTest("${case.word} is written in ${case.parts}") {
                assertEquals(case.parts, printedParts(case.word))
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
                val likeness = similarity(normalize(case.left), normalize(case.right))
                case.equals?.let { assertEquals(it, likeness, 1e-9) }
                case.at_least?.let { assertTrue(likeness >= it, "$likeness is under $it") }
                case.at_most?.let { assertTrue(likeness <= it, "$likeness is over $it") }
            }
        }

    @TestFactory
    fun englishSyllables(): List<DynamicTest> =
        cases.english_syllables.map { case ->
            DynamicTest.dynamicTest("${case.word} is said in ${case.count ?: case.at_least}") {
                val counted = EnglishSyllableWeighting().weight(case.word)
                case.count?.let { assertEquals(it.toDouble(), counted, 1e-9) }
                case.at_least?.let { assertTrue(counted >= it, "$counted is under $it") }
            }
        }
}
