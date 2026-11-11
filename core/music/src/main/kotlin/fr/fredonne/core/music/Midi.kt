package fr.fredonne.core.music

/**
 * Écriture d'un fichier MIDI standard (format 0, une piste) pour une mélodie à une voix.
 *
 * Les durées restent exactes : la résolution (impulsions par noire) est choisie pour que
 * chaque note, tuplets compris, tombe sur un nombre entier d'impulsions. Les notes liées
 * sont jouées d'une seule tenue. La lecture de fichiers MIDI n'est pas encore faite :
 * elle demande de quantifier les durées et de choisir l'orthographe des notes.
 */
object Midi {

    /** Résolution minimale visée, pour que les autres logiciels relisent le fichier confortablement. */
    private const val TARGET_RESOLUTION = 480L

    /**
     * @param tempo tempo de lecture, écrit dans le fichier.
     * @param program numéro d'instrument General MIDI, de 0 (piano) à 127.
     */
    fun write(melody: Melody, tempo: Tempo = Tempo.quarter(90), program: Int = 0, velocity: Int = 80): ByteArray {
        require(program in 0..127) { "Instrument MIDI invalide : $program" }
        require(velocity in 1..127) { "Vélocité invalide : $velocity" }
        val resolution = resolutionFor(melody)
        val track = Bytes()

        // Tempo : microsecondes par noire.
        val quartersPerMinute = tempo.wholesPerMinute * Fraction.of(4)
        val microseconds = (60_000_000.0 / quartersPerMinute.toDouble()).toLong().coerceIn(1, 0xFFFFFF)
        track.delta(0).add(0xFF, 0x51, 0x03)
            .add((microseconds shr 16).toInt() and 0xFF, (microseconds shr 8).toInt() and 0xFF, microseconds.toInt() and 0xFF)

        // Chiffrage : le dénominateur est codé par son logarithme en base 2.
        track.delta(0).add(0xFF, 0x58, 0x04, melody.time.beats and 0xFF, log2(melody.time.unit), 24, 8)

        if (melody.key.isPractical) {
            track.delta(0).add(0xFF, 0x59, 0x02, melody.key.fifths and 0xFF, if (melody.key.mode == Mode.MINOR) 1 else 0)
        }
        track.delta(0).add(0xC0, program)

        var pending = 0L // silence accumulé avant le prochain événement
        var held: Int? = null // note liée en cours de tenue
        var heldTicks = 0L

        fun release() {
            val key = held ?: return
            track.delta(heldTicks).add(0x80, key, 0)
            held = null
            heldTicks = 0
        }

        for (measure in melody.measures) {
            for (event in measure.events) {
                val ticks = ticksOf(event, resolution)
                val key = (event as? Note)?.pitch?.midi?.takeIf { it in 0..127 }

                if (held != null && key == held) {
                    // Suite d'une liaison : on prolonge la tenue sans réattaquer.
                    heldTicks += ticks
                    if (!(event as Note).tiedToNext) release()
                    continue
                }
                release()
                if (key == null) {
                    pending += ticks
                    continue
                }
                track.delta(pending).add(0x90, key, velocity)
                pending = 0
                held = key
                heldTicks = ticks
                if (!(event as Note).tiedToNext) release()
            }
        }
        release()
        track.delta(pending).add(0xFF, 0x2F, 0x00)

        val out = Bytes()
        out.text("MThd").int32(6).int16(0).int16(1).int16(resolution.toInt())
        out.text("MTrk").int32(track.size).addAll(track)
        return out.toByteArray()
    }

    /** Nombre d'impulsions par noire : multiple du plus petit commun dénominateur des durées. */
    internal fun resolutionFor(melody: Melody): Long {
        var base = 1L
        for (measure in melody.measures) {
            for (event in measure.events) {
                base = lcm(base, (event.length * Fraction.of(4)).denominator)
            }
        }
        require(base <= 32767) { "Rythme trop fin pour un fichier MIDI (résolution $base)" }
        val factor = maxOf(1L, (TARGET_RESOLUTION + base - 1) / base)
        return if (base * factor <= 32767) base * factor else base
    }

    private fun ticksOf(event: Event, resolution: Long): Long =
        (event.length * Fraction.of(4) * Fraction.of(resolution, 1)).numerator

    private fun log2(value: Int): Int {
        var n = 0
        var v = value
        while (v > 1) {
            v = v shr 1
            n++
        }
        return n
    }

    private fun lcm(a: Long, b: Long): Long {
        var x = a
        var y = b
        while (y != 0L) {
            val t = x % y
            x = y
            y = t
        }
        return a / x * b
    }

    /** Petit tampon d'octets, pour ne dépendre d'aucune classe de la JVM. */
    private class Bytes {
        private val data = ArrayList<Byte>()
        val size: Int get() = data.size

        fun add(vararg values: Int): Bytes {
            for (v in values) data += v.toByte()
            return this
        }

        fun addAll(other: Bytes): Bytes {
            data += other.data
            return this
        }

        fun text(value: String): Bytes {
            for (c in value) data += c.code.toByte()
            return this
        }

        fun int16(value: Int): Bytes = add((value shr 8) and 0xFF, value and 0xFF)

        fun int32(value: Int): Bytes =
            add((value ushr 24) and 0xFF, (value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF)

        /** Délai avant l'événement, en quantité de longueur variable (7 bits par octet). */
        fun delta(ticks: Long): Bytes {
            require(ticks in 0..0x0FFFFFFF) { "Délai MIDI hors limites : $ticks" }
            var shift = 21
            var started = false
            while (shift > 0) {
                val group = ((ticks shr shift) and 0x7F).toInt()
                if (group != 0 || started) {
                    data += (group or 0x80).toByte()
                    started = true
                }
                shift -= 7
            }
            data += (ticks and 0x7F).toInt().toByte()
            return this
        }

        fun toByteArray(): ByteArray = data.toByteArray()
    }
}
