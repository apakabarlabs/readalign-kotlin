package fm.apakabar.readalign

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every example the README shows, run. A README nobody runs describes the library it
 * described when it was written, and the reader who copies it out finds that out first.
 */
class ReadmeTests {
    @Test
    fun `align says when each written word was spoken`() {
        val spans =
            TranscriptAligner.align(
                expected = listOf("From", "fairest", "creatures", "we", "desire", "increase"),
                heard =
                    listOf(
                        RecognizedWord("from", 0.00, 0.32),
                        RecognizedWord("farest", 0.32, 0.81),
                        RecognizedWord("creatures", 0.81, 1.44),
                        RecognizedWord("we", 1.44, 1.60),
                        RecognizedWord("desire", 1.60, 2.08),
                        RecognizedWord("increase", 2.08, 2.72),
                    ),
                duration = 3.0,
            )

        assertEquals(0.32, spans[1].start)
        assertEquals(0.81, spans[1].end)
    }

    @Test
    fun `a recording nothing was heard in comes back empty`() {
        assertTrue(
            TranscriptAligner
                .align(
                    expected = listOf("From", "fairest"),
                    heard = emptyList(),
                    duration = 3.0,
                ).isEmpty(),
        )
    }

    @Test
    fun `pair answers which word came back as which`() {
        val matches =
            TranscriptAligner.pair(
                expected = listOf("hearts", "shouldst", "owe"),
                heard = listOf("hearts", "should", "stow"),
                threshold = 0.6,
            )

        assertTrue(matches.any { it.expected == 1 until 3 && it.heard == 1 until 3 })
    }

    @Test
    fun `a patch vouches for a pair likeness cannot carry`() {
        val matches =
            TranscriptAligner.pair(
                expected = listOf("the", "heir"),
                heard = listOf("the", "air"),
                threshold = 0.6,
                equivalent = { written, heard, _ -> written == "heir" && heard == "air" },
            )

        assertTrue(matches.any { it.expected == 1 until 2 })
    }

    @Test
    fun `held carries a mark into the quiet behind it`() {
        val samples =
            (
                FloatArray(1500) { 0.5f } + FloatArray(1000) { 0.0f } +
                    FloatArray(2000) { 0.5f } + FloatArray(1000) { 0.0f }
            )

        val spans =
            SilenceHold.held(
                listOf(WordSpan(0.05, 0.10)),
                samples = samples,
                sampleRate = 10000.0,
            )

        assertTrue(spans[0].end > 0.10)
    }

    @Test
    fun `another language brings its own weighting`() {
        val spans =
            TranscriptAligner.align(
                expected = listOf("kuća", "čaša", "šuma"),
                heard =
                    listOf(
                        RecognizedWord("kuca", 0.0, 0.8),
                        RecognizedWord("casa", 1.0, 1.8),
                        RecognizedWord("suma", 2.0, 2.8),
                    ),
                duration = 4.0,
                weighting = EvenWeighting(),
            )

        assertEquals(1.0, spans[1].start)
    }
}
