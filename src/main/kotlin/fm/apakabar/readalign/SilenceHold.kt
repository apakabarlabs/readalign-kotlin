package fm.apakabar.readalign

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Holds a word open through the silence behind it, and widens it into the quiet on both
 * sides.
 *
 * A recogniser marks where a word stops being audible, not where the voice has finished
 * with it: the release of a final consonant and the fall of a line land past the mark,
 * and the burst of an opening consonant lands before it.
 */
object SilenceHold {
    val frameSeconds: Double get() = Rules.shared.frameSeconds

    fun held(
        spans: List<WordSpan>,
        samples: FloatArray,
        sampleRate: Double,
        limit: Double = Rules.shared.holdLimit,
    ): List<WordSpan> {
        val frames = energyFrames(samples, sampleRate)
        if (frames.isEmpty()) return spans
        val threshold = speechThreshold(frames)
        val eachFrame = Rules.shared.frameSeconds
        val duration = samples.size / sampleRate

        return spans.indices.map { index ->
            val span = spans[index]
            val next = if (index + 1 < spans.size) spans[index + 1].start else duration
            val ceiling = min(next, span.end + limit)
            var end = span.end
            var frame = (span.end / eachFrame).toInt()
            var wentQuiet = false
            while ((frame + 1) * eachFrame <= ceiling && frame < frames.size) {
                if (frames[frame] < threshold) {
                    wentQuiet = true
                } else if (wentQuiet) {
                    break
                }
                end = (frame + 1) * eachFrame
                frame += 1
            }
            WordSpan(
                start = span.start,
                end = min(max(span.end, end), max(next, span.start)),
            )
        }
    }

    /**
     * Each word given the quiet on both sides of it, so that a cut along its edges keeps
     * the consonants a recogniser leaves outside its mark.
     *
     * A start goes back from its mark through the word's own sound and then through the
     * quiet before it, and stops where the recording speaks again; an end goes on the same
     * way. Neither passes the middle of the gap to the neighbouring mark, the ends of the
     * recording, or [limit] from its own mark. An edge that meets no quiet before one of
     * those stays where it was: with no pause between two words there is nothing to give
     * either of them. So does an edge that would move by less than `least_widening`, each
     * edge judged on its own and the distance counted in samples.
     *
     * Where an edge between two words falls inside a sound, both words first meet in the
     * middle of the nearest pause within [limit] of it: the recogniser marks a word from
     * where it is sure of it, so the opening hiss of the next word, or the first stretch of
     * a vowel it opens with, sounds before its mark. That holds for two words marked edge
     * to edge and for two with a gap between their marks alike, since the gap can lie in
     * the next word's own sound. The edge goes no further than the middle of either word,
     * so a short word keeps the pause after it, and words whose marks overlap keep them.
     */
    fun widened(
        marks: List<WordSpan>,
        samples: FloatArray,
        sampleRate: Double,
        limit: Double = Rules.shared.holdLimit,
    ): List<WordSpan> {
        val frames = energyFrames(samples, sampleRate)
        if (frames.isEmpty()) return marks
        val threshold = speechThreshold(frames)
        val size = max((Rules.shared.frameSeconds * sampleRate).toInt(), 1)
        val eachFrame = size / sampleRate
        val duration = samples.size / sampleRate
        val spans = settled(marks, frames, threshold, eachFrame, limit, sampleRate)

        return spans.indices.map { index ->
            val span = spans[index]
            var floor = max(0.0, span.start - limit)
            if (index > 0) {
                floor = max(floor, (spans[index - 1].end + span.start) / 2)
            }
            var ceiling = min(duration, span.end + limit)
            if (index + 1 < spans.size) {
                ceiling = min(ceiling, (span.end + spans[index + 1].start) / 2)
            }

            var start = span.start
            var frame = ((span.start * sampleRate).roundToLong().toInt() + size - 1) / size - 1
            var wentQuiet = false
            while (frame >= 0 && frame < frames.size && (frame + 1) * eachFrame > floor) {
                if (frames[frame] < threshold) {
                    wentQuiet = true
                } else if (wentQuiet) {
                    break
                }
                start = max(frame * eachFrame, floor)
                frame -= 1
            }

            var end = span.end
            frame = (span.end * sampleRate).roundToLong().toInt() / size
            var endWentQuiet = false
            while (frame >= 0 && frame < frames.size && frame * eachFrame < ceiling) {
                if (frames[frame] < threshold) {
                    endWentQuiet = true
                } else if (endWentQuiet) {
                    break
                }
                end = min((frame + 1) * eachFrame, ceiling)
                frame += 1
            }

            val least = (Rules.shared.leastWidening * sampleRate).roundToLong()
            val startMoves = ((span.start - start) * sampleRate).roundToLong() >= least
            val endMoves = ((end - span.end) * sampleRate).roundToLong() >= least
            WordSpan(
                start = if (wentQuiet && startMoves) start else span.start,
                end = if (endWentQuiet && endMoves) end else span.end,
            )
        }
    }

    private fun settled(
        spans: List<WordSpan>,
        frames: List<Double>,
        threshold: Double,
        eachFrame: Double,
        limit: Double,
        sampleRate: Double,
    ): List<WordSpan> {
        val pauses = quietRuns(frames, threshold, max((Rules.shared.pauseSeconds / eachFrame).roundToInt(), 1))
        val settled = spans.toMutableList()
        for (index in 0 until settled.size - 1) {
            val word = settled[index]
            val following = settled[index + 1]
            if ((word.end * sampleRate).roundToLong() > (following.start * sampleRate).roundToLong()) continue
            val loud =
                listOf(following.start, word.end).filter { edge ->
                    val frame = (edge / eachFrame).roundToInt()
                    frame > 0 && frame < frames.size && frames[frame - 1] >= threshold && frames[frame] >= threshold
                }
            if (loud.isEmpty()) continue
            val lowest = (word.start + word.end) / 2
            val highest = (following.start + following.end) / 2
            val nearest =
                pauses
                    .mapNotNull { run ->
                        val from = run.first * eachFrame
                        val to = (run.last + 1) * eachFrame
                        val distance = loud.minOf { edge -> if (to <= edge) edge - to else from - edge }
                        val middle = (from + to) / 2
                        if (distance <= limit && middle in lowest..highest) distance to middle else null
                    }.minByOrNull { it.first } ?: continue
            settled[index] = WordSpan(word.start, nearest.second)
            settled[index + 1] = WordSpan(nearest.second, following.end)
        }
        return settled
    }

    private fun quietRuns(
        frames: List<Double>,
        threshold: Double,
        atLeast: Int,
    ): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var start: Int? = null
        for (index in frames.indices) {
            if (frames[index] < threshold) {
                if (start == null) start = index
            } else if (start != null) {
                if (index - start >= atLeast) runs.add(start until index)
                start = null
            }
        }
        if (start != null && frames.size - start >= atLeast) runs.add(start until frames.size)
        return runs
    }

    /** How loud the recording is over each short stretch of it. */
    fun energyFrames(
        samples: FloatArray,
        sampleRate: Double,
    ): List<Double> {
        val size = max((Rules.shared.frameSeconds * sampleRate).toInt(), 1)
        return (samples.indices step size).map { start ->
            val end = min(start + size, samples.size)
            var sum = 0.0
            for (index in start until end) sum += samples[index].toDouble() * samples[index].toDouble()
            sqrt(sum / (end - start))
        }
    }

    /**
     * Louder than the room, quieter than a voice, taken from this recording rather than
     * fixed: one reader is recorded hotter than another.
     */
    fun speechThreshold(frames: List<Double>): Double {
        if (frames.isEmpty()) return 0.0
        val room = frames.sorted()[(frames.size * Rules.shared.roomQuantile).toInt()]
        return maxOf(
            room * Rules.shared.speechAboveRoom,
            Rules.shared.quietestRoom,
            speechLevel(frames) * Rules.shared.quietBelowVoice,
        )
    }

    /** How loud this recording speaks, as the mean of its loudest share. */
    fun speechLevel(frames: List<Double>): Double {
        if (frames.isEmpty()) return Rules.shared.quietestSpeech
        val quieter = (frames.size * (1 - Rules.shared.speechFromLoudestShare)).toInt()
        val louder = frames.sorted().drop(quieter)
        return max(louder.sum() / louder.size, Rules.shared.quietestSpeech)
    }
}
