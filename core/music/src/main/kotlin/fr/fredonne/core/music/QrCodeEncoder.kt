package fr.fredonne.core.music

/**
 * Encodeur QR Code ISO/IEC 18004 autonome en Kotlin pur multiplateforme (Versions 1 à 10,
 * mode Octet / Byte, correction d'erreur Reed-Solomon sur GF(2^8) niveau L).
 *
 * Aucune dépendance externe (pas de ZXing) : fonctionne à l'identique sur Android, iOS et JVM
 * pour le tracé vectoriel dans le PDF ([ScorePdfGenerator]) et sur le Canvas Compose.
 */
object QrCodeEncoder {

    data class QrMatrix(val size: Int, val modules: Array<BooleanArray>) {
        operator fun get(x: Int, y: Int): Boolean = modules[y][x]
    }

    // Table des versions 1 à 10 (niveau L) : (totalCodewords, ecCodewordsPerBlock, numBlocksGroup1, dataCwGroup1, numBlocksGroup2, dataCwGroup2)
    private data class VersionSpec(
        val version: Int,
        val totalDataCodewords: Int,
        val ecCodewordsPerBlock: Int,
        val blocksGroup1: Int,
        val dataCwGroup1: Int,
        val blocksGroup2: Int,
        val dataCwGroup2: Int,
        val alignmentCenters: IntArray,
    )

    private val VERSION_SPECS = listOf(
        VersionSpec(1, 19, 7, 1, 19, 0, 0, intArrayOf()),
        VersionSpec(2, 34, 10, 1, 34, 0, 0, intArrayOf(6, 18)),
        VersionSpec(3, 55, 15, 1, 55, 0, 0, intArrayOf(6, 22)),
        VersionSpec(4, 80, 20, 1, 80, 0, 0, intArrayOf(6, 26)),
        VersionSpec(5, 108, 26, 1, 108, 0, 0, intArrayOf(6, 30)),
        VersionSpec(6, 136, 18, 2, 68, 0, 0, intArrayOf(6, 34)),
        VersionSpec(7, 156, 20, 2, 78, 0, 0, intArrayOf(6, 22, 38)),
        VersionSpec(8, 194, 24, 2, 97, 0, 0, intArrayOf(6, 24, 42)),
        VersionSpec(9, 232, 30, 2, 116, 0, 0, intArrayOf(6, 26, 46)),
        VersionSpec(10, 274, 18, 2, 68, 2, 69, intArrayOf(6, 28, 50)),
    )

    fun encode(text: String): QrMatrix {
        val rawBytes = text.encodeToByteArray()
        // Si la chaîne dépasse la capacité de la version 10 (271 octets), on tronque proprement à la dernière mesure
        val payload = if (rawBytes.size <= 271) rawBytes else rawBytes.copyOfRange(0, 271)

        val spec = VERSION_SPECS.first { v ->
            val headerBits = if (v.version <= 9) 4 + 8 else 4 + 16
            (headerBits + payload.size * 8) <= v.totalDataCodewords * 8
        }

        val dataCodewords = buildDataCodewords(payload, spec)
        val finalMessage = interleaveWithEc(dataCodewords, spec)

        val size = 17 + 4 * spec.version
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        drawFunctionPatterns(spec, size, modules, isFunction)
        drawCodewords(finalMessage, size, modules, isFunction)

        // Choix du masque (0..7) minimisant le score de pénalité ISO/IEC 18004
        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestModules = modules

        for (mask in 0..7) {
            val copy = Array(size) { y -> modules[y].copyOf() }
            applyMask(mask, size, copy, isFunction)
            drawFormatBits(ecLevelBits = 0b01, mask = mask, size = size, modules = copy, isFunction = isFunction)
            val penalty = computePenalty(size, copy)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
                bestModules = copy
            }
        }

        return QrMatrix(size, bestModules)
    }

    private fun buildDataCodewords(payload: ByteArray, spec: VersionSpec): IntArray {
        val capacityBits = spec.totalDataCodewords * 8
        val bits = BooleanArray(capacityBits)
        var bitPos = 0

        fun appendBits(value: Int, len: Int) {
            for (i in (len - 1) downTo 0) {
                if (bitPos < capacityBits) {
                    bits[bitPos++] = ((value ushr i) and 1) != 0
                }
            }
        }

        // Mode Byte = 0100
        appendBits(0b0100, 4)
        val countBits = if (spec.version <= 9) 8 else 16
        appendBits(payload.size, countBits)
        for (b in payload) {
            appendBits(b.toInt() and 0xFF, 8)
        }
        // Terminateur (jusqu'à 4 zéros)
        appendBits(0, minOf(4, capacityBits - bitPos))
        // Alignement sur l'octet
        while (bitPos % 8 != 0) {
            appendBits(0, 1)
        }
        // Octets de bourrage alternés 0xEC, 0x11
        var padToggle = true
        while (bitPos < capacityBits) {
            appendBits(if (padToggle) 0xEC else 0x11, 8)
            padToggle = !padToggle
        }

        val out = IntArray(spec.totalDataCodewords)
        for (i in out.indices) {
            var v = 0
            for (b in 0 until 8) {
                v = (v shl 1) or (if (bits[i * 8 + b]) 1 else 0)
            }
            out[i] = v
        }
        return out
    }

    private fun interleaveWithEc(dataCodewords: IntArray, spec: VersionSpec): IntArray {
        val dataBlocks = mutableListOf<IntArray>()
        val ecBlocks = mutableListOf<IntArray>()
        val gen = reedSolomonGenerator(spec.ecCodewordsPerBlock)

        var offset = 0
        repeat(spec.blocksGroup1) {
            val block = dataCodewords.copyOfRange(offset, offset + spec.dataCwGroup1)
            offset += spec.dataCwGroup1
            dataBlocks += block
            ecBlocks += reedSolomonRemainder(block, gen)
        }
        repeat(spec.blocksGroup2) {
            val block = dataCodewords.copyOfRange(offset, offset + spec.dataCwGroup2)
            offset += spec.dataCwGroup2
            dataBlocks += block
            ecBlocks += reedSolomonRemainder(block, gen)
        }

        val result = mutableListOf<Int>()
        val maxDataLen = maxOf(spec.dataCwGroup1, spec.dataCwGroup2)
        for (i in 0 until maxDataLen) {
            for (b in dataBlocks) {
                if (i < b.size) result += b[i]
            }
        }
        for (i in 0 until spec.ecCodewordsPerBlock) {
            for (eb in ecBlocks) {
                result += eb[i]
            }
        }
        return result.toIntArray()
    }

    private fun reedSolomonGenerator(degree: Int): IntArray {
        var result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        repeat(degree) {
            for (j in 0 until degree) {
                result[j] = gfMultiply(result[j], root)
                if (j + 1 < degree) {
                    result[j] = result[j] xor result[j + 1]
                }
            }
            root = gfMultiply(root, 0x02)
        }
        return result
    }

    private fun reedSolomonRemainder(data: IntArray, divisor: IntArray): IntArray {
        val result = IntArray(divisor.size)
        for (b in data) {
            val factor = b xor result[0]
            for (i in 0 until result.size - 1) {
                result[i] = result[i + 1] xor gfMultiply(divisor[i], factor)
            }
            result[result.size - 1] = gfMultiply(divisor[result.size - 1], factor)
        }
        return result
    }

    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun drawFunctionPatterns(
        spec: VersionSpec,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
    ) {
        fun setFunctionModule(x: Int, y: Int, isDark: Boolean) {
            if (x in 0 until size && y in 0 until size) {
                modules[y][x] = isDark
                isFunction[y][x] = true
            }
        }

        // Motifs de synchronisation (ligne 6 et colonne 6)
        for (i in 0 until size) {
            setFunctionModule(6, i, i % 2 == 0)
            setFunctionModule(i, 6, i % 2 == 0)
        }

        // 3 motifs de détection de position (Finder patterns) aux coins
        fun drawFinderPattern(cx: Int, cy: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                    val xx = cx + dx
                    val yy = cy + dy
                    if (xx in 0 until size && yy in 0 until size) {
                        setFunctionModule(xx, yy, dist != 2 && dist != 4)
                    }
                }
            }
        }
        drawFinderPattern(3, 3)
        drawFinderPattern(size - 4, 3)
        drawFinderPattern(3, size - 4)

        // Motifs d'alignement
        val aligns = spec.alignmentCenters
        for (ay in aligns) {
            for (ax in aligns) {
                val overlapsFinder = (ax == 6 && ay == 6) ||
                    (ax == 6 && ay == size - 7) ||
                    (ax == size - 7 && ay == 6)
                if (!overlapsFinder) {
                    for (dy in -2..2) {
                        for (dx in -2..2) {
                            val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                            setFunctionModule(ax + dx, ay + dy, dist != 1)
                        }
                    }
                }
            }
        }

        // Réservation des zones d'information de format + module sombre
        drawFormatBits(0b01, 0, size, modules, isFunction)

        // Version >= 7 : information de version 6x3
        if (spec.version >= 7) {
            var rem = spec.version
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            val bits = (spec.version shl 12) or rem
            for (i in 0 until 18) {
                val bit = ((bits ushr i) and 1) != 0
                val a = size - 11 + (i % 3)
                val b = i / 3
                setFunctionModule(a, b, bit)
                setFunctionModule(b, a, bit)
            }
        }
    }

    private fun drawFormatBits(
        ecLevelBits: Int,
        mask: Int,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
    ) {
        val data = (ecLevelBits shl 3) or mask
        var rem = data
        repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
        val bits = ((data shl 10) or rem) xor 0x5412

        fun setFmt(x: Int, y: Int, bitIdx: Int) {
            modules[y][x] = ((bits ushr bitIdx) and 1) != 0
            isFunction[y][x] = true
        }

        for (i in 0..5) setFmt(8, i, i)
        setFmt(8, 7, 6)
        setFmt(8, 8, 7)
        setFmt(7, 8, 8)
        for (i in 9..14) setFmt(14 - i, 8, i)

        for (i in 0..7) setFmt(size - 1 - i, 8, i)
        for (i in 8..14) setFmt(8, size - 15 + i, i)

        // Module toujours sombre
        modules[size - 8][8] = true
        isFunction[size - 8][8] = true
    }

    private fun drawCodewords(
        codewords: IntArray,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
    ) {
        var bitIdx = 0
        val totalBits = codewords.size * 8
        var right = size - 1
        var upward = true

        while (right >= 1) {
            if (right == 6) right = 5
            val ys = if (upward) (size - 1 downTo 0) else (0 until size)
            for (y in ys) {
                for (j in 0..1) {
                    val x = right - j
                    if (!isFunction[y][x]) {
                        val dark = if (bitIdx < totalBits) {
                            ((codewords[bitIdx ushr 3] ushr (7 - (bitIdx and 7))) and 1) != 0
                        } else {
                            false
                        }
                        modules[y][x] = dark
                        bitIdx++
                    }
                }
            }
            upward = !upward
            right -= 2
        }
    }

    private fun applyMask(
        mask: Int,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
    ) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (!isFunction[y][x]) {
                    val invert = when (mask) {
                        0 -> (x + y) % 2 == 0
                        1 -> y % 2 == 0
                        2 -> x % 3 == 0
                        3 -> (x + y) % 3 == 0
                        4 -> (y / 2 + x / 3) % 2 == 0
                        5 -> (x * y) % 2 + (x * y) % 3 == 0
                        6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
                        else -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
                    }
                    if (invert) modules[y][x] = !modules[y][x]
                }
            }
        }
    }

    private fun computePenalty(size: Int, modules: Array<BooleanArray>): Int {
        var penalty = 0
        // Règle 1 : lignes/colonnes de >= 5 modules de même couleur
        for (y in 0 until size) {
            var runColor = modules[y][0]
            var runLen = 1
            for (x in 1 until size) {
                if (modules[y][x] == runColor) {
                    runLen++
                    if (runLen == 5) penalty += 3 else if (runLen > 5) penalty += 1
                } else {
                    runColor = modules[y][x]
                    runLen = 1
                }
            }
        }
        for (x in 0 until size) {
            var runColor = modules[0][x]
            var runLen = 1
            for (y in 1 until size) {
                if (modules[y][x] == runColor) {
                    runLen++
                    if (runLen == 5) penalty += 3 else if (runLen > 5) penalty += 1
                } else {
                    runColor = modules[y][x]
                    runLen = 1
                }
            }
        }
        // Règle 2 : blocs 2x2 de même couleur
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) {
                    penalty += 3
                }
            }
        }
        return penalty
    }
}
