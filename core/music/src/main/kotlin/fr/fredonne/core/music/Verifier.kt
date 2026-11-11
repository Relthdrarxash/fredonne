package fr.fredonne.core.music

/** Problème repéré par le vérificateur. Le texte affiché est choisi par l'interface, pas ici. */
sealed interface Issue {
    /** Numéro de mesure, à partir de 0. */
    val measure: Int
}

/** La mesure contient moins que son chiffrage : il manque [missing] (en fraction de ronde). */
data class MeasureTooShort(override val measure: Int, val missing: Fraction) : Issue

/** La mesure dépasse son chiffrage de [excess] (en fraction de ronde). */
data class MeasureTooLong(override val measure: Int, val excess: Fraction) : Issue

/** Une note est hors de la tessiture de l'instrument. [eventIndex] est sa position dans la mesure. */
data class OutOfRange(
    override val measure: Int,
    val eventIndex: Int,
    val pitch: Pitch,
    val tooLow: Boolean,
) : Issue

/**
 * Vérificateur en toile de fond. Il signale, il ne corrige jamais et ne bloque jamais :
 * c'est à l'interface de souligner les mesures ou notes concernées.
 */
object Verifier {

    /**
     * @param instrument si fourni, vérifie aussi la tessiture (en sons réels, comme la mélodie).
     * @param pickup si vrai, la première mesure peut être plus courte (anacrouse).
     */
    fun check(melody: Melody, instrument: Instrument? = null, pickup: Boolean = false): List<Issue> {
        val full = melody.time.measureLength
        val issues = mutableListOf<Issue>()

        melody.measures.forEachIndexed { index, measure ->
            val total = measure.events.fold(Fraction.ZERO) { acc, event -> acc + event.length }
            val allowedPickup = pickup && index == 0 && !total.isZero
            when {
                total > full -> issues += MeasureTooLong(index, total - full)
                total < full && !allowedPickup -> issues += MeasureTooShort(index, full - total)
            }

            if (instrument != null) {
                measure.events.forEachIndexed { eventIndex, event ->
                    if (event is Note) {
                        val midi = event.pitch.midi
                        when {
                            midi < instrument.soundingLow.midi ->
                                issues += OutOfRange(index, eventIndex, event.pitch, tooLow = true)
                            midi > instrument.soundingHigh.midi ->
                                issues += OutOfRange(index, eventIndex, event.pitch, tooLow = false)
                        }
                    }
                }
            }
        }
        return issues
    }
}
