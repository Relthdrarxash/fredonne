package fr.fredonne.core.music

/** Chiffrage de mesure : [beats] temps de valeur 1/[unit] de ronde (3/4, 6/8, 5/16...). */
data class TimeSignature(val beats: Int, val unit: Int) {
    init {
        require(beats > 0) { "Nombre de temps invalide : $beats" }
        require(unit in setOf(1, 2, 4, 8, 16, 32, 64)) { "Unité de temps invalide : $unit" }
    }

    /** Durée d'une mesure complète, en fraction de ronde. */
    val measureLength: Fraction get() = Fraction.of(beats, unit)
}

/** Division irrégulière : [actual] notes jouées à la place de [normal] (triolet = 3 pour 2). */
data class Tuplet(val actual: Int, val normal: Int) {
    init {
        require(actual > 0 && normal > 0) { "Tuplet invalide : $actual pour $normal" }
    }

    val factor: Fraction get() = Fraction.of(normal, actual)
}

sealed interface Event {
    /** Valeur écrite, en fraction de ronde, sans tenir compte du tuplet. */
    val written: Fraction
    val tuplet: Tuplet?

    /** Durée réelle dans la mesure. */
    val length: Fraction get() = tuplet?.let { written * it.factor } ?: written
}

/** [tiedToNext] : la note est liée à la suivante de même hauteur (une seule attaque à l'écoute). */
data class Note(
    val pitch: Pitch,
    override val written: Fraction,
    override val tuplet: Tuplet? = null,
    val tiedToNext: Boolean = false,
) : Event

data class Rest(
    override val written: Fraction,
    override val tuplet: Tuplet? = null,
) : Event

data class Measure(val events: List<Event>)

/**
 * Mélodie à une voix. Les hauteurs sont en sons réels (ton de concert) ; la transposition
 * propre à un instrument ne s'applique qu'à l'affichage et à l'export.
 */
data class Melody(val key: Key, val time: TimeSignature, val measures: List<Measure>)

/** Transpose toute la mélodie : notes et armure. */
fun Melody.transposed(interval: Interval): Melody = copy(
    key = key.transposed(interval),
    measures = measures.map { measure ->
        Measure(measure.events.map { event ->
            if (event is Note) event.copy(pitch = event.pitch.transpose(interval)) else event
        })
    },
)
