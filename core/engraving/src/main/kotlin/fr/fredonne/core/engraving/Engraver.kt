package fr.fredonne.core.engraving

import fr.fredonne.core.music.Event
import fr.fredonne.core.music.Key
import fr.fredonne.core.music.Melody
import fr.fredonne.core.music.Note
import fr.fredonne.core.music.NoteFigure
import fr.fredonne.core.music.Rest
import fr.fredonne.core.music.Step
import fr.fredonne.core.music.alterOf
import kotlin.math.abs
import kotlin.math.ln

/**
 * Met en page une mélodie à une voix : place les notes sur la portée, choisit les hampes et les
 * altérations à afficher, espace les mesures et les répartit en lignes d'une largeur donnée.
 *
 * Kotlin pur, sans dessin : le résultat ([ScoreLayout]) est testé sans téléphone, puis dessiné tel
 * quel par l'interface. Première version : pas de ligatures (les croches gardent leurs crochets),
 * une seule voix, une seule clef.
 */
object Engraver {

    // Toutes les mesures sont en interlignes.
    private const val CLEF_X = 0.5f
    private const val CLEF_WIDTH = 3.5f
    private const val KEY_ACCIDENTAL_WIDTH = 1f
    private const val KEY_PADDING = 0.5f
    private const val TIME_WIDTH = 2.5f
    private const val MEASURE_PADDING = 1f
    private const val EMPTY_MEASURE_WIDTH = 4f
    private const val ACCIDENTAL_WIDTH = 1f
    private const val DOT_WIDTH = 0.5f
    private const val HEAD_WIDTH = 1.2f
    private const val STEM_LENGTH = 3.5f
    private const val MAX_STRETCH = 1.8f

    /** Position de la ligne du milieu : en dessous, la hampe monte ; à partir d'elle, elle descend. */
    private const val MIDDLE_LINE = 4

    private val SHARPS_ORDER = listOf(Step.F, Step.C, Step.G, Step.D, Step.A, Step.E, Step.B)

    /** Positions des dièses et des bémols de l'armure en clef de sol ; la clef de fa est deux positions plus bas. */
    private val SHARP_POSITIONS = listOf(8, 5, 9, 6, 3, 7, 4)
    private val FLAT_POSITIONS = listOf(4, 7, 3, 6, 2, 5, 1)

    /**
     * @param melody mélodie telle qu'elle doit être lue (déjà transposée pour l'instrument s'il y a lieu).
     * @param width largeur disponible pour un système, en interlignes.
     */
    fun layout(melody: Melody, clef: Clef = Clef.TREBLE, width: Float = 40f): ScoreLayout {
        val keySignature = keySignature(melody.key, clef)
        val keyWidth = if (keySignature.isEmpty()) 0f else keySignature.size * KEY_ACCIDENTAL_WIDTH + KEY_PADDING
        val prefix = CLEF_X + CLEF_WIDTH + keyWidth

        val drafts = draftMeasures(melody, clef)
        val systems = ArrayList<SystemLayout>()
        var next = 0
        do {
            val first = systems.isEmpty()
            val start = prefix + if (first) TIME_WIDTH else 0f
            val available = (width - start).coerceAtLeast(EMPTY_MEASURE_WIDTH)

            // Autant de mesures que la ligne en contient, et au moins une.
            var end = next
            var natural = 0f
            while (end < drafts.size && (end == next || natural + drafts[end].width <= available)) {
                natural += drafts[end].width
                end++
            }
            val last = end >= drafts.size
            // On étire pour remplir la ligne, sauf la dernière ; on resserre si une mesure déborde.
            val factor = when {
                natural <= 0f -> 1f
                natural > available -> available / natural
                last -> 1f
                else -> (available / natural).coerceAtMost(MAX_STRETCH)
            }

            var x = start
            val measures = ArrayList<MeasureLayout>()
            for (i in next until end) {
                val placed = drafts[i].place(x, factor)
                measures += placed
                x = placed.x1
            }
            systems += SystemLayout(
                index = systems.size,
                width = if (measures.isEmpty()) start else x,
                clefX = CLEF_X,
                keySignature = keySignature,
                time = if (first) melody.time else null,
                timeX = prefix,
                measures = measures,
            )
            next = end
        } while (next < drafts.size)
        return ScoreLayout(clef, systems)
    }

    /** Altérations de l'armure, dans l'ordre d'écriture, placées après la clef. */
    internal fun keySignature(key: Key, clef: Clef): List<KeyAccidental> {
        val count = key.fifths.coerceIn(-7, 7)
        val shift = if (clef == Clef.BASS) -2 else 0
        val start = CLEF_X + CLEF_WIDTH
        return List(abs(count)) { i ->
            KeyAccidental(
                x = start + i * KEY_ACCIDENTAL_WIDTH,
                position = (if (count > 0) SHARP_POSITIONS[i] else FLAT_POSITIONS[i]) + shift,
                accidental = if (count > 0) Accidental.SHARP else Accidental.FLAT,
            )
        }
    }

    // ------------------------------------------------------------ Mesures

    /** Mesure mise en page à partir de zéro, à sa largeur naturelle, avant répartition en lignes. */
    private class Draft(
        val index: Int,
        val width: Float,
        val symbols: List<Symbol>,
        val slots: List<Float>,
    ) {
        fun place(x0: Float, factor: Float): MeasureLayout {
            fun at(x: Float) = x0 + x * factor
            val placed = symbols.map { symbol ->
                when (symbol) {
                    is NoteSymbol -> symbol.copy(x = at(symbol.x))
                    is RestSymbol -> symbol.copy(x = at(symbol.x))
                }
            }
            return MeasureLayout(
                index = index,
                x0 = x0,
                x1 = at(width),
                symbols = placed,
                brackets = brackets(placed),
                cursorSlots = slots.map(::at),
            )
        }
    }

    private fun draftMeasures(melody: Melody, clef: Clef): List<Draft> {
        // Une mélodie vide garde une mesure vide : il faut un endroit où poser le curseur.
        val measures = melody.measures.ifEmpty { listOf(fr.fredonne.core.music.Measure(emptyList())) }
        var tiedFrom: Note? = null

        return measures.mapIndexed { measureIndex, measure ->
            // Altérations en vigueur dans la mesure, par position sur la portée.
            val current = HashMap<Int, Int>()
            val symbols = ArrayList<Symbol>()
            val slots = ArrayList<Float>()
            var x = MEASURE_PADDING

            measure.events.forEachIndexed { eventIndex, event ->
                val ref = EventRef(measureIndex, eventIndex)
                val figure = NoteFigure.closest(event.written)
                slots += x
                when (event) {
                    is Note -> {
                        val position = clef.positionOf(event.pitch)
                        val inForce = current[position] ?: melody.key.alterOf(event.pitch.step)
                        val continuesTie = tiedFrom?.pitch == event.pitch
                        val accidental = if (event.pitch.alter != inForce && !continuesTie) {
                            Accidental.of(event.pitch.alter)
                        } else {
                            null
                        }
                        current[position] = event.pitch.alter
                        if (accidental != null) x += ACCIDENTAL_WIDTH
                        symbols += noteSymbol(x, ref, event, position, figure, accidental)
                        tiedFrom = event.takeIf { it.tiedToNext }
                    }
                    is Rest -> {
                        symbols += RestSymbol(x, ref, figure.denominator, figure.dots, event.tuplet)
                        tiedFrom = null
                    }
                }
                x += spaceFor(event) + figure.dots * DOT_WIDTH
            }
            slots += x
            Draft(measureIndex, if (measure.events.isEmpty()) EMPTY_MEASURE_WIDTH else x, symbols, slots)
        }
    }

    private fun noteSymbol(
        x: Float,
        ref: EventRef,
        note: Note,
        position: Int,
        figure: NoteFigure,
        accidental: Accidental?,
    ): NoteSymbol {
        val head = when (figure.denominator) {
            1 -> Head.WHOLE
            2 -> Head.HALF
            else -> Head.FILLED
        }
        // Une note très éloignée de la portée voit sa hampe allongée jusqu'à la ligne du milieu.
        val distance = abs(position - MIDDLE_LINE) / 2f
        return NoteSymbol(
            x = x,
            ref = ref,
            position = position,
            head = head,
            stemUp = if (head == Head.WHOLE) null else position < MIDDLE_LINE,
            stemLength = if (head == Head.WHOLE) 0f else maxOf(STEM_LENGTH, distance),
            flags = figure.flags,
            dots = figure.dots,
            accidental = accidental,
            ledgerLines = ledgerLines(position),
            tiedToNext = note.tiedToNext,
            tuplet = note.tuplet,
        )
    }

    /** Lignes supplémentaires nécessaires pour une note hors de la portée. */
    internal fun ledgerLines(position: Int): List<Int> = when {
        position <= -2 -> (-2 downTo position step 2).toList()
        position >= 10 -> (10..position step 2).toList()
        else -> emptyList()
    }

    /**
     * Place accordée à un événement selon sa durée : une note deux fois plus longue reçoit un peu
     * plus de place, pas le double (espacement logarithmique, comme en gravure traditionnelle).
     */
    internal fun spaceFor(event: Event): Float {
        val eighths = event.length.toDouble() * 8
        val space = 2.5 + ln(eighths) / ln(2.0)
        return space.toFloat().coerceIn(1.8f, 6f)
    }

    /** Crochets des groupes irréguliers : notes voisines de même division, par paquets de la taille du groupe. */
    private fun brackets(symbols: List<Symbol>): List<TupletBracket> {
        val result = ArrayList<TupletBracket>()
        var i = 0
        while (i < symbols.size) {
            val tuplet = symbols[i].tuplet
            if (tuplet == null) {
                i++
                continue
            }
            var j = i
            while (j < symbols.size && symbols[j].tuplet == tuplet && j - i < tuplet.actual) j++
            val group = symbols.subList(i, j)
            val notes = group.filterIsInstance<NoteSymbol>()
            // Du côté des hampes : au-dessus si la majorité monte.
            val above = notes.count { it.stemUp != false } >= notes.count { it.stemUp == false }
            result += TupletBracket(group.first().x - 0.3f, group.last().x + HEAD_WIDTH + 0.3f, tuplet.actual, above)
            i = j
        }
        return result
    }
}
