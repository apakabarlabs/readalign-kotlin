package fm.apakabar.readalign

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.assertTrue

/** The cases every port of this library is held to, copied here by `make sync-yaml`. */
object Corpus {
    const val TOLERANCE = 0.001

    fun <T> load(
        path: String,
        serializer: KSerializer<T>,
    ): T {
        val text =
            requireNotNull(Corpus::class.java.getResourceAsStream("/$path")) {
                "$path is missing: run `make sync-yaml`"
            }.use { it.readBytes().decodeToString() }
        return Yaml.default.decodeFromString(serializer, text)
    }
}

@Serializable
data class HeardWord(
    val text: String,
    val start: Double,
    val end: Double,
) {
    val recognized: RecognizedWord get() = RecognizedWord(text, start, end)
}

@Serializable
data class EquivalentEntry(
    val written: String,
    val heard: String,
    val after: String? = null,
)

/** The patch table a case hands the alignment, in the shape the library asks for. */
fun patch(entries: List<EquivalentEntry>?): ((String, String, String?) -> Boolean)? {
    if (entries == null) return null
    return { written, heard, after ->
        entries.any { entry ->
            entry.written == written && entry.heard == heard && (entry.after == null || entry.after == after)
        }
    }
}

/** A weighting that answers nothing usable, for the case that has to survive one. */
class UnusableWeighting : SpeechWeighting {
    override fun weight(word: String): Double = Double.NaN
}

/** Which weighting a case asks for, by the name the corpus writes. */
@Serializable
enum class WeightingName {
    @SerialName("english")
    ENGLISH,

    @SerialName("even")
    EVEN,

    @SerialName("unusable")
    UNUSABLE,
    ;

    val weighting: SpeechWeighting
        get() =
            when (this) {
                ENGLISH -> EnglishSyllableWeighting()
                EVEN -> EvenWeighting()
                UNUSABLE -> UnusableWeighting()
            }
}

@Serializable
data class SpanExpectation(
    val word: Int,
    val start: Double? = null,
    val end: Double? = null,
    @SerialName("start_at_least") val startAtLeast: Double? = null,
    @SerialName("end_at_least") val endAtLeast: Double? = null,
    @SerialName("end_at_most") val endAtMost: Double? = null,
) {
    val pinsSomething: Boolean
        get() = start != null || end != null || startAtLeast != null || endAtLeast != null || endAtMost != null
}

fun WordSpan.check(
    against: SpanExpectation,
    name: String,
) {
    val subject = "$name, word ${against.word}"
    assertTrue(against.pinsSomething, "$subject: pins nothing, so a key here is misspelt")
    against.start?.let { assertTrue(kotlin.math.abs(start - it) < Corpus.TOLERANCE, "$subject: start is $start") }
    against.end?.let { assertTrue(kotlin.math.abs(end - it) < Corpus.TOLERANCE, "$subject: end is $end") }
    against.startAtLeast?.let { assertTrue(start >= it - Corpus.TOLERANCE, "$subject: start is $start") }
    against.endAtLeast?.let { assertTrue(end >= it - Corpus.TOLERANCE, "$subject: end is $end") }
    against.endAtMost?.let { assertTrue(end <= it + Corpus.TOLERANCE, "$subject: end is $end") }
}

/** What every case is held to, whatever else it pins. */
fun expectWellFormed(
    spans: List<WordSpan>,
    count: Int,
    name: String,
) {
    assertTrue(spans.size == count, "$name: one span per word")
    for ((index, span) in spans.withIndex()) {
        assertTrue(span.end >= span.start - Corpus.TOLERANCE, "$name: word $index ends before it starts")
    }
    for ((earlier, later) in spans.zipWithNext()) {
        assertTrue(later.start >= earlier.start - Corpus.TOLERANCE, "$name: spans go backwards")
    }
}
