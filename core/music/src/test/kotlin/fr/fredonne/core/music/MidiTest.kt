package fr.fredonne.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MidiTest {

    private fun n(step: Step, octave: Int, d: Fraction, tuplet: Tuplet? = null, tied: Boolean = false) =
        Note(Pitch(step, octave), d, tuplet, tied)

    private val cMajor = Key(Step.C, 0, Mode.MAJOR)

    /** Événement relu dans la piste : instant en impulsions, octet de statut, données. */
    private data class Read(val tick: Long, val status: Int, val data: List<Int>)

    private class Parsed(val format: Int, val tracks: Int, val resolution: Int, val events: List<Read>)

    /** Relit le fichier produit, pour vérifier la structure sans dépendre d'un autre logiciel. */
    private fun parse(bytes: ByteArray): Parsed {
        fun u(i: Int) = bytes[i].toInt() and 0xFF
        fun int16(i: Int) = (u(i) shl 8) or u(i + 1)
        fun int32(i: Int) = (u(i) shl 24) or (u(i + 1) shl 16) or (u(i + 2) shl 8) or u(i + 3)

        assertEquals("MThd", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(6, int32(4))
        assertEquals("MTrk", String(bytes, 14, 4, Charsets.US_ASCII))
        val length = int32(18)
        assertEquals(bytes.size, 22 + length, "la longueur de piste annoncée doit couvrir tout le fichier")

        val events = ArrayList<Read>()
        var i = 22
        var tick = 0L
        while (i < bytes.size) {
            var delta = 0L
            while (true) {
                val b = u(i++)
                delta = (delta shl 7) or (b and 0x7F).toLong()
                if (b and 0x80 == 0) break
            }
            tick += delta
            val status = u(i++)
            when {
                status == 0xFF -> {
                    val type = u(i++)
                    val size = u(i++)
                    events += Read(tick, 0xFF, listOf(type) + (0 until size).map { u(i + it) })
                    i += size
                }
                status and 0xF0 == 0xC0 -> events += Read(tick, status, listOf(u(i++)))
                else -> {
                    events += Read(tick, status, listOf(u(i), u(i + 1)))
                    i += 2
                }
            }
        }
        return Parsed(int16(8), int16(10), int16(12), events)
    }

    @Test
    fun `gamme simple - en-tete, notes et fin de piste`() {
        val melody = Melody(
            cMajor,
            TimeSignature(4, 4),
            listOf(Measure(listOf(n(Step.C, 4, Durations.QUARTER), n(Step.D, 4, Durations.QUARTER), Rest(Durations.QUARTER), n(Step.E, 4, Durations.QUARTER)))),
        )
        val midi = parse(Midi.write(melody, tempo = Tempo.quarter(120), program = 73))
        assertEquals(0, midi.format)
        assertEquals(1, midi.tracks)
        assertEquals(480, midi.resolution)

        val on = midi.events.filter { it.status == 0x90 }
        val off = midi.events.filter { it.status == 0x80 }
        assertEquals(listOf(60, 62, 64), on.map { it.data[0] })
        assertEquals(listOf(0L, 480L, 1440L), on.map { it.tick }) // le silence décale le mi
        assertEquals(listOf(480L, 960L, 1920L), off.map { it.tick })

        // Tempo : noire = 120, soit 500 000 microsecondes par noire.
        assertEquals(listOf(0x51, 0x07, 0xA1, 0x20), midi.events.first { it.status == 0xFF && it.data[0] == 0x51 }.data)
        // Chiffrage 4/4 : numérateur 4, dénominateur 2 puissance 2.
        assertEquals(listOf(0x58, 4, 2, 24, 8), midi.events.first { it.status == 0xFF && it.data[0] == 0x58 }.data)
        assertEquals(listOf(73), midi.events.single { it.status == 0xC0 }.data)

        val end = midi.events.last()
        assertEquals(listOf(0x2F), end.data)
        assertEquals(1920L, end.tick)
    }

    @Test
    fun `un triolet tombe sur des impulsions entieres`() {
        val triplet = Tuplet(3, 2)
        val melody = Melody(
            cMajor,
            TimeSignature(1, 4),
            listOf(Measure(listOf(n(Step.C, 4, Durations.EIGHTH, triplet), n(Step.D, 4, Durations.EIGHTH, triplet), n(Step.E, 4, Durations.EIGHTH, triplet)))),
        )
        val midi = parse(Midi.write(melody))
        assertEquals(0, midi.resolution % 3)
        val third = midi.resolution / 3L
        assertEquals(listOf(0L, third, 2 * third), midi.events.filter { it.status == 0x90 }.map { it.tick })
        assertEquals(midi.resolution.toLong(), midi.events.last().tick)
    }

    @Test
    fun `deux notes liees sont jouees d une seule tenue`() {
        val melody = Melody(
            cMajor,
            TimeSignature(2, 4),
            listOf(
                Measure(listOf(n(Step.C, 4, Durations.QUARTER), n(Step.G, 4, Durations.QUARTER, tied = true))),
                Measure(listOf(n(Step.G, 4, Durations.QUARTER), n(Step.E, 4, Durations.QUARTER))),
            ),
        )
        val midi = parse(Midi.write(melody))
        val on = midi.events.filter { it.status == 0x90 }
        assertEquals(listOf(60, 67, 64), on.map { it.data[0] })
        val solOff = midi.events.first { it.status == 0x80 && it.data[0] == 67 }
        assertEquals(480L * 3, solOff.tick) // attaque à 480, tenue de deux noires
    }

    @Test
    fun `une liaison vers une autre hauteur ne fusionne pas`() {
        val melody = Melody(
            cMajor,
            TimeSignature(2, 4),
            listOf(Measure(listOf(n(Step.C, 4, Durations.QUARTER, tied = true), n(Step.D, 4, Durations.QUARTER)))),
        )
        val midi = parse(Midi.write(melody))
        assertEquals(listOf(60, 62), midi.events.filter { it.status == 0x90 }.map { it.data[0] })
        assertEquals(2, midi.events.count { it.status == 0x80 })
    }

    @Test
    fun `armure ecrite avec le bon signe`() {
        val flats = Melody(Key(Step.E, -1, Mode.MAJOR), TimeSignature(6, 8), listOf(Measure(listOf(n(Step.E, 4, Durations.dotted(Durations.HALF))))))
        val midi = parse(Midi.write(flats))
        // Trois bémols : -3 codé sur un octet, mode majeur.
        assertEquals(listOf(0x59, 0xFD, 0), midi.events.first { it.status == 0xFF && it.data[0] == 0x59 }.data)
        assertEquals(listOf(0x58, 6, 3, 24, 8), midi.events.first { it.status == 0xFF && it.data[0] == 0x58 }.data)
    }

    @Test
    fun `une melodie vide donne un fichier valide`() {
        val midi = parse(Midi.write(Melody(cMajor, TimeSignature(4, 4), emptyList())))
        assertTrue(midi.events.none { it.status == 0x90 })
        assertEquals(listOf(0x2F), midi.events.last().data)
    }

    @Test
    fun `les parametres hors limites sont refuses`() {
        val melody = Melody(cMajor, TimeSignature(4, 4), emptyList())
        assertFailsWith<IllegalArgumentException> { Midi.write(melody, program = 128) }
        assertFailsWith<IllegalArgumentException> { Midi.write(melody, velocity = 0) }
    }
}
