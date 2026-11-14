package fr.fredonne.core.music

import kotlin.math.roundToInt

/**
 * Générateur de partition PDF 1.4 vectoriel autonome (A4 portrait, sans filigrane intrusif,
 * 100 % hors-ligne, écrit en Kotlin pur multiplateforme pour Android et iOS).
 *
 * Direction artistique « Édition de poche Pilmil Studio » :
 * - Bandeau d'en-tête ivoire chaud, double filet vert sauge (#285D4D) et ocre ambré (#D97706)
 * - Logo vectoriel de Fredonne (carré arrondi ambré et double croche ligaturée)
 * - Typographie d'édition musicale (Times-Bold pour le titre et les chiffrages, Helvetica pour les métadonnées)
 * - Gravure avec espacement logarithmique de Gourlay (1987) et règles de hampes d'Elaine Gould (2011)
 * - Signature de pied de page discrète avec mini-logo pour faire connaître l'application lors du partage.
 */
object ScorePdfGenerator {

    private const val PAGE_WIDTH = 595f
    private const val PAGE_HEIGHT = 842f
    private const val MARGIN_X = 44f

    fun generate(
        melody: Melody,
        instrument: Instrument? = null,
        tempoBpm: Int = 96,
        title: String = "Partition Fredonne",
    ): ByteArray {
        val written = if (instrument != null && instrument.transposition != Interval.UNISON) {
            melody.transposed(-instrument.transposition).respellPractical()
        } else {
            melody.respellPractical()
        }

        val content = StringBuilder()

        // 1. Fond papier crème très subtil et cadre d'édition musicale
        drawRect(content, 0f, 0f, PAGE_WIDTH, PAGE_HEIGHT, 0.992f, 0.986f, 0.973f) // #FDFBF8
        drawStrokedRect(content, 24f, 24f, PAGE_WIDTH - 48f, PAGE_HEIGHT - 48f, 0.6f, 0.86f, 0.84f, 0.79f)

        // 2. Cartouche d'en-tête (bandeau ivoire + double filet sauge/ambre)
        val headerBottomY = PAGE_HEIGHT - 114f
        drawRoundedRect(
            sb = content,
            x = MARGIN_X,
            y = headerBottomY,
            w = PAGE_WIDTH - 2 * MARGIN_X,
            h = 72f,
            r = 8f,
            fillR = 0.965f,
            fillG = 0.953f,
            fillB = 0.925f, // #F6F3EC
            strokeR = 0.88f,
            strokeG = 0.85f,
            strokeB = 0.79f,
        )

        // Filet d'accentuation supérieur vert sauge (#285D4D) et ocre (#D97706)
        drawLine(content, MARGIN_X + 8f, PAGE_HEIGHT - 42f, PAGE_WIDTH - MARGIN_X - 8f, PAGE_HEIGHT - 42f, 1.8f, 0.157f, 0.365f, 0.302f)
        drawLine(content, MARGIN_X + 8f, PAGE_HEIGHT - 44.5f, MARGIN_X + 96f, PAGE_HEIGHT - 44.5f, 1.2f, 0.851f, 0.467f, 0.024f)

        // Logo vectoriel Fredonne à gauche du cartouche (36x36 pt)
        val logoX = MARGIN_X + 14f
        val logoY = headerBottomY + 18f
        drawFredonneLogo(content, logoX, logoY, size = 36f)

        // Kicker & Titre de la partition
        val textStartX = logoX + 48f
        drawText(
            sb = content,
            x = textStartX,
            y = headerBottomY + 46f,
            size = 8.5f,
            text = "FREDONNE  ·  CARNET MÉLODIQUE HORS-LIGNE",
            font = Font.HELVETICA_BOLD,
            r = 0.706f,
            g = 0.325f,
            b = 0.035f, // #B45309
        )
        drawText(
            sb = content,
            x = textStartX,
            y = headerBottomY + 27f,
            size = 19f,
            text = title,
            font = Font.TIMES_BOLD,
            r = 0.118f,
            g = 0.114f,
            b = 0.102f, // #1E1D1A
        )

        val instLabel = instrumentDisplayName(instrument)
        val keyLabel = formatKeyFrench(written.key)
        val pedalSuffix = if (instrument?.pedalHarp == true) {
            "   ·   Pédales : " + HarpPedals.initialDiagram(written.key).replace("♮", "")
        } else {
            ""
        }
        val metaLine = "Tonalité écrite : $keyLabel   ·   Mesure : ${written.time.beats}/${written.time.unit}   ·   Tempo : $tempoBpm BPM$pedalSuffix"
        drawText(
            sb = content,
            x = textStartX,
            y = headerBottomY + 12f,
            size = 9.5f,
            text = metaLine,
            font = Font.HELVETICA,
            r = 0.38f,
            g = 0.36f,
            b = 0.33f,
        )

        // Pastille d'instrument en haut à droite du cartouche
        val badgeW = (instLabel.length * 5.2f + 22f).coerceIn(88f, 165f)
        val badgeX = PAGE_WIDTH - MARGIN_X - badgeW - 14f
        val badgeY = headerBottomY + 26f
        drawRoundedRect(
            sb = content,
            x = badgeX,
            y = badgeY,
            w = badgeW,
            h = 22f,
            r = 6f,
            fillR = 0.898f,
            fillG = 0.941f,
            fillB = 0.925f, // #E5F0EC
            strokeR = 0.157f,
            strokeG = 0.365f,
            strokeB = 0.302f,
        )
        drawText(
            sb = content,
            x = badgeX + 10f,
            y = badgeY + 7.5f,
            size = 9f,
            text = instLabel,
            font = Font.HELVETICA_BOLD,
            r = 0.157f,
            g = 0.365f,
            b = 0.302f,
        )

        // 3. Systèmes de portées (2 à 4 mesures par système selon la longueur de la pièce)
        val measures = written.measures.ifEmpty { listOf(Measure(emptyList())) }
        val measuresPerSystem = when {
            measures.size >= 8 -> 4
            measures.size >= 5 -> 3
            else -> 2
        }
        val systems = measures.chunked(measuresPerSystem)
        val lineSpacing = 7.8f
        val stepHalf = lineSpacing / 2f
        val clef = Clef.resolveFor(written, instrument)
        val bottomLineDiatonic = clef.bottomLinePitch.diatonic
        val staffUsableWidth = PAGE_WIDTH - 2 * MARGIN_X

        var systemTopY = headerBottomY - 38f

        systems.forEachIndexed { sysIdx, sysMeasures ->
            if (systemTopY < 122f) return@forEachIndexed
            val bottomLineY = systemTopY - 4f * lineSpacing

            // 5 lignes de la portée
            for (line in 0..4) {
                val y = systemTopY - line * lineSpacing
                drawLine(content, MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, 0.72f, 0.20f, 0.19f, 0.17f)
            }

            // Barre de début de système renforcée (vert sauge sombre)
            drawLine(content, MARGIN_X, systemTopY + 1f, MARGIN_X, bottomLineY - 1f, 1.8f, 0.157f, 0.365f, 0.302f)

            // Clé (Sol, Sol 8vb ou Fa) en Times-Bold + armure + chiffrage
            drawText(
                sb = content,
                x = MARGIN_X + 5f,
                y = bottomLineY + lineSpacing * (if (clef == Clef.BASS) 1.35f else 0.75f),
                size = if (clef == Clef.TREBLE) 22f else 17f,
                text = clef.displaySymbol,
                font = Font.TIMES_BOLD,
                r = 0.157f,
                g = 0.365f,
                b = 0.302f,
            )

            val fifths = written.key.fifths
            val accLabel = when {
                fifths > 0 -> "${fifths}#"
                fifths < 0 -> "${-fifths}b"
                else -> ""
            }
            if (accLabel.isNotEmpty()) {
                drawText(
                    sb = content,
                    x = MARGIN_X + 24f,
                    y = systemTopY - lineSpacing * 0.85f,
                    size = 9.5f,
                    text = accLabel,
                    font = Font.HELVETICA_BOLD,
                    r = 0.706f,
                    g = 0.325f,
                    b = 0.035f,
                )
            }

            if (sysIdx == 0) {
                drawText(
                    sb = content,
                    x = MARGIN_X + 38f,
                    y = systemTopY - lineSpacing * 1.75f,
                    size = 12f,
                    text = "${written.time.beats}",
                    font = Font.TIMES_BOLD,
                    r = 0.12f,
                    g = 0.11f,
                    b = 0.10f,
                )
                drawText(
                    sb = content,
                    x = MARGIN_X + 38f,
                    y = systemTopY - lineSpacing * 3.75f,
                    size = 12f,
                    text = "${written.time.unit}",
                    font = Font.TIMES_BOLD,
                    r = 0.12f,
                    g = 0.11f,
                    b = 0.10f,
                )
            }

            val headerOffset = if (sysIdx == 0) 54f else 40f
            val availableMeasuresWidth = staffUsableWidth - headerOffset

            val measureWeights = sysMeasures.map { m ->
                if (m.events.isEmpty()) 2.0f
                else m.events.sumOf { MusicTheory.gourlaySpacingFactor(it.length).toDouble() }.toFloat().coerceAtLeast(1.5f)
            }
            val totalWeight = measureWeights.sum().coerceAtLeast(1f)

            var measureStartX = MARGIN_X + headerOffset
            sysMeasures.forEachIndexed { localIdx, measure ->
                val globalMeasureIdx = sysIdx * measuresPerSystem + localIdx
                val mWidth = availableMeasuresWidth * (measureWeights[localIdx] / totalWeight)
                val measureEndX = measureStartX + mWidth

                // Petit numéro de mesure en italique au-dessus de la portée
                drawText(
                    sb = content,
                    x = measureStartX + 3f,
                    y = systemTopY + 6.5f,
                    size = 8f,
                    text = "${globalMeasureIdx + 1}",
                    font = Font.TIMES_ITALIC,
                    r = 0.45f,
                    g = 0.43f,
                    b = 0.39f,
                )

                val padX = if (measuresPerSystem >= 4) 8f else 14f
                val innerWidth = (mWidth - 2 * padX).coerceAtLeast(16f)
                val eventFactors = measure.events.map { MusicTheory.gourlaySpacingFactor(it.length) }
                val factorSum = eventFactors.sum().coerceAtLeast(1f)

                var eventCursorX = measureStartX + padX
                measure.events.forEachIndexed { eIdx, event ->
                    val slotW = innerWidth * (eventFactors[eIdx] / factorSum)
                    val cx = eventCursorX + slotW * (if (measure.events.size == 1) 0.32f else 0.42f)
                    eventCursorX += slotW

                    when (event) {
                        is Note -> {
                            val d = event.pitch.diatonic
                            val cy = bottomLineY + (d - bottomLineDiatonic) * stepHalf

                            // Lignes supplémentaires (sous Mi4 ou au-dessus de Fa5)
                            val lowLineD = bottomLineDiatonic
                            val highLineD = bottomLineDiatonic + 8
                            if (d <= lowLineD - 2) {
                                var ld = lowLineD - 2
                                while (ld >= d) {
                                    val ly = bottomLineY + (ld - bottomLineDiatonic) * stepHalf
                                    drawLine(content, cx - 7.5f, ly, cx + 7.5f, ly, 0.85f, 0.12f, 0.11f, 0.10f)
                                    ld -= 2
                                }
                            } else if (d >= highLineD + 2) {
                                var ld = highLineD + 2
                                while (ld <= d) {
                                    val ly = bottomLineY + (ld - bottomLineDiatonic) * stepHalf
                                    drawLine(content, cx - 7.5f, ly, cx + 7.5f, ly, 0.85f, 0.12f, 0.11f, 0.10f)
                                    ld += 2
                                }
                            }

                            // Altération accidentelle (# ou b)
                            if (event.pitch.alter != 0) {
                                val acc = if (event.pitch.alter > 0) "#" else "b"
                                drawText(
                                    sb = content,
                                    x = cx - 12f,
                                    y = cy - 3f,
                                    size = 9.5f,
                                    text = acc,
                                    font = Font.HELVETICA_BOLD,
                                    r = 0.12f,
                                    g = 0.11f,
                                    b = 0.10f,
                                )
                            }

                            val isWhole = event.written >= Durations.WHOLE
                            val isHalf = event.written >= Durations.HALF && event.written < Durations.WHOLE
                            val rx = 4.5f
                            val ry = 3.2f
                            drawOval(content, cx, cy, rx, ry, filled = !(isWhole || isHalf), r = 0.12f, g = 0.11f, b = 0.10f)

                            // Hampe selon la règle d'Elaine Gould
                            if (!isWhole) {
                                val stemUp = d < bottomLineDiatonic + 4
                                val stemX = if (stemUp) cx + rx - 0.5f else cx - rx + 0.5f
                                val stemEndY = if (stemUp) cy + lineSpacing * 2.85f else cy - lineSpacing * 2.85f
                                drawLine(content, stemX, cy, stemX, stemEndY, 1.15f, 0.12f, 0.11f, 0.10f)

                                val flags = when {
                                    event.written <= Durations.SIXTEENTH -> 2
                                    event.written <= Durations.EIGHTH -> 1
                                    else -> 0
                                }
                                repeat(flags) { f ->
                                    val fy = if (stemUp) stemEndY - f * 4.5f else stemEndY + f * 4.5f
                                    val tipY = if (stemUp) fy - 7f else fy + 7f
                                    drawLine(content, stemX, fy, stemX + 6f, tipY, 1.25f, 0.12f, 0.11f, 0.10f)
                                }
                            }

                            // Point de prolongation
                            if (event.written.numerator == 3L && event.written.denominator >= 4L) {
                                drawOval(content, cx + rx + 4f, cy + 1f, 1.4f, 1.4f, filled = true, r = 0.12f, g = 0.11f, b = 0.10f)
                            }

                            // Nom de note discret sous la portée (décalé sous la tête si note grave sur ligne supplémentaire)
                            val noteLabel = stepFrench(event.pitch.step) +
                                (if (event.pitch.alter > 0) "#" else if (event.pitch.alter < 0) "b" else "")
                            val labelY = minOf(bottomLineY - 16f, cy - 13f)
                            drawText(
                                sb = content,
                                x = cx - 5f,
                                y = labelY,
                                size = 7.5f,
                                text = noteLabel,
                                font = Font.HELVETICA,
                                r = 0.42f,
                                g = 0.40f,
                                b = 0.36f,
                            )
                        }
                        is Rest -> {
                            val ry = systemTopY - lineSpacing * 1.8f
                            drawRect(content, cx - 4f, ry, 8f, 3.2f, 0.42f, 0.40f, 0.36f)
                        }
                    }
                }

                // Barre de mesure à droite (double barre finale sur la dernière mesure)
                val isVeryLast = globalMeasureIdx == measures.lastIndex
                if (isVeryLast) {
                    drawLine(content, measureEndX - 3.2f, systemTopY, measureEndX - 3.2f, bottomLineY, 0.8f, 0.12f, 0.11f, 0.10f)
                    drawLine(content, measureEndX, systemTopY, measureEndX, bottomLineY, 2.2f, 0.12f, 0.11f, 0.10f)
                } else {
                    drawLine(content, measureEndX, systemTopY, measureEndX, bottomLineY, 0.9f, 0.12f, 0.11f, 0.10f)
                }

                measureStartX = measureEndX
            }

            systemTopY -= 88f
        }

        // 4. Signature éditoriale en pied de page avec mini-logo Fredonne et QR code de reprise hors-ligne
        val sharePayload = ShareCodec.encode(melody = melody, instrument = instrument, tempoBpm = tempoBpm)
        val qrMatrix = QrCodeEncoder.encode(sharePayload)

        val footerY = 36f
        val qrBoxSize = 68f
        val qrBoxX = PAGE_WIDTH - MARGIN_X - qrBoxSize
        val qrBoxY = footerY - 2f

        drawLine(content, MARGIN_X, footerY + 34f, qrBoxX - 12f, footerY + 34f, 0.6f, 0.86f, 0.84f, 0.79f)
        drawFredonneLogo(content, MARGIN_X, footerY + 6f, size = 20f)
        drawText(
            sb = content,
            x = MARGIN_X + 26f,
            y = footerY + 17f,
            size = 8.5f,
            text = "Fredonne  ·  Écrire, vérifier et transposer une mélodie hors-ligne (Pilmil Studio)",
            font = Font.HELVETICA_BOLD,
            r = 0.157f,
            g = 0.365f,
            b = 0.302f,
        )
        drawText(
            sb = content,
            x = MARGIN_X + 26f,
            y = footerY + 7f,
            size = 7.5f,
            text = "Application libre (MPL-2.0), sans compte ni Internet  ·  Scannez le QR code à droite pour modifier la partition",
            font = Font.HELVETICA,
            r = 0.45f,
            g = 0.43f,
            b = 0.39f,
        )

        // Encadré du QR code en bas à droite
        drawRoundedRect(
            sb = content,
            x = qrBoxX,
            y = qrBoxY,
            w = qrBoxSize,
            h = qrBoxSize,
            r = 6f,
            fillR = 1.0f,
            fillG = 1.0f,
            fillB = 1.0f,
            strokeR = 0.82f,
            strokeG = 0.80f,
            strokeB = 0.75f,
        )
        drawQrMatrix(
            sb = content,
            qr = qrMatrix,
            x = qrBoxX + 5f,
            y = qrBoxY + 5f,
            boxSize = qrBoxSize - 10f,
        )

        return buildSinglePagePdf(content.toString())
    }

    private fun drawQrMatrix(
        sb: StringBuilder,
        qr: QrCodeEncoder.QrMatrix,
        x: Float,
        y: Float,
        boxSize: Float,
    ) {
        val cell = boxSize / qr.size.toFloat()
        sb.appendLine("0.118 0.114 0.102 rg") // Encre #1E1D1A
        for (row in 0 until qr.size) {
            // Regroupe les modules sombres adjacents sur une même ligne pour alléger le flux PDF
            var col = 0
            while (col < qr.size) {
                if (qr[col, row]) {
                    val startCol = col
                    while (col < qr.size && qr[col, row]) col++
                    val runLen = col - startCol
                    val rx = x + startCol * cell
                    val ry = y + (qr.size - 1 - row) * cell
                    sb.appendLine("${fmt(rx)} ${fmt(ry)} ${fmt(runLen * cell + 0.04f)} ${fmt(cell + 0.04f)} re")
                } else {
                    col++
                }
            }
        }
        sb.appendLine("f")
    }

    /**
     * Dessine le logo officiel de Fredonne en vectoriel PDF :
     * fond carré arrondi ocre ambré (#D97706) + double croche ligaturée sombre (#111318).
     */
    private fun drawFredonneLogo(sb: StringBuilder, x: Float, y: Float, size: Float) {
        val scale = size / 36f
        val corner = 9f * scale
        drawRoundedRect(
            sb = sb,
            x = x,
            y = y,
            w = size,
            h = size,
            r = corner,
            fillR = 0.851f,
            fillG = 0.467f,
            fillB = 0.024f, // #D97706
            strokeR = 0.706f,
            strokeG = 0.325f,
            strokeB = 0.035f,
        )

        // Coordonnées internes basées sur le SVG 36x36 (origine en haut dans le SVG -> inversée en Y pour PDF)
        fun lx(svgX: Float): Float = x + svgX * scale
        fun ly(svgY: Float): Float = y + (36f - svgY) * scale

        // Deux têtes de notes : (12.5, 24.5) et (23.5, 22.0) de rayon 3.0
        val rHead = 3.0f * scale
        drawOval(sb, lx(12.5f), ly(24.5f), rHead, rHead * 0.88f, filled = true, r = 0.067f, g = 0.075f, b = 0.094f)
        drawOval(sb, lx(23.5f), ly(22.0f), rHead, rHead * 0.88f, filled = true, r = 0.067f, g = 0.075f, b = 0.094f)

        // Hampes et ligature oblique : M15 24 V11 L26 8.5 V21.5
        val stemW = (2.4f * scale).coerceAtLeast(1.1f)
        drawLine(sb, lx(15f), ly(24f), lx(15f), ly(11f), stemW, 0.067f, 0.075f, 0.094f)
        drawLine(sb, lx(15f), ly(11f), lx(26f), ly(8.5f), stemW * 1.15f, 0.067f, 0.075f, 0.094f)
        drawLine(sb, lx(26f), ly(8.5f), lx(26f), ly(21.5f), stemW, 0.067f, 0.075f, 0.094f)
    }

    private enum class Font(val pdfName: String) {
        HELVETICA("/F1"),
        HELVETICA_BOLD("/F2"),
        TIMES_BOLD("/F3"),
        TIMES_ITALIC("/F4"),
    }

    private fun fmt(v: Float): String {
        val scaled = (v * 100f).roundToInt()
        val intPart = scaled / 100
        val fracPart = kotlin.math.abs(scaled % 100)
        return if (fracPart == 0) "$intPart" else "$intPart.${fracPart.toString().padStart(2, '0')}"
    }

    private fun drawLine(
        sb: StringBuilder,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        width: Float,
        r: Float,
        g: Float,
        b: Float,
    ) {
        sb.appendLine("${fmt(r)} ${fmt(g)} ${fmt(b)} RG")
        sb.appendLine("1 J 1 j") // Extrémités et jointures arrondies
        sb.appendLine("${fmt(width)} w")
        sb.appendLine("${fmt(x1)} ${fmt(y1)} m ${fmt(x2)} ${fmt(y2)} l S")
    }

    private fun drawRect(
        sb: StringBuilder,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        r: Float,
        g: Float,
        b: Float,
    ) {
        sb.appendLine("${fmt(r)} ${fmt(g)} ${fmt(b)} rg")
        sb.appendLine("${fmt(x)} ${fmt(y)} ${fmt(w)} ${fmt(h)} re f")
    }

    private fun drawStrokedRect(
        sb: StringBuilder,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        width: Float,
        r: Float,
        g: Float,
        b: Float,
    ) {
        sb.appendLine("${fmt(r)} ${fmt(g)} ${fmt(b)} RG")
        sb.appendLine("${fmt(width)} w")
        sb.appendLine("${fmt(x)} ${fmt(y)} ${fmt(w)} ${fmt(h)} re S")
    }

    private fun drawRoundedRect(
        sb: StringBuilder,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        r: Float,
        fillR: Float,
        fillG: Float,
        fillB: Float,
        strokeR: Float,
        strokeG: Float,
        strokeB: Float,
    ) {
        val k = 0.5522847f * r
        sb.appendLine("${fmt(fillR)} ${fmt(fillG)} ${fmt(fillB)} rg")
        sb.appendLine("${fmt(strokeR)} ${fmt(strokeG)} ${fmt(strokeB)} RG")
        sb.appendLine("0.8 w")
        sb.appendLine("${fmt(x + r)} ${fmt(y)} m")
        sb.appendLine("${fmt(x + w - r)} ${fmt(y)} l")
        sb.appendLine("${fmt(x + w - r + k)} ${fmt(y)} ${fmt(x + w)} ${fmt(y + r - k)} ${fmt(x + w)} ${fmt(y + r)} c")
        sb.appendLine("${fmt(x + w)} ${fmt(y + h - r)} l")
        sb.appendLine("${fmt(x + w)} ${fmt(y + h - r + k)} ${fmt(x + w - r + k)} ${fmt(y + h)} ${fmt(x + w - r)} ${fmt(y + h)} c")
        sb.appendLine("${fmt(x + r)} ${fmt(y + h)} l")
        sb.appendLine("${fmt(x + r - k)} ${fmt(y + h)} ${fmt(x)} ${fmt(y + h - r + k)} ${fmt(x)} ${fmt(y + h - r)} c")
        sb.appendLine("${fmt(x)} ${fmt(y + r)} l")
        sb.appendLine("${fmt(x)} ${fmt(y + r - k)} ${fmt(x + r - k)} ${fmt(y)} ${fmt(x + r)} ${fmt(y)} c")
        sb.appendLine("B")
    }

    private fun drawOval(
        sb: StringBuilder,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        filled: Boolean,
        r: Float,
        g: Float,
        b: Float,
    ) {
        val k = 0.5522847f
        val ox = rx * k
        val oy = ry * k
        if (filled) {
            sb.appendLine("${fmt(r)} ${fmt(g)} ${fmt(b)} rg")
        } else {
            sb.appendLine("${fmt(r)} ${fmt(g)} ${fmt(b)} RG")
            sb.appendLine("1.3 w")
        }
        sb.appendLine("${fmt(cx - rx)} ${fmt(cy)} m")
        sb.appendLine("${fmt(cx - rx)} ${fmt(cy + oy)} ${fmt(cx - ox)} ${fmt(cy + ry)} ${fmt(cx)} ${fmt(cy + ry)} c")
        sb.appendLine("${fmt(cx + ox)} ${fmt(cy + ry)} ${fmt(cx + rx)} ${fmt(cy + oy)} ${fmt(cx + rx)} ${fmt(cy)} c")
        sb.appendLine("${fmt(cx + rx)} ${fmt(cy - oy)} ${fmt(cx + ox)} ${fmt(cy - ry)} ${fmt(cx)} ${fmt(cy - ry)} c")
        sb.appendLine("${fmt(cx - ox)} ${fmt(cy - ry)} ${fmt(cx - rx)} ${fmt(cy - oy)} ${fmt(cx - rx)} ${fmt(cy)} c")
        sb.appendLine(if (filled) "f" else "S")
    }

    private fun drawText(
        sb: StringBuilder,
        x: Float,
        y: Float,
        size: Float,
        text: String,
        font: Font,
        r: Float,
        g: Float,
        b: Float,
    ) {
        val safe = escapePdfText(text)
        sb.appendLine("BT")
        sb.appendLine("${fmt(r)} ${fmt(g)} ${fmt(b)} rg")
        sb.appendLine("${font.pdfName} ${fmt(size)} Tf")
        sb.appendLine("${fmt(x)} ${fmt(y)} Td")
        sb.appendLine("($safe) Tj")
        sb.appendLine("ET")
    }

    private fun escapePdfText(s: String): String {
        val ascii = s
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("é", "\\351")
            .replace("è", "\\350")
            .replace("ê", "\\352")
            .replace("à", "\\340")
            .replace("ù", "\\371")
            .replace("î", "\\356")
            .replace("ô", "\\364")
            .replace("É", "\\311")
            .replace("♭", "b")
            .replace("♯", "#")
            .replace("—", "-")
            .replace("·", "\\267")
        return ascii
    }

    private fun buildSinglePagePdf(streamContent: String): ByteArray {
        val streamBytes = streamContent.encodeToByteArray()
        val objects = listOf(
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R /F3 6 0 R /F4 7 0 R >> >> /Contents 8 0 R >>\nendobj\n",
            "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n",
            "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n",
            "6 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Times-Bold /Encoding /WinAnsiEncoding >>\nendobj\n",
            "7 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Times-Italic /Encoding /WinAnsiEncoding >>\nendobj\n",
            "8 0 obj\n<< /Length ${streamBytes.size} >>\nstream\n$streamContent\nendstream\nendobj\n",
        )

        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")
        val offsets = IntArray(objects.size)
        for (i in objects.indices) {
            offsets[i] = sb.toString().encodeToByteArray().size
            sb.append(objects[i])
        }
        val xrefOffset = sb.toString().encodeToByteArray().size
        sb.append("xref\n")
        sb.append("0 ${objects.size + 1}\n")
        sb.append("0000000000 65535 f \n")
        for (off in offsets) {
            sb.append(off.toString().padStart(10, '0')).append(" 00000 n \n")
        }
        sb.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
        sb.append("startxref\n$xrefOffset\n%%EOF\n")
        return sb.toString().encodeToByteArray()
    }

    private fun stepFrench(step: Step): String = when (step) {
        Step.C -> "Do"
        Step.D -> "Ré"
        Step.E -> "Mi"
        Step.F -> "Fa"
        Step.G -> "Sol"
        Step.A -> "La"
        Step.B -> "Si"
    }

    private fun formatKeyFrench(key: Key): String {
        val acc = when {
            key.tonicAlter > 0 -> "#"
            key.tonicAlter < 0 -> "b"
            else -> ""
        }
        val mode = if (key.mode == Mode.MAJOR) "majeur" else "mineur"
        return "${stepFrench(key.tonicStep)}$acc $mode"
    }

    private fun instrumentDisplayName(instrument: Instrument?): String = when (instrument?.id) {
        null -> "Sons réels (Ut)"
        "flute" -> "Flûte (Ut)"
        "oboe" -> "Hautbois (Ut)"
        "clarinet_bb" -> "Clarinette (Sib)"
        "sax_soprano" -> "Saxophone soprano (Sib)"
        "sax_alto" -> "Saxophone alto (Mib)"
        "sax_tenor" -> "Saxophone ténor (Sib)"
        "sax_baritone" -> "Saxophone baryton (Mib)"
        "trumpet_bb" -> "Trompette (Sib)"
        "horn_f" -> "Cor (Fa)"
        "trombone" -> "Trombone (Ut, clé de Fa)"
        "violin" -> "Violon (Ut)"
        "harp_celtic" -> "Harpe celtique (Ut)"
        "harp_pedal" -> "Harpe à pédales (Ut)"
        "guitar" -> "Guitare (Ut, 8va)"
        "bass_electric" -> "Basse (Ut, clé de Fa)"
        "contrabass" -> "Contrebasse (clé de Fa)"
        "voice_soprano" -> "Voix Soprano"
        "voice_alto" -> "Voix Alto"
        "voice_tenor" -> "Voix Ténor (Sol 8vb)"
        "voice_bass" -> "Voix Basse (clé de Fa)"
        else -> instrument.id
    }
}
