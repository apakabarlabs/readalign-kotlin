package fm.apakabar.readalign

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertTrue

@Serializable
data class AlignCase(
    val name: String,
    val why: String? = null,
    val expected: List<String>,
    val heard: List<HeardWord>,
    val duration: Double,
    val weighting: WeightingName? = null,
    val equivalent: List<EquivalentEntry>? = null,
    val want: List<SpanExpectation>? = null,
    @SerialName("strictly_increasing") val strictlyIncreasing: List<Int>? = null,
    @SerialName("want_empty") val wantEmpty: Boolean? = null,
) {
    val assertsSomething: Boolean
        get() = wantEmpty == true || !want.isNullOrEmpty() || strictlyIncreasing != null
}

@Serializable
data class AlignSection(
    val section: String,
    val cases: List<AlignCase>,
)

@Serializable
data class AlignFile(
    val tests: List<AlignSection>,
)

/** A text, what a recogniser heard, and when each written word was spoken. */
class AlignTests {
    @TestFactory
    fun alignsAsTheCorpusSays(): List<DynamicTest> =
        Corpus
            .load("align_tests.yaml", AlignFile.serializer())
            .tests
            .flatMap { it.cases }
            .map { alignmentCase ->
                DynamicTest.dynamicTest(alignmentCase.name) {
                    assertTrue(alignmentCase.assertsSomething, "${alignmentCase.name}: asserts nothing")

                    val spans =
                        TranscriptAligner.align(
                            expected = alignmentCase.expected,
                            heard = alignmentCase.heard.map { it.recognized },
                            duration = alignmentCase.duration,
                            weighting = (alignmentCase.weighting ?: WeightingName.ENGLISH).weighting,
                            equivalent = patch(alignmentCase.equivalent),
                        )

                    if (alignmentCase.wantEmpty == true) {
                        assertTrue(spans.isEmpty(), "${alignmentCase.name}: nothing rather than a guess")
                        return@dynamicTest
                    }

                    expectWellFormed(spans, alignmentCase.expected.size, alignmentCase.name)
                    for (expectation in alignmentCase.want.orEmpty()) {
                        spans[expectation.word].check(expectation, alignmentCase.name)
                    }
                    val increasing = alignmentCase.strictlyIncreasing ?: return@dynamicTest
                    for (index in increasing[0] until increasing[1] - 1) {
                        assertTrue(
                            spans[index + 1].start > spans[index].start,
                            "${alignmentCase.name}: words $index and ${index + 1} land on one instant",
                        )
                    }
                }
            }
}
