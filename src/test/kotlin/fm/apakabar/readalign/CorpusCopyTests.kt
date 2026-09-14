package fm.apakabar.readalign

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.net.URI
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rules and the cases belong to the leading port and live here as a copy that
 * `make sync-yaml` refreshes. Nobody reads that repository at run time, so a copy left
 * behind would keep this port on older behaviour with every test still green: the case
 * that never arrived is a case these tests do not know.
 *
 * So the copies are held against the leading repository itself. A file that has moved on
 * there fails here, and so does a file that has not been copied at all.
 */
class CorpusCopyTests {
    private fun fetch(path: String): String {
        val address = "https://raw.githubusercontent.com/apakabarlabs/readalign-swift/main/$path"
        val request =
            java.net.http.HttpRequest
                .newBuilder(URI.create(address))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build()
        val answer =
            java.net.http.HttpClient
                .newHttpClient()
                .send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
        assertTrue(answer.statusCode() == OK, "$address answered ${answer.statusCode()}")
        return answer.body()
    }

    private fun copy(path: String): String =
        requireNotNull(CorpusCopyTests::class.java.getResourceAsStream(path)) {
            "$path is missing: run `make sync-yaml`"
        }.use { it.readBytes().decodeToString() }

    @TestFactory
    fun matchesTheLeadingPort(): List<DynamicTest> =
        SHARED.map { (there, here) ->
            DynamicTest.dynamicTest(here.substringAfterLast('/')) {
                assertEquals(
                    fetch(there),
                    copy(here),
                    "$here differs from the leading port: run `make sync-yaml`",
                )
            }
        }

    companion object {
        private const val OK = 200
        private const val TIMEOUT_SECONDS = 10L

        /**
         * Every file copied from the leading port, as a pair of where it lives there and
         * where it lives here. A new shared file is checked from the moment it is added
         * to this list, rather than when somebody remembers to write a test for it.
         */
        private val SHARED =
            listOf(
                "Sources/ReadAlign/Resources/rules.yaml" to "/rules.yaml",
                "Tests/ReadAlignTests/Resources/align_tests.yaml" to "/align_tests.yaml",
                "Tests/ReadAlignTests/Resources/fill_tests.yaml" to "/fill_tests.yaml",
                "Tests/ReadAlignTests/Resources/hold_tests.yaml" to "/hold_tests.yaml",
                "Tests/ReadAlignTests/Resources/match_tests.yaml" to "/match_tests.yaml",
                "Tests/ReadAlignTests/Resources/pair_tests.yaml" to "/pair_tests.yaml",
                "Tests/ReadAlignTests/Resources/word_tests.yaml" to "/word_tests.yaml",
            )
    }
}
