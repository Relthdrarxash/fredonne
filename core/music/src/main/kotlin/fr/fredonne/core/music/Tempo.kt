package fr.fredonne.core.music

/**
 * Indication métronomique : [perMinute] fois la valeur [beatUnit] par minute.
 * « Noire = 60 » s'écrit `Tempo(Durations.QUARTER, Fraction.of(60))`.
 *
 * Le nombre par minute est une fraction exacte : après une modulation métrique, un tempo
 * comme 67,5 reste 135/2, sans arrondi qui s'accumulerait d'un changement à l'autre.
 */
data class Tempo(val beatUnit: Fraction, val perMinute: Fraction) {
    init {
        require(beatUnit > Fraction.ZERO) { "Valeur de battue invalide : $beatUnit" }
        require(perMinute > Fraction.ZERO) { "Tempo invalide : $perMinute" }
    }

    /** Nombre de rondes par minute : la vitesse réelle, indépendante de la valeur de battue. */
    val wholesPerMinute: Fraction get() = beatUnit * perMinute

    /** Même vitesse, exprimée avec une autre valeur de battue (noire = 60 donne croche = 120). */
    fun expressedIn(unit: Fraction): Tempo = Tempo(unit, wholesPerMinute / unit)

    /** Durée réelle de [value] (fraction de ronde), en fraction de minute. */
    fun minutesFor(value: Fraction): Fraction = value / wholesPerMinute

    /**
     * Tempo obtenu après la modulation métrique [modulation]. La valeur de battue est conservée :
     * noire = 60 avec « triolet de croches = croche » donne noire = 90.
     */
    fun modulated(modulation: MetricModulation): Tempo =
        Tempo(beatUnit, perMinute * modulation.ratio)

    companion object {
        fun quarter(perMinute: Int): Tempo = Tempo(Durations.QUARTER, Fraction.of(perMinute))
    }
}

/**
 * Modulation métrique : la valeur [oldValue] de l'ancien tempo dure autant que la valeur
 * [newValue] du nouveau. Les deux sont des durées réelles en fraction de ronde, tuplet compris
 * (une croche de triolet vaut 1/12).
 */
data class MetricModulation(val oldValue: Fraction, val newValue: Fraction) {
    init {
        require(oldValue > Fraction.ZERO && newValue > Fraction.ZERO) { "Équivalence invalide" }
    }

    /** Rapport du nouveau tempo à l'ancien : 3/2 quand le triolet de croches devient la croche. */
    val ratio: Fraction get() = newValue / oldValue

    companion object {
        /** Équivalence écrite avec des événements : leur durée réelle tient compte du tuplet. */
        fun of(old: Event, new: Event): MetricModulation = MetricModulation(old.length, new.length)
    }
}

/** Enchaîne plusieurs modulations à partir d'un tempo de départ et renvoie tous les tempos traversés. */
fun Tempo.through(modulations: List<MetricModulation>): List<Tempo> =
    modulations.runningFold(this) { tempo, modulation -> tempo.modulated(modulation) }
