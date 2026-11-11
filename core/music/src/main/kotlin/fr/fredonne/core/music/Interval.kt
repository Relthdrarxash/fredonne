package fr.fredonne.core.music

/**
 * Intervalle orienté, décrit par son nombre de degrés (steps) et de demi-tons (semitones).
 * Positif vers l'aigu, négatif vers le grave. La paire (degrés, demi-tons) suffit à garder
 * l'orthographe : une seconde majeure au-dessus de si donne do dièse, pas ré bémol.
 */
data class Interval(val steps: Int, val semitones: Int) {

    operator fun unaryMinus(): Interval = Interval(-steps, -semitones)

    companion object {
        val UNISON = Interval(0, 0)
        val MINOR_SECOND = Interval(1, 1)
        val MAJOR_SECOND = Interval(1, 2)
        val MINOR_THIRD = Interval(2, 3)
        val MAJOR_THIRD = Interval(2, 4)
        val PERFECT_FOURTH = Interval(3, 5)
        val PERFECT_FIFTH = Interval(4, 7)
        val MINOR_SIXTH = Interval(5, 8)
        val MAJOR_SIXTH = Interval(5, 9)
        val MINOR_SEVENTH = Interval(6, 10)
        val MAJOR_SEVENTH = Interval(6, 11)
        val OCTAVE = Interval(7, 12)

        /** Intervalle orienté qui mène de [from] à [to]. */
        fun between(from: Pitch, to: Pitch): Interval =
            Interval(to.diatonic - from.diatonic, to.midi - from.midi)
    }
}

/** Transpose une hauteur en conservant une orthographe correcte. */
fun Pitch.transpose(interval: Interval): Pitch {
    val target = Pitch.natural(diatonic + interval.steps)
    return target.copy(alter = midi + interval.semitones - target.midi)
}
