package fr.fredonne.core.engraving

import fr.fredonne.core.music.Pitch
import fr.fredonne.core.music.TimeSignature
import fr.fredonne.core.music.Tuplet

/*
 * Résultat de la mise en page : ce que l'interface n'a plus qu'à dessiner.
 *
 * Unités. Horizontalement, tout est en « interlignes » (distance entre deux lignes de la portée),
 * à partir du bord gauche du système. Verticalement, on donne une position sur la portée :
 * 0 pour la ligne du bas, 1 pour le premier interligne, 8 pour la ligne du haut. Les positions
 * négatives ou supérieures à 8 sortent de la portée. L'interface choisit la taille d'un interligne
 * en pixels et convertit avec [yOf].
 */

/** Clef, définie par la note de la ligne du bas (en position diatonique). */
enum class Clef(private val bottomLine: Int) {
    /** Clef de sol : mi 4 sur la ligne du bas. */
    TREBLE(4 * 7 + 2),

    /** Clef de fa : sol 2 sur la ligne du bas. */
    BASS(2 * 7 + 4),
    ;

    /** Position d'une hauteur sur la portée (l'altération ne change pas la position). */
    fun positionOf(pitch: Pitch): Int = pitch.diatonic - bottomLine

    /** Note naturelle correspondant à une position, pour la saisie au doigt sur la portée. */
    fun naturalAt(position: Int): Pitch = Pitch.natural(bottomLine + position)
}

/** Distance verticale, en interlignes, entre la ligne du haut et une position (positive vers le bas). */
fun yOf(position: Int): Float = (8 - position) / 2f

/** Position la plus proche d'une hauteur donnée en interlignes sous la ligne du haut. */
fun positionAt(y: Float): Int = Math.round(8 - 2 * y)

enum class Accidental {
    DOUBLE_FLAT, FLAT, NATURAL, SHARP, DOUBLE_SHARP;

    companion object {
        fun of(alter: Int): Accidental = when {
            alter <= -2 -> DOUBLE_FLAT
            alter == -1 -> FLAT
            alter == 0 -> NATURAL
            alter == 1 -> SHARP
            else -> DOUBLE_SHARP
        }
    }
}

enum class Head { WHOLE, HALF, FILLED }

/** Repère d'un événement dans la mélodie : numéro de mesure et rang dans la mesure, à partir de 0. */
data class EventRef(val measure: Int, val index: Int)

sealed interface Symbol {
    /** Bord gauche de la tête de note ou du silence. */
    val x: Float
    val ref: EventRef
    val dots: Int
    val tuplet: Tuplet?
}

/**
 * @property stemUp sens de la hampe, ou `null` pour une ronde.
 * @property stemLength longueur de la hampe en interlignes.
 * @property accidental altération à dessiner devant la note, ou `null` si l'armure ou une note
 *   précédente de la mesure la donne déjà.
 * @property ledgerLines positions des lignes supplémentaires à tracer.
 */
data class NoteSymbol(
    override val x: Float,
    override val ref: EventRef,
    val position: Int,
    val head: Head,
    val stemUp: Boolean?,
    val stemLength: Float,
    val flags: Int,
    override val dots: Int,
    val accidental: Accidental?,
    val ledgerLines: List<Int>,
    val tiedToNext: Boolean,
    override val tuplet: Tuplet?,
) : Symbol

/** @property denominator figure du silence : 1 pour la pause, 2 demi-pause, 4 soupir, 8 demi-soupir... */
data class RestSymbol(
    override val x: Float,
    override val ref: EventRef,
    val denominator: Int,
    override val dots: Int,
    override val tuplet: Tuplet?,
) : Symbol

/** Crochet de groupe irrégulier, avec son chiffre (3 pour un triolet). */
data class TupletBracket(val x0: Float, val x1: Float, val number: Int, val above: Boolean)

/**
 * Une mesure dans son système.
 *
 * @property x0 début de la mesure (après la barre précédente ou l'armure).
 * @property x1 position de la barre de mesure finale.
 * @property cursorSlots positions possibles du curseur d'insertion : une avant chaque événement,
 *   plus une à la fin. Il y en a toujours une de plus que d'événements.
 */
data class MeasureLayout(
    val index: Int,
    val x0: Float,
    val x1: Float,
    val symbols: List<Symbol>,
    val brackets: List<TupletBracket>,
    val cursorSlots: List<Float>,
)

/** Altération de l'armure, placée sur la portée. */
data class KeyAccidental(val x: Float, val position: Int, val accidental: Accidental)

/**
 * Une ligne de musique.
 *
 * @property time chiffrage à dessiner en [timeX], seulement sur le premier système.
 */
data class SystemLayout(
    val index: Int,
    val width: Float,
    val clefX: Float,
    val keySignature: List<KeyAccidental>,
    val time: TimeSignature?,
    val timeX: Float,
    val measures: List<MeasureLayout>,
)

/** Point d'insertion désigné par un appui : avant l'événement [index] de la mesure [measure]. */
data class CursorPosition(val measure: Int, val index: Int)

data class ScoreLayout(val clef: Clef, val systems: List<SystemLayout>) {

    /** Symbole d'un événement, pour le surligner ou le faire défiler à l'écran. */
    fun find(ref: EventRef): Pair<SystemLayout, Symbol>? {
        for (system in systems) {
            for (measure in system.measures) {
                if (measure.index != ref.measure) continue
                val symbol = measure.symbols.firstOrNull { it.ref == ref } ?: return null
                return system to symbol
            }
        }
        return null
    }

    /** Point d'insertion le plus proche de l'abscisse [x] dans le système [system]. */
    fun cursorAt(system: Int, x: Float): CursorPosition? {
        val line = systems.getOrNull(system) ?: return null
        var best: CursorPosition? = null
        var bestDistance = Float.MAX_VALUE
        for (measure in line.measures) {
            measure.cursorSlots.forEachIndexed { index, slot ->
                val distance = kotlin.math.abs(slot - x)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = CursorPosition(measure.index, index)
                }
            }
        }
        return best
    }
}
