package fm.apakabar.readalign

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
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
data class PieceFile(
    val pauses: List<PauseCase>,
    val cuts: List<CutCase>,
    val joins: List<JoinCase>,
    @SerialName("join_refusals") val joinRefusals: List<JoinRefusalCase>,
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
                    // Overlap where a pause allows it, but never a gap: a sample no piece
                    // holds is a word no recogniser is ever asked about.
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
}
