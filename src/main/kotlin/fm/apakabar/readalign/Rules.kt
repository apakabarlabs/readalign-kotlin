package fm.apakabar.readalign

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The numbers the alignment is tuned to. What each is for is written beside it in `rules.yaml`. */
@Serializable
data class Rules(
    @SerialName("match_threshold") val matchThreshold: Double,
    @SerialName("join_floor") val joinFloor: Double,
    @SerialName("gap_penalty") val gapPenalty: Double,
    @SerialName("mismatch_penalty") val mismatchPenalty: Double,
    @SerialName("room_enough") val roomEnough: Double,
    @SerialName("join_span") val joinSpan: Int,
    @SerialName("vouched_join_span") val vouchedJoinSpan: Int,
    @SerialName("english_vowels") val englishVowels: String,
    @SerialName("silent_ending") val silentEnding: String,
    @SerialName("silent_ending_except_after") val silentEndingExceptAfter: String,
    @SerialName("shortest_with_a_silent_ending") val shortestWithASilentEnding: Int,
    @SerialName("lightest_word") val lightestWord: Double,
    @SerialName("lifted_marks_from") val liftedMarksFrom: String,
    @SerialName("lifted_marks_to") val liftedMarksTo: String,
    @SerialName("letter_joiners") val letterJoiners: List<String>,
    @SerialName("folded_letters") val foldedLetters: Map<String, String>,
    @SerialName("frame_seconds") val frameSeconds: Double,
    @SerialName("room_quantile") val roomQuantile: Double,
    @SerialName("speech_above_room") val speechAboveRoom: Double,
    @SerialName("quietest_room") val quietestRoom: Double,
    @SerialName("hold_limit") val holdLimit: Double,
    @SerialName("speech_from_loudest_share") val speechFromLoudestShare: Double,
    @SerialName("quietest_speech") val quietestSpeech: Double,
    @SerialName("piece_seconds") val pieceSeconds: Double,
    @SerialName("shortest_piece_share") val shortestPieceShare: Double,
    @SerialName("pause_seconds") val pauseSeconds: Double,
    @SerialName("ask_again_trims") val askAgainTrims: List<Double>,
    @SerialName("shortest_worth_asking_again") val shortestWorthAskingAgain: Double,
) {
    /** Whether this mark writes one consonant joined to the next, making them one letter. */
    fun joins(code: Int): Boolean = code in joiners

    /** Whether this is one of the marks likeness is measured without. */
    fun lifts(code: Int): Boolean {
        val first = liftedMarksFrom.toInt(HEXADECIMAL)
        val last = liftedMarksTo.toInt(HEXADECIMAL)
        return code in first..last
    }

    private val joiners: Set<Int> by lazy { letterJoiners.map { it.toInt(HEXADECIMAL) }.toSet() }

    companion object {
        internal const val HEXADECIMAL = 16

        val shared: Rules by lazy {
            val text =
                checkNotNull(Rules::class.java.getResourceAsStream("/rules.yaml")) {
                    "rules.yaml is missing from the library"
                }.use { it.readBytes().decodeToString() }
            Yaml.default.decodeFromString(serializer(), text)
        }
    }
}
