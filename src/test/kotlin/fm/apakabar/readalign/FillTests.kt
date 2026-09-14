package fm.apakabar.readalign

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertTrue

@Serializable
data class FoundPair(
    val word: Int,
    val text: String,
    val start: Double,
    val end: Double,
)

@Serializable
data class FillCase(
    val name: String,
    val why: String? = null,
    val expected: List<String>,
    val pairs: List<FoundPair>,
    val duration: Double,
    val weighting: WeightingName? = null,
    val want: List<SpanExpectation>? = null,
    @SerialName("non_overlapping") val nonOverlapping: Boolean? = null,
) {
    val assertsSomething: Boolean get() = !want.isNullOrEmpty() || nonOverlapping == true
}

@Serializable
data class FillSection(
    val section: String,
    val cases: List<FillCase>,
)

@Serializable
data class FillFile(
    val tests: List<FillSection>,
)

/** What the words nobody heard are given, out of the time their neighbours leave. */
class FillTests {
    @TestFactory
    fun fillsAsTheCorpusSays(): List<DynamicTest> =
        Corpus
            .load("fill_tests.yaml", FillFile.serializer())
            .tests
            .flatMap { it.cases }
            .map { fillCase ->
                DynamicTest.dynamicTest(fillCase.name) {
                    assertTrue(fillCase.assertsSomething, "${fillCase.name}: asserts nothing")

                    val pairs =
                        fillCase.pairs.associate {
                            it.word to RecognizedWord(it.text, it.start, it.end)
                        }

                    val spans =
                        TranscriptAligner.fill(
                            expected = fillCase.expected,
                            pairs = pairs,
                            duration = fillCase.duration,
                            weighting = (fillCase.weighting ?: WeightingName.ENGLISH).weighting,
                        )

                    expectWellFormed(spans, fillCase.expected.size, fillCase.name)
                    for (expectation in fillCase.want.orEmpty()) {
                        spans[expectation.word].check(expectation, fillCase.name)
                    }
                    if (fillCase.nonOverlapping != true) return@dynamicTest
                    for ((earlier, later) in spans.zipWithNext()) {
                        assertTrue(
                            later.start >= earlier.end - Corpus.TOLERANCE,
                            "${fillCase.name}: two words claim the same instant",
                        )
                    }
                }
            }
}
