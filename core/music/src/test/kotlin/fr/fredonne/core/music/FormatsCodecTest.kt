package fr.fredonne.core.music

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormatsCodecTest {

    private val q = Durations.QUARTER
    private val e = Durations.EIGHTH

    @Test
    fun `MusicXML export et import conservent la melodie, les tuplets et l instrument transpositeur`() {
        val triplet = Tuplet(3, 2)
        val original = Melody(
            key = Key(Step.F, 0, Mode.MAJOR),
            time = TimeSignature(4, 4),
            measures = listOf(
                Measure(
                    listOf(
                        Note(Pitch(Step.F, 4), Durations.dotted(q)),
                        Note(Pitch(Step.G, 4), e),
                        Note(Pitch(Step.A, 4), e, triplet),
                        Note(Pitch(Step.B, 4, -1), e, triplet),
                        Note(Pitch(Step.C, 5), e, triplet),
                        Rest(q),
                    ),
                ),
            ),
        )

        val xml = MusicXmlCodec.export(original, instrument = Instruments.CLARINET_BB, title = "Test Clarinette")
        assertTrue(xml.contains("<score-partwise version=\"4.0\">"))
        assertTrue(xml.contains("<transpose>"))
        assertTrue(xml.contains("<diatonic>-1</diatonic>"))
        assertTrue(xml.contains("<chromatic>-2</chromatic>"))

        val (importedMelody, importedInstrument) = MusicXmlCodec.import(xml)
        assertEquals(Instruments.CLARINET_BB, importedInstrument)
        assertEquals(original.key, importedMelody.key)
        assertEquals(original.time, importedMelody.time)
        assertEquals(original.measures, importedMelody.measures)
    }

    @Test
    fun `MIDI SMF export et import reconstruisent les hauteurs, durées, armure et tempo`() {
        val original = Melody(
            key = Key(Step.G, 0, Mode.MAJOR),
            time = TimeSignature(4, 4),
            measures = listOf(
                Measure(
                    listOf(
                        Note(Pitch(Step.G, 4), q),
                        Note(Pitch(Step.A, 4), q),
                        Note(Pitch(Step.B, 4), q),
                        Note(Pitch(Step.F, 4, 1), q),
                    ),
                ),
            ),
        )

        val midiBytes = MidiCodec.export(original, bpm = 108, instrument = Instruments.FLUTE)
        assertEquals('M'.code.toByte(), midiBytes[0])
        assertEquals('T'.code.toByte(), midiBytes[1])
        assertEquals('h'.code.toByte(), midiBytes[2])
        assertEquals('d'.code.toByte(), midiBytes[3])

        val (importedMelody, importedBpm) = MidiCodec.import(midiBytes)
        assertEquals(108, importedBpm)
        assertEquals(original.key, importedMelody.key)
        assertEquals(original.time, importedMelody.time)
        assertEquals(original.measures, importedMelody.measures)
    }

    @Test
    fun `ScorePdfGenerator genere un document PDF 1_4 vectoriel valide sans filigrane`() {
        val melody = Melody(
            key = Key(Step.F, 0, Mode.MAJOR),
            time = TimeSignature(4, 4),
            measures = listOf(
                Measure(
                    listOf(
                        Note(Pitch(Step.F, 4), Durations.QUARTER),
                        Note(Pitch(Step.G, 4), Durations.QUARTER),
                        Note(Pitch(Step.A, 4), Durations.QUARTER),
                        Note(Pitch(Step.B, 4, -1), Durations.QUARTER),
                    ),
                ),
                Measure(
                    listOf(
                        Note(Pitch(Step.C, 5), Durations.dotted(Durations.QUARTER)),
                        Note(Pitch(Step.A, 4), Durations.EIGHTH),
                        Note(Pitch(Step.F, 4), Durations.HALF),
                    ),
                ),
                Measure(
                    listOf(
                        Note(Pitch(Step.G, 4), Durations.EIGHTH),
                        Note(Pitch(Step.A, 4), Durations.EIGHTH),
                        Note(Pitch(Step.B, 4, -1), Durations.QUARTER),
                        Note(Pitch(Step.A, 4), Durations.QUARTER),
                        Rest(Durations.QUARTER),
                    ),
                ),
                Measure(
                    listOf(
                        Note(Pitch(Step.G, 4), Durations.HALF),
                        Note(Pitch(Step.F, 4), Durations.HALF),
                    ),
                ),
            ),
        )
        val pdfBytes = ScorePdfGenerator.generate(
            melody = melody,
            instrument = Instruments.CLARINET_BB,
            tempoBpm = 104,
            title = "Air d'automne en Fa",
        )
        val pdfText = pdfBytes.decodeToString()
        assertTrue(pdfText.startsWith("%PDF-1.4\n"))
        assertTrue(pdfText.endsWith("%%EOF\n"))
        assertTrue(pdfText.contains("Clarinette \\(Sib\\)"))
        assertTrue(pdfText.contains("FREDONNE"))
    }

    @Test
    fun `ShareCodec et QrCodeEncoder encodent et decodent Au clair de la lune avec compression de mesures`() {
        val f4 = Pitch(Step.F, 4)
        val g4 = Pitch(Step.G, 4)
        val a4 = Pitch(Step.A, 4)
        val d4 = Pitch(Step.D, 4)
        val e4 = Pitch(Step.E, 4)
        val c4 = Pitch(Step.C, 4)
        val h = Durations.HALF
        val w = Durations.WHOLE
        val phraseA = listOf(
            Measure(listOf(Note(f4, q), Note(f4, q), Note(f4, q), Note(g4, q))),
            Measure(listOf(Note(a4, h), Note(g4, h))),
            Measure(listOf(Note(f4, q), Note(a4, q), Note(g4, q), Note(g4, q))),
            Measure(listOf(Note(f4, w))),
        )
        val phraseB = listOf(
            Measure(listOf(Note(g4, q), Note(g4, q), Note(g4, q), Note(g4, q))),
            Measure(listOf(Note(d4, h), Note(d4, h))),
            Measure(listOf(Note(g4, q), Note(f4, q), Note(e4, q), Note(d4, q))),
            Measure(listOf(Note(c4, w))),
        )
        val auClairDeLaLune = Melody(
            key = Key(Step.F, 0, Mode.MAJOR),
            time = TimeSignature(4, 4),
            measures = phraseA + phraseA + phraseB + phraseA,
        )

        val code = ShareCodec.encode(auClairDeLaLune, Instruments.CLARINET_BB, tempoBpm = 96)
        assertTrue(code.startsWith("FR1|"))
        // Vérifie que les mesures répétées utilisent les références =0, =1, =2, =3
        assertTrue(code.contains("=0"))

        val decoded = ShareCodec.decode(code)
        assertEquals(auClairDeLaLune, decoded.melody)
        assertEquals(Instruments.CLARINET_BB, decoded.instrument)
        assertEquals(96, decoded.tempoBpm)

        val qr = QrCodeEncoder.encode(code)
        assertTrue(qr.size >= 21)
        // Vérifie les 3 coins des motifs de détection (Finder patterns)
        assertTrue(qr[0, 0] && qr[6, 0] && qr[0, 6] && qr[3, 3])
        assertTrue(qr[qr.size - 1, 0] && qr[0, qr.size - 1])
    }
}
