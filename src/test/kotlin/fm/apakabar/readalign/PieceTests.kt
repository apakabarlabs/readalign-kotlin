package fm.apakabar.readalign

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Serializable
data class PauseCase(
    val name: String,
    @SerialName("sample_rate") val sampleRate: Double,
    val waveform: List<Stretch>,
    val equals: List<Int>,
)

@Serializable
data class CutCase(
    val name: String,
    @SerialName("sample_rate") val sampleRate: Double,
    val waveform: List<Stretch>,
    val equals: List<List<Int>>,
) {
    val pieces: List<IntRange> get() = equals.map { it[0] until it[1] }
}

@Serializable
data class JoinCase(
    val name: String,
    @SerialName("sample_rate") val sampleRate: Double,
    val pieces: List<List<Int>>,
    val heard: List<List<HeardWord>>,
    val equals: List<HeardWord>,
) {
    val ranges: List<IntRange> get() = pieces.map { it[0] until it[1] }
    val transcripts: List<List<RecognizedWord>> get() = heard.map { piece -> piece.map { it.recognized } }
    val reading: List<RecognizedWord> get() = equals.map { it.recognized }
}

@Serializable
data class JoinRefusalCase(
    val name: String,
    @SerialName("sample_rate") val sampleRate: Double,
    val pieces: List<List<Int>>,
    val heard: List<List<HeardWord>>,
) {
    val ranges: List<IntRange> get() = pieces.map { it[0] until it[1] }
    val transcripts: List<List<RecognizedWord>> get() = heard.map { piece -> piece.map { it.recognized } }
}

@Serializable
data class HeardCase(
    val name: String,
    @SerialName("sample_rate") val sampleRate: Double,
    val waveform: List<Stretch>,
    val answers: List<List<HeardWord>>,
    @SerialName("asked_lengths") val askedLengths: List<Int>,
    val equals: List<HeardWord>,
) {
    val transcripts: List<List<RecognizedWord>> get() = answers.map { answer -> answer.map { it.recognized } }
    val reading: List<RecognizedWord> get() = equals.map { it.recognized }
}

@Serializable
data class PieceFile(
    val pauses: List<PauseCase>,
    val cuts: List<CutCase>,
    val joins: List<JoinCase>,
    @SerialName("join_refusals") val joinRefusals: List<JoinRefusalCase>,
    val heard: List<HeardCase>,
)

/** Where a recording is cut into the pieces a recogniser is asked one at a time. */
class PieceTests {
    private val file = Corpus.load("piece_tests.yaml", PieceFile.serializer())

    @TestFactory
    fun findsThePausesTheCorpusNames(): List<DynamicTest> =
        file.pauses.map { pauseCase ->
            DynamicTest.dynamicTest(pauseCase.name) {
                val found = Pieces.pauses(pauseCase.waveform.samples(pauseCase.sampleRate), pauseCase.sampleRate)

                assertEquals(pauseCase.equals, found, pauseCase.name)
            }
        }

    @TestFactory
    fun cutsWhereTheCorpusSays(): List<DynamicTest> =
        file.cuts.map { cutCase ->
            DynamicTest.dynamicTest(cutCase.name) {
                val pieces = Pieces.cuts(cutCase.waveform.samples(cutCase.sampleRate), cutCase.sampleRate)

                assertEquals(cutCase.pieces, pieces, cutCase.name)
            }
        }

    @TestFactory
    fun leavesNoSampleOutOfEveryPiece(): List<DynamicTest> =
        file.cuts.map { cutCase ->
            DynamicTest.dynamicTest(cutCase.name) {
                val samples = cutCase.waveform.samples(cutCase.sampleRate)

                val pieces = Pieces.cuts(samples, cutCase.sampleRate)

                assertEquals(0, pieces.first().first, "${cutCase.name}: does not start at the beginning")
                assertEquals(samples.size, pieces.last().last + 1, "${cutCase.name}: ends short")
                for ((earlier, later) in pieces.zipWithNext()) {
                    assertTrue(later.first <= earlier.last + 1, "${cutCase.name}: a gap between pieces")
                    assertTrue(later.first > earlier.first, "${cutCase.name}: a piece that goes nowhere")
                }
            }
        }

    @TestFactory
    fun joinsThePiecesIntoTheReadingTheCorpusNames(): List<DynamicTest> =
        file.joins.map { joinCase ->
            DynamicTest.dynamicTest(joinCase.name) {
                val reading = Pieces.joined(joinCase.transcripts, joinCase.ranges, joinCase.sampleRate)

                assertEquals(joinCase.reading, reading, joinCase.name)
            }
        }

    @TestFactory
    fun refusesAPieceAndItsTranscriptThatDoNotPairOff(): List<DynamicTest> =
        file.joinRefusals.map { refusal ->
            DynamicTest.dynamicTest(refusal.name) {
                assertFailsWith<UnevenPiecesException>(refusal.name) {
                    Pieces.joined(refusal.transcripts, refusal.ranges, refusal.sampleRate)
                }
            }
        }

    @TestFactory
    fun recoversWhatTheCorpusSays(): List<DynamicTest> =
        file.heard.map { heardCase ->
            DynamicTest.dynamicTest(heardCase.name) {
                val answers = heardCase.transcripts.toMutableList()
                val asked = mutableListOf<Int>()

                val words =
                    Pieces.heard(heardCase.waveform.samples(heardCase.sampleRate), heardCase.sampleRate) { given ->
                        asked.add(given.size)
                        answers.removeFirst()
                    }

                assertEquals(heardCase.reading, words, heardCase.name)
                assertEquals(heardCase.askedLengths, asked, "${heardCase.name}: asked $asked")
            }
        }

    private fun silentUntilTrimmedBy(
        seconds: Double,
        ofLength: Int,
        asked: MutableList<Int>,
    ): (FloatArray) -> List<RecognizedWord> {
        val speaksAtOrBelow = ofLength - (seconds * SAMPLE_RATE).toInt()
        return { piece ->
            asked.add(piece.size)
            if (piece.size <= speaksAtOrBelow) ONE_WORD else emptyList()
        }
    }

    private fun saysNothing(asked: MutableList<Int>): (FloatArray) -> List<RecognizedWord> =
        { piece ->
            asked.add(piece.size)
            emptyList()
        }

    @Test
    fun asksOnceWhenTheFirstAnswerHasWordsInIt() {
        val piece = FloatArray(10 * SAMPLE_RATE.toInt()) { 0.1f }
        val asked = mutableListOf<Int>()

        val words = Pieces.heard(piece, SAMPLE_RATE, silentUntilTrimmedBy(0.0, piece.size, asked))

        assertEquals(ONE_WORD, words)
        assertEquals(listOf(piece.size), asked)
    }

    @Test
    fun asksAgainWithLessOfTheTailUntilSomethingComesBack() {
        val piece = FloatArray(10 * SAMPLE_RATE.toInt()) { 0.1f }
        val asked = mutableListOf<Int>()

        val words = Pieces.heard(piece, SAMPLE_RATE, silentUntilTrimmedBy(0.3, piece.size, asked))

        assertEquals(ONE_WORD, words)
        assertEquals(listOf(piece.size, piece.size - 1600, piece.size - 3200, piece.size - 4800), asked)
    }

    @Test
    fun leavesAPieceTooShortToExpectWordsFromAskedOnlyOnce() {
        val asked = mutableListOf<Int>()

        val words = Pieces.heard(FloatArray(SAMPLE_RATE.toInt()) { 0.1f }, SAMPLE_RATE, saysNothing(asked))

        assertTrue(words.isEmpty())
        assertEquals(1, asked.size)
    }

    @Test
    fun answersNothingWhenNoTrimBringsWordsBack() {
        val asked = mutableListOf<Int>()

        val words = Pieces.heard(FloatArray(10 * SAMPLE_RATE.toInt()) { 0.1f }, SAMPLE_RATE, saysNothing(asked))

        assertTrue(words.isEmpty())
        assertEquals(1 + Rules.shared.askAgainTrims.size, asked.size)
    }

    private companion object {
        const val SAMPLE_RATE = 16000.0
        val ONE_WORD = listOf(RecognizedWord("heard", 0.0, 1.0))
    }
}
