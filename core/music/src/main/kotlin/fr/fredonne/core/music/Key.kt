package fr.fredonne.core.music

enum class Mode { MAJOR, MINOR }

/** Sens à choisir quand on transpose vers une autre tonalité. */
enum class Direction { UP, DOWN, NEAREST }

/** Tonalité, définie par sa tonique et son mode. */
data class Key(val tonicStep: Step, val tonicAlter: Int, val mode: Mode) {

    /** Nombre d'altérations à la clef : positif pour les dièses, négatif pour les bémols. */
    val fifths: Int
        get() {
            val major = MAJOR_FIFTHS.getValue(tonicStep) + 7 * tonicAlter
            return if (mode == Mode.MINOR) major - 3 else major
        }

    /** Vrai si l'armure compte au plus sept altérations. */
    val isPractical: Boolean get() = fifths in -7..7

    /** Altération diatonique portée par l'armure pour le degré [step] (-1, 0 ou +1). */
    fun diatonicAlter(step: Step): Int {
        val f = fifths.coerceIn(-7, 7)
        return when {
            f > 0 -> if (FIFTHS_LINE.indexOf(step) < f) 1 else 0
            f < 0 -> if (FIFTHS_LINE.asReversed().indexOf(step) < -f) -1 else 0
            else -> 0
        }
    }

    /** Même mode, tonique transposée. L'armure obtenue peut être peu pratique : voir [isPractical]. */
    fun transposed(interval: Interval): Key {
        val tonic = Pitch(tonicStep, 4, tonicAlter).transpose(interval)
        return Key(tonic.step, tonic.alter, mode)
    }

    companion object {
        private val MAJOR_FIFTHS = mapOf(
            Step.F to -1, Step.C to 0, Step.G to 1, Step.D to 2,
            Step.A to 3, Step.E to 4, Step.B to 5,
        )

        /** Ordre des tons sur le cycle des quintes, de fa (-1 altération) à si (+5). */
        private val FIFTHS_LINE = listOf(Step.F, Step.C, Step.G, Step.D, Step.A, Step.E, Step.B)

        fun fromFifths(fifths: Int, mode: Mode): Key {
            val majorFifths = if (mode == Mode.MINOR) fifths + 3 else fifths
            val position = majorFifths + 1
            return Key(
                tonicStep = FIFTHS_LINE[Math.floorMod(position, 7)],
                tonicAlter = Math.floorDiv(position, 7),
                mode = mode,
            )
        }

        /**
         * Intervalle qui mène de la tonique de [from] à la tonique cible.
         * Avec [Direction.NEAREST], on choisit le sens le plus court (le triton se fait vers l'aigu).
         */
        fun intervalBetweenTonics(
            from: Key,
            targetStep: Step,
            targetAlter: Int,
            direction: Direction = Direction.NEAREST,
        ): Interval {
            val a = Pitch(from.tonicStep, 4, from.tonicAlter)
            val b = Pitch(targetStep, 4, targetAlter)
            val rawSteps = b.diatonic - a.diatonic
            val rawSemitones = b.midi - a.midi
            val steps = Math.floorMod(rawSteps, 7)
            val octaves = (steps - rawSteps) / 7
            val semitones = rawSemitones + 12 * octaves
            val up = Interval(steps, semitones)
            val down = Interval(steps - 7, semitones - 12)
            return when (direction) {
                Direction.UP -> up
                Direction.DOWN -> down
                Direction.NEAREST -> if (semitones <= 6) up else down
            }
        }
    }
}
