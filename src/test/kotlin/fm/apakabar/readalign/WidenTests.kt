package fm.apakabar.readalign

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertTrue

@Serializable
data class WidenFile(
    val tests: List<HoldSection>,
)

/** How far each word reaches into the quiet on both sides of it. */
class WidenTests {
    private val file = Corpus.load("widen_tests.yaml", WidenFile.serializer())

    @TestFactory
    fun widensAsTheCorpusSays(): List<DynamicTest> =
        file.tests
            .flatMap { it.cases }
            .map { widenCase ->
                DynamicTest.dynamicTest(widenCase.name) {
                    assertTrue(widenCase.assertsSomething, "${widenCase.name}: asserts nothing")

                    val widened =
                        SilenceHold.widened(
                            widenCase.marks,
                            samples = widenCase.waveform.samples(widenCase.sampleRate),
                            sampleRate = widenCase.sampleRate,
                            limit = widenCase.limit ?: Rules.shared.holdLimit,
                        )

                    expectWellFormed(widened, widenCase.spans.size, widenCase.name)
                    for (expectation in widenCase.want.orEmpty()) {
                        widened[expectation.word].check(expectation, widenCase.name)
                    }
                }
            }
}
