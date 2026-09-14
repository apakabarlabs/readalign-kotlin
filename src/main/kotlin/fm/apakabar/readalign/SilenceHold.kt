package fm.apakabar.readalign

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Holds a word open through the silence behind it.
 *
 * A recogniser marks where a word stops being audible, not where the voice has finished
 * with it: the release of a final consonant and the fall of a line land past the mark.
 * This is the one part that needs the recording itself rather than the transcript.
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
