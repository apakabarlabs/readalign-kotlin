package fm.apakabar.readalign

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.math.abs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
data class MatchCase(
    val name: String,
    val why: String? = null,
    val expected: List<String>,
    val heard: List<HeardWord>,
    val weighting: WeightingName? = null,
    val want: List<SpanExpectation>? = null,
    val contiguous: List<Int>? = null,
) {
    val assertsSomething: Boolean get() = !want.isNullOrEmpty() || contiguous != null
}

@Serializable
data class MatchSection(
    val section: String,
    val cases: List<MatchCase>,
)

@Serializable
data class MatchFile(
    val tests: List<MatchSection>,
)

/** Where in the recording each written word the recogniser did return was heard. */
class MatchTests {
    @TestFactory
    fun placesWordsAsTheCorpusSays(): List<DynamicTest> =
        Corpus
            .load("match_tests.yaml", MatchFile.serializer())
            .tests
            .flatMap { it.cases }
            .map { matchCase ->
                DynamicTest.dynamicTest(matchCase.name) {
                    assertTrue(matchCase.assertsSomething, "${matchCase.name}: asserts nothing")

                    val placed =
                        TranscriptAligner.match(
                            expected = matchCase.expected,
                            heard = matchCase.heard.map { it.recognized },
                            weighting = (matchCase.weighting ?: WeightingName.ENGLISH).weighting,
                        )

                    for (expectation in matchCase.want.orEmpty()) {
                        val word = placed[expectation.word]
                        assertNotNull(
                            word,
                            "${matchCase.name}: word ${expectation.word} was not placed at all",
                        )
                        WordSpan(word.start, word.end).check(expectation, matchCase.name)
                        assertTrue(
                            word.end > word.start,
                            "${matchCase.name}: word ${expectation.word} has no length",
                        )
                    }

                    val touching = matchCase.contiguous ?: return@dynamicTest
                    for (index in touching[0] until touching[1] - 1) {
                        val earlier = assertNotNull(placed[index])
                        val later = assertNotNull(placed[index + 1])
                        assertTrue(
                            abs(later.start - earlier.end) < Corpus.TOLERANCE,
                            "${matchCase.name}: words $index and ${index + 1} do not touch",
                        )
                    }
                }
            }
}
