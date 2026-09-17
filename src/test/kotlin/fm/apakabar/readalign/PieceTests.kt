package fm.apakabar.readalign

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
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
data class PieceFile(
    val pauses: List<PauseCase>,
    val cuts: List<CutCase>,
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
}
