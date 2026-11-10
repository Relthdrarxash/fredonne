package fr.fredonne.core.music

import kotlin.math.abs

/**
 * Nombre rationnel exact, toujours réduit, avec un dénominateur strictement positif.
 *
 * Toutes les durées et tous les rapports de tempo passent par cette classe : jamais de
 * décimaux, donc jamais d'erreur d'arrondi dans le calcul des mesures.
 */
class Fraction private constructor(val numerator: Long, val denominator: Long) : Comparable<Fraction> {

    val isZero: Boolean get() = numerator == 0L

    operator fun plus(other: Fraction): Fraction = of(
        Math.addExact(
            Math.multiplyExact(numerator, other.denominator),
            Math.multiplyExact(other.numerator, denominator),
        ),
        Math.multiplyExact(denominator, other.denominator),
    )

    operator fun minus(other: Fraction): Fraction = this + (-other)

    operator fun unaryMinus(): Fraction = Fraction(-numerator, denominator)

    operator fun times(other: Fraction): Fraction = of(
        Math.multiplyExact(numerator, other.numerator),
        Math.multiplyExact(denominator, other.denominator),
    )

    operator fun times(factor: Int): Fraction = this * of(factor)

    operator fun div(other: Fraction): Fraction {
        require(other.numerator != 0L) { "Division par zéro" }
        return of(
            Math.multiplyExact(numerator, other.denominator),
            Math.multiplyExact(denominator, other.numerator),
        )
    }

    override fun compareTo(other: Fraction): Int =
        Math.multiplyExact(numerator, other.denominator)
            .compareTo(Math.multiplyExact(other.numerator, denominator))

    override fun equals(other: Any?): Boolean =
        other is Fraction && numerator == other.numerator && denominator == other.denominator

    override fun hashCode(): Int = 31 * numerator.hashCode() + denominator.hashCode()

    override fun toString(): String = if (denominator == 1L) "$numerator" else "$numerator/$denominator"

    fun toDouble(): Double = numerator.toDouble() / denominator

    companion object {
        val ZERO = Fraction(0, 1)
        val ONE = Fraction(1, 1)

        fun of(numerator: Long, denominator: Long): Fraction {
            require(denominator != 0L) { "Dénominateur nul" }
            var n = numerator
            var d = denominator
            if (d < 0) {
                n = -n
                d = -d
            }
            val g = gcd(abs(n), d)
            return Fraction(n / g, d / g)
        }

        fun of(numerator: Int, denominator: Int = 1): Fraction =
            of(numerator.toLong(), denominator.toLong())

        private fun gcd(a: Long, b: Long): Long {
            var x = a
            var y = b
            while (y != 0L) {
                val t = x % y
                x = y
                y = t
            }
            return x
        }
    }
}

/** Valeurs de note usuelles, en fraction de ronde (la ronde vaut 1). */
object Durations {
    val WHOLE: Fraction = Fraction.of(1)
    val HALF: Fraction = Fraction.of(1, 2)
    val QUARTER: Fraction = Fraction.of(1, 4)
    val EIGHTH: Fraction = Fraction.of(1, 8)
    val SIXTEENTH: Fraction = Fraction.of(1, 16)
    val THIRTY_SECOND: Fraction = Fraction.of(1, 32)

    /** Ajoute un point : la valeur est multipliée par 3/2. */
    fun dotted(duration: Fraction): Fraction = duration * Fraction.of(3, 2)
}
