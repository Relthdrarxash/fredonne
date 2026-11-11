package fr.fredonne.core.music

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicXmlTest {

    private fun n(step: Step, octave: Int, d: Fraction, alter: Int = 0, tuplet: Tuplet? = null, tied: Boolean = false) =
        Note(Pitch(step, octave, alter), d, tuplet, tied)

    private val gMajor = Key(Step.G, 0, Mode.MAJOR)

    /** Sol majeur à 3/4 : valeurs simples, point, silence, triolet, liaison par-dessus la barre. */
    private val melody = Melody(
        gMajor,
        TimeSignature(3, 4),
        listOf(
            Measure(listOf(n(Step.G, 4, Durations.QUARTER), n(Step.F, 4, Durations.EIGHTH, alter = 1), Rest(Durations.EIGHTH), n(Step.B, 4, Durations.QUARTER))),
            Measure(
                listOf(
                    n(Step.D, 5, Durations.EIGHTH, tuplet = Tuplet(3, 2)),
                    n(Step.C, 5, Durations.EIGHTH, tuplet = Tuplet(3, 2)),
                    n(Step.B, 4, Durations.EIGHTH, tuplet = Tuplet(3, 2)),
                    n(Step.A, 4, Durations.HALF, tied = true),
                ),
            ),
            Measure(listOf(n(Step.A, 4, Durations.dotted(Durations.QUARTER)), n(Step.G, 4, Durations.dotted(Durations.QUARTER)))),
        ),
    )

    @Test
    fun `ecrire puis relire redonne la meme melodie`() {
        val xml = MusicXml.write(melody, title = "Essai")
        val read = MusicXml.read(xml)
        assertEquals(melody, read.melody)
        assertEquals("Essai", read.title)
        assertEquals(Interval.UNISON, read.transposition)
    }

    @Test
    fun `le fichier contient armure, chiffrage, divisions et figures`() {
        val xml = MusicXml.write(melody)
        assertContains(xml, "<score-partwise version=\"4.0\">")
        assertContains(xml, "<fifths>1</fifths>")
        assertContains(xml, "<beats>3</beats>")
        assertContains(xml, "<beat-type>4</beat-type>")
        // Croche de triolet = 1/3 de noire : il faut 6 divisions par noire (lcm de 2 et 3).
        assertContains(xml, "<divisions>6</divisions>")
        assertContains(xml, "<alter>1</alter>")
        assertContains(xml, "<actual-notes>3</actual-notes>")
        assertContains(xml, "<type>quarter</type>\n        <dot/>")
        assertContains(xml, "<tie type=\"start\"/>")
        assertContains(xml, "<tie type=\"stop\"/>")
        assertFalse("<transpose>" in xml)
    }

    @Test
    fun `le titre est protege`() {
        val xml = MusicXml.write(melody, title = "Dupont & fils <1>")
        assertContains(xml, "Dupont &amp; fils &lt;1&gt;")
        assertEquals("Dupont & fils <1>", MusicXml.read(xml).title)
    }

    @Test
    fun `partie de clarinette en si bemol - ecrite un ton plus haut, avec l element transpose`() {
        // Si bémol majeur en sons réels : la clarinette lit do majeur.
        val concert = Melody(
            Key(Step.B, -1, Mode.MAJOR),
            TimeSignature(2, 4),
            listOf(Measure(listOf(n(Step.B, 3, Durations.QUARTER, alter = -1), n(Step.D, 4, Durations.QUARTER)))),
        )
        val xml = MusicXml.write(concert, instrument = Instruments.CLARINET_BB)
        assertContains(xml, "<fifths>0</fifths>")
        assertContains(xml, "<step>C</step>")
        assertContains(xml, "<step>E</step>")
        assertContains(xml, "<diatonic>-1</diatonic>")
        assertContains(xml, "<chromatic>-2</chromatic>")
        assertFalse("<octave-change>" in xml)

        val read = MusicXml.read(xml)
        assertEquals(concert, read.melody)
        assertEquals(Instruments.CLARINET_BB.transposition, read.transposition)
    }

    @Test
    fun `saxophone tenor - la transposition depasse l octave`() {
        val concert = Melody(
            Key(Step.C, 0, Mode.MAJOR),
            TimeSignature(4, 4),
            listOf(Measure(listOf(n(Step.C, 3, Durations.WHOLE)))),
        )
        val xml = MusicXml.write(concert, instrument = Instruments.SAX_TENOR)
        assertContains(xml, "<diatonic>-1</diatonic>")
        assertContains(xml, "<chromatic>-2</chromatic>")
        assertContains(xml, "<octave-change>-1</octave-change>")
        assertEquals(concert, MusicXml.read(xml).melody)
    }

    @Test
    fun `instrument grave - clef de fa`() {
        val concert = Melody(Key(Step.C, 0, Mode.MAJOR), TimeSignature(4, 4), listOf(Measure(listOf(n(Step.C, 3, Durations.WHOLE)))))
        assertContains(MusicXml.write(concert, instrument = Instruments.TROMBONE), "<sign>F</sign>")
        assertContains(MusicXml.write(concert, instrument = Instruments.FLUTE), "<sign>G</sign>")
    }

    @Test
    fun `une valeur sans figure simple passe par la duree`() {
        // 5/16 ne s'écrit pas d'un seul signe : pas d'élément type, mais la durée suffit à relire.
        val odd = Melody(
            Key(Step.C, 0, Mode.MAJOR),
            TimeSignature(5, 16),
            listOf(Measure(listOf(n(Step.E, 4, Fraction.of(5, 16))))),
        )
        val xml = MusicXml.write(odd)
        assertFalse("<type>" in xml)
        assertEquals(odd, MusicXml.read(xml).melody)
    }

    @Test
    fun `lecture d un fichier d un autre logiciel - seconde voix, accords et notes d agrement ignores`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 3.1 Partwise//EN" "http://www.musicxml.org/dtds/partwise.dtd">
            <score-partwise version="3.1">
              <!-- exporté par un autre logiciel -->
              <movement-title>Au clair de la lune</movement-title>
              <part-list><score-part id="P1"><part-name>Voix</part-name></score-part></part-list>
              <part id="P1">
                <measure number="1">
                  <attributes>
                    <divisions>2</divisions>
                    <key><fifths>-1</fifths></key>
                    <time><beats>2</beats><beat-type>4</beat-type></time>
                    <clef><sign>G</sign><line>2</line></clef>
                  </attributes>
                  <note><grace/><pitch><step>A</step><octave>4</octave></pitch><voice>1</voice><type>eighth</type></note>
                  <note><pitch><step>F</step><octave>4</octave></pitch><duration>2</duration><voice>1</voice><type>quarter</type></note>
                  <note><chord/><pitch><step>A</step><octave>4</octave></pitch><duration>2</duration><voice>1</voice><type>quarter</type></note>
                  <note><pitch><step>B</step><alter>-1</alter><octave>4</octave></pitch><duration>1</duration><voice>1</voice></note>
                  <note><rest/><duration>1</duration><voice>1</voice><type>eighth</type></note>
                  <backup><duration>4</duration></backup>
                  <note><pitch><step>F</step><octave>3</octave></pitch><duration>4</duration><voice>2</voice><type>half</type></note>
                </measure>
              </part>
            </score-partwise>
        """.trimIndent()
        val read = MusicXml.read(xml)
        assertEquals("Au clair de la lune", read.title)
        assertEquals(Key(Step.F, 0, Mode.MAJOR), read.melody.key)
        assertEquals(TimeSignature(2, 4), read.melody.time)
        assertEquals(
            listOf(
                n(Step.F, 4, Durations.QUARTER),
                // Pas de figure dans le fichier : déduite de la durée (1 sur 2 divisions = croche).
                n(Step.B, 4, Durations.EIGHTH, alter = -1),
                Rest(Durations.EIGHTH),
            ),
            read.melody.measures.single().events,
        )
        assertTrue(Verifier.check(read.melody).isEmpty())
    }

    @Test
    fun `sans armure ni chiffrage - do majeur a quatre temps`() {
        val xml = "<score-partwise><part id=\"P1\"><measure number=\"1\">" +
            "<note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><type>whole</type></note>" +
            "</measure></part></score-partwise>"
        val read = MusicXml.read(xml)
        assertEquals(Key(Step.C, 0, Mode.MAJOR), read.melody.key)
        assertEquals(TimeSignature(4, 4), read.melody.time)
        assertNull(read.title)
    }

    @Test
    fun `une melodie vide donne un fichier relisible`() {
        val empty = Melody(gMajor, TimeSignature(4, 4), emptyList())
        val read = MusicXml.read(MusicXml.write(empty)).melody
        assertEquals(gMajor, read.key)
        assertEquals(listOf(Measure(emptyList())), read.measures)
    }

    @Test
    fun `les fichiers illisibles sont refuses avec un message`() {
        assertFailsWith<MusicXmlException> { MusicXml.read("ceci n'est pas du XML") }
        assertFailsWith<MusicXmlException> { MusicXml.read("<score-timewise></score-timewise>") }
        assertFailsWith<MusicXmlException> { MusicXml.read("<score-partwise><part-list/></score-partwise>") }
        assertFailsWith<MusicXmlException> { MusicXml.read("<score-partwise><part><measure><note>") }
    }

    @Test
    fun `figures et points`() {
        assertEquals("quarter" to 0, MusicXml.typeAndDots(Durations.QUARTER))
        assertEquals("half" to 1, MusicXml.typeAndDots(Durations.dotted(Durations.HALF)))
        assertEquals("eighth" to 2, MusicXml.typeAndDots(Fraction.of(7, 32)))
        assertNull(MusicXml.typeAndDots(Fraction.of(5, 16)))
    }
}
