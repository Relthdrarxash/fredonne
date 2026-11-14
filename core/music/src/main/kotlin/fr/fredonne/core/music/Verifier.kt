package fr.fredonne.core.music

import kotlin.math.abs

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
 * Une note comporte une altération accidentelle hors armure sur un instrument diatonique à leviers
 * (ex. harpe celtique), nécessitant un basculement de levier en cours de morceau.
 */
data class LeverChangeRequired(
    override val measure: Int,
    val eventIndex: Int,
    val pitch: Pitch,
) : Issue

/**
 * Sur une harpe à pédales (double mouvement, 7 pédales couplées sur toutes les octaves),
 * signale qu'une pédale doit être déplacée sur un nouveau cran (`♭`, `♮` ou `♯`) pour cette note
 * et qu'elle restera dans cet état sur toutes les octaves jusqu'au prochain changement.
 */
data class PedalChangeRequired(
    override val measure: Int,
    val eventIndex: Int,
    val pitch: Pitch,
    val pedalLabel: String,
) : Issue

/**
 * Une note comporte un double-dièse ou double-bémol (`|alter| > 1`), impossible sur une harpe
 * à leviers ou à pédales sans ré-orthographe enharmonique.
 */
data class DoubleAlterationOnHarp(
    override val measure: Int,
    val eventIndex: Int,
    val pitch: Pitch,
) : Issue

/** Quintes parallèles consécutives par mouvement semblable entre deux voix (contrepoint de Fux / Tymoczko). */
data class ParallelFifths(
    override val measure: Int,
    val beatOffset: Fraction,
    val upperPitch: Pitch,
    val lowerPitch: Pitch,
) : Issue

/** Octaves ou unissons parallèles consécutifs par mouvement semblable entre deux voix. */
data class ParallelOctaves(
    override val measure: Int,
    val beatOffset: Fraction,
    val upperPitch: Pitch,
    val lowerPitch: Pitch,
) : Issue

/** Croisement de voix : la voix grave passe strictement au-dessus de la voix aiguë. */
data class VoiceCrossing(
    override val measure: Int,
    val beatOffset: Fraction,
    val upperPitch: Pitch,
    val lowerPitch: Pitch,
) : Issue

/** Dissonance sensorielle élevée (Plomp & Levelt 1965 / Sethares 1993) sur un temps fort. */
data class HighRoughnessInterval(
    override val measure: Int,
    val beatOffset: Fraction,
    val upperPitch: Pitch,
    val lowerPitch: Pitch,
    val roughness: Double,
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
        val pedalState = if (instrument?.pedalHarp == true) {
            Step.entries.associateWith { melody.key.diatonicAlter(it) }.toMutableMap()
        } else {
            null
        }

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
                        if ((instrument.diatonicOnly || instrument.pedalHarp) && abs(event.pitch.alter) > 1) {
                            issues += DoubleAlterationOnHarp(index, eventIndex, event.pitch)
                        } else if (instrument.diatonicOnly && event.pitch.alter != melody.key.diatonicAlter(event.pitch.step)) {
                            issues += LeverChangeRequired(index, eventIndex, event.pitch)
                        } else if (pedalState != null) {
                            val currentNotch = pedalState.getValue(event.pitch.step)
                            if (event.pitch.alter != currentNotch) {
                                pedalState[event.pitch.step] = event.pitch.alter
                                issues += PedalChangeRequired(
                                    measure = index,
                                    eventIndex = eventIndex,
                                    pitch = event.pitch,
                                    pedalLabel = HarpPedals.pedalLabel(event.pitch.step, event.pitch.alter),
                                )
                            }
                        }
                    }
                }
            }
        }
        return issues
    }

    /**
     * Vérifie le contrepoint à deux voix ([upper] voix supérieure, [lower] voix inférieure) :
     * - Règles individuelles de mesure et tessiture sur chaque voix
     * - Croisements de voix ([VoiceCrossing])
     * - Quintes et octaves parallèles par mouvement semblable ([ParallelFifths], [ParallelOctaves])
     * - Rugosité sensorielle de Plomp & Levelt / Sethares sur les attaques simultanées ([HighRoughnessInterval])
     */
    fun checkTwoVoices(
        upper: Melody,
        lower: Melody,
        upperInstrument: Instrument? = null,
        lowerInstrument: Instrument? = null,
        roughnessThreshold: Double = 0.42,
    ): List<Issue> {
        val issues = mutableListOf<Issue>()
        issues += check(upper, upperInstrument)
        issues += check(lower, lowerInstrument)

        data class VerticalSlice(
            val measure: Int,
            val offset: Fraction,
            val upper: Pitch,
            val lower: Pitch,
        )

        val slices = mutableListOf<VerticalSlice>()
        val measureCount = minOf(upper.measures.size, lower.measures.size)

        for (m in 0 until measureCount) {
            val uEvents = upper.measures[m].events
            val lEvents = lower.measures[m].events

            // Collecte des instants d'attaque dans la mesure
            val onsetsSet = mutableSetOf<Fraction>()
            var pos = Fraction.ZERO
            for (e in uEvents) {
                onsetsSet += pos
                pos += e.length
            }
            pos = Fraction.ZERO
            for (e in lEvents) {
                onsetsSet += pos
                pos += e.length
            }
            val onsets = onsetsSet.sorted()

            fun pitchAt(events: List<Event>, target: Fraction): Pitch? {
                var cur = Fraction.ZERO
                for (e in events) {
                    val next = cur + e.length
                    if (target >= cur && target < next) {
                        return (e as? Note)?.pitch
                    }
                    cur = next
                }
                return null
            }

            for (onset in onsets) {
                val uPitch = pitchAt(uEvents, onset) ?: continue
                val lPitch = pitchAt(lEvents, onset) ?: continue
                slices += VerticalSlice(m, onset, uPitch, lPitch)
            }
        }

        var prevSlice: VerticalSlice? = null
        for (slice in slices) {
            val uMidi = slice.upper.midi
            val lMidi = slice.lower.midi

            if (lMidi > uMidi) {
                issues += VoiceCrossing(slice.measure, slice.offset, slice.upper, slice.lower)
            }

            val roughness = MusicTheory.sensoryDissonance(uMidi, lMidi)
            if (roughness >= roughnessThreshold) {
                issues += HighRoughnessInterval(
                    measure = slice.measure,
                    beatOffset = slice.offset,
                    upperPitch = slice.upper,
                    lowerPitch = slice.lower,
                    roughness = roughness,
                )
            }

            val prev = prevSlice
            if (prev != null) {
                val prevUMidi = prev.upper.midi
                val prevLMidi = prev.lower.midi
                val dU = uMidi - prevUMidi
                val dL = lMidi - prevLMidi
                // Mouvement semblable non stationnaire des deux voix
                if (dU != 0 && dL != 0 && (dU > 0 == dL > 0)) {
                    val prevIntervalMod = abs(prevUMidi - prevLMidi) % 12
                    val curIntervalMod = abs(uMidi - lMidi) % 12
                    if (prevIntervalMod == 7 && curIntervalMod == 7) {
                        issues += ParallelFifths(slice.measure, slice.offset, slice.upper, slice.lower)
                    } else if (prevIntervalMod == 0 && curIntervalMod == 0) {
                        issues += ParallelOctaves(slice.measure, slice.offset, slice.upper, slice.lower)
                    }
                }
            }
            prevSlice = slice
        }

        return issues
    }
}
