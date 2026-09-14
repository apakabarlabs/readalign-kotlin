package fm.apakabar.readalign

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.assertTrue

@Serializable
data class Stretch(
    val level: Float,
    val seconds: Double,
)

/** A recording written as stretches of one loudness, so no audio file joins the corpus. */
fun List<Stretch>.samples(sampleRate: Double): FloatArray =
    flatMap { stretch ->
        List((stretch.seconds * sampleRate).roundToInt()) { stretch.level }
    }.toFloatArray()

@Serializable
data class Mark(
    val start: Double,
    val end: Double,
)

@Serializable
data class HoldCase(
    val name: String,
    val why: String? = null,
    @SerialName("sample_rate") val sampleRate: Double,
    val limit: Double? = null,
    val waveform: List<Stretch>,
    val spans: List<Mark>,
    val want: List<SpanExpectation>? = null,
) {
    val assertsSomething: Boolean get() = !want.isNullOrEmpty()

    val marks: List<WordSpan> get() = spans.map { WordSpan(it.start, it.end) }
}

@Serializable
data class HoldSection(
    val section: String,
    val cases: List<HoldCase>,
)

@Serializable
data class SpeechLevelCase(
    val name: String,
    @SerialName("sample_rate") val sampleRate: Double,
    val waveform: List<Stretch>,
    val equals: Double? = null,
    @SerialName("at_least") val atLeast: Double? = null,
)

@Serializable
data class HoldFile(
    @SerialName("speech_level") val speechLevel: List<SpeechLevelCase>,
    val tests: List<HoldSection>,
)

/** How far past its own mark a word is held, and how loud the recording speaks. */
class HoldTests {
    private val file = Corpus.load("hold_tests.yaml", HoldFile.serializer())

    @TestFactory
    fun measuresHowLoudlyTheRecordingSpeaks(): List<DynamicTest> =
        file.speechLevel.map { levelCase ->
            DynamicTest.dynamicTest(levelCase.name) {
                assertTrue(
                    levelCase.equals != null || levelCase.atLeast != null,
                    "${levelCase.name}: pins nothing",
                )

                val level =
                    SilenceHold.speechLevel(
                        SilenceHold.energyFrames(
                            levelCase.waveform.samples(levelCase.sampleRate),
                            levelCase.sampleRate,
                        ),
                    )

                levelCase.equals?.let {
                    assertTrue(abs(level - it) < Corpus.TOLERANCE, "${levelCase.name}: $level")
                }
                levelCase.atLeast?.let {
                    assertTrue(level >= it, "${levelCase.name}: $level")
                }
            }
        }

    @TestFactory
    fun holdsAsTheCorpusSays(): List<DynamicTest> =
        file.tests
            .flatMap { it.cases }
            .map { holdCase ->
                DynamicTest.dynamicTest(holdCase.name) {
                    assertTrue(holdCase.assertsSomething, "${holdCase.name}: asserts nothing")

                    val held =
                        SilenceHold.held(
                            holdCase.marks,
                            samples = holdCase.waveform.samples(holdCase.sampleRate),
                            sampleRate = holdCase.sampleRate,
                            limit = holdCase.limit ?: Rules.shared.holdLimit,
                        )

                    expectWellFormed(held, holdCase.spans.size, holdCase.name)
                    for (expectation in holdCase.want.orEmpty()) {
                        held[expectation.word].check(expectation, holdCase.name)
                    }
                }
            }
}
