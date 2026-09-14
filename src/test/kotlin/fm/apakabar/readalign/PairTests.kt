package fm.apakabar.readalign

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertTrue

/** Which written word came back as which heard word, by the cases every port shares. */
class PairTests {
    @Serializable
    data class Pairing(
        val expected: List<Int>,
        val heard: List<Int>,
    ) {
        fun matches(match: WordMatch): Boolean = match.expected == expected[0] until expected[1] && match.heard == heard[0] until heard[1]

        val described: String get() = "expected $expected, heard $heard"
    }

    @Serializable
    data class PairCase(
        val name: String,
        val why: String? = null,
        val expected: List<String>,
        val heard: List<String>,
        val threshold: Double,
        val equivalent: List<EquivalentEntry>? = null,
        val want: List<Pairing>? = null,
        @SerialName("want_absent") val wantAbsent: List<Pairing>? = null,
    ) {
        val assertsSomething: Boolean get() = !want.isNullOrEmpty() || !wantAbsent.isNullOrEmpty()
    }

    @Serializable
    data class PairSection(
        val section: String,
        val cases: List<PairCase>,
    )

    @Serializable
    data class PairFile(
        val tests: List<PairSection>,
    )

    @TestFactory
    fun pairsAsTheCorpusSays(): List<DynamicTest> =
        Corpus
            .load("pair_tests.yaml", PairFile.serializer())
            .tests
            .flatMap { it.cases }
            .map { case ->
                DynamicTest.dynamicTest(case.name) {
                    assertTrue(case.assertsSomething, "${case.name}: asserts nothing")
                    val matches =
                        TranscriptAligner.pair(
                            expected = case.expected,
                            heard = case.heard,
                            threshold = case.threshold,
                            equivalent = patch(case.equivalent),
                        )
                    for (wanted in case.want.orEmpty()) {
                        assertTrue(
                            matches.any(wanted::matches),
                            "${case.name}: no match for ${wanted.described}",
                        )
                    }
                    for (absent in case.wantAbsent.orEmpty()) {
                        assertTrue(
                            matches.none(absent::matches),
                            "${case.name}: matched ${absent.described} and should not have",
                        )
                    }
                }
            }
}
