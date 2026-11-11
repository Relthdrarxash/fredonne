package fr.fredonne.core.music

/**
 * Figure de note ou de silence : une valeur de base (1 = ronde, 2 = blanche, 4 = noire,
 * 8 = croche...) et un nombre de points.
 */
data class NoteFigure(val denominator: Int, val dots: Int = 0) {

    /** Valeur écrite correspondante, en fraction de ronde. */
    val value: Fraction
        get() = Fraction.of(1, denominator) * Fraction.of((1 shl (dots + 1)) - 1, 1 shl dots)

    /** Nombre de crochets sur la hampe : 1 pour la croche, 2 pour la double croche... */
    val flags: Int
        get() = when (denominator) {
            8 -> 1
            16 -> 2
            32 -> 3
            64 -> 4
            else -> 0
        }

    companion object {
        private val DENOMINATORS = listOf(1, 2, 4, 8, 16, 32, 64)

        /** Figure exacte pour une valeur écrite, ou `null` si elle ne s'écrit pas d'un seul signe (5/16). */
        fun of(written: Fraction): NoteFigure? {
            for (denominator in DENOMINATORS) {
                for (dots in 0..2) {
                    val figure = NoteFigure(denominator, dots)
                    if (figure.value == written) return figure
                }
            }
            return null
        }

        /** Figure sans point la plus proche par défaut, pour afficher quand même une valeur inhabituelle. */
        fun closest(written: Fraction): NoteFigure =
            of(written) ?: NoteFigure(DENOMINATORS.firstOrNull { Fraction.of(1, it) <= written } ?: 64)
    }
}

/**
 * Altération que l'armure impose à une note : +1 pour fa en sol majeur, -1 pour si en fa majeur.
 * Les armures théoriques au-delà de sept altérations donnent des doubles dièses ou doubles bémols.
 */
fun Key.alterOf(step: Step): Int {
    val sharpsOrder = listOf(Step.F, Step.C, Step.G, Step.D, Step.A, Step.E, Step.B)
    val count = fifths
    return if (count >= 0) {
        (count - sharpsOrder.indexOf(step) + 6) / 7
    } else {
        -((-count - sharpsOrder.reversed().indexOf(step) + 6) / 7)
    }
}
