package fr.fredonne.core.music

/** Nom de note naturelle, avec son demi-ton au-dessus de do. */
enum class Step(val semitone: Int) { C(0), D(2), E(4), F(5), G(7), A(9), B(11) }

/**
 * Hauteur écrite : nom de note, octave scientifique (do central = C4) et altération
 * en demi-tons (+1 dièse, -1 bémol, 0 naturel).
 *
 * Deux hauteurs enharmoniques (Fa dièse et Sol bémol) sont différentes ici, car
 * l'orthographe compte en notation, mais ont le même [midi].
 */
data class Pitch(val step: Step, val octave: Int, val alter: Int = 0) {

    /** Numéro MIDI (do central = 60). */
    val midi: Int get() = (octave + 1) * 12 + step.semitone + alter

    /** Position sur l'échelle des notes naturelles : sept par octave. */
    val diatonic: Int get() = octave * 7 + step.ordinal

    /** Altération raisonnable en lecture courante (pas au-delà du double dièse ou double bémol). */
    val isPractical: Boolean get() = alter in -2..2

    companion object {
        /** Note naturelle correspondant à une position diatonique. */
        fun natural(diatonic: Int): Pitch =
            Pitch(Step.entries[Math.floorMod(diatonic, 7)], Math.floorDiv(diatonic, 7))
    }
}
