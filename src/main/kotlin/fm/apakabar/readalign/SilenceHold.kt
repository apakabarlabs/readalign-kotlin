package fm.apakabar.readalign

import kotlin.math.max
import kotlin.math.min
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
     */
    fun widened(
        spans: List<WordSpan>,
        samples: FloatArray,
        sampleRate: Double,
        limit: Double = Rules.shared.holdLimit,
    ): List<WordSpan> {
        val frames = energyFrames(samples, sampleRate)
        if (frames.isEmpty()) return spans
        val threshold = speechThreshold(frames)
        val size = max((Rules.shared.frameSeconds * sampleRate).toInt(), 1)
        val eachFrame = size / sampleRate
        val duration = samples.size / sampleRate

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
        return max(room * Rules.shared.speechAboveRoom, Rules.shared.quietestRoom)
    }

    /** How loud this recording speaks, as the mean of its loudest share. */
    fun speechLevel(frames: List<Double>): Double {
        if (frames.isEmpty()) return Rules.shared.quietestSpeech
        val quieter = (frames.size * (1 - Rules.shared.speechFromLoudestShare)).toInt()
        val louder = frames.sorted().drop(quieter)
        return max(louder.sum() / louder.size, Rules.shared.quietestSpeech)
    }
}
