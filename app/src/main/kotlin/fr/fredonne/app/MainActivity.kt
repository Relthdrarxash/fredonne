package fr.fredonne.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.fredonne.core.music.Durations
import fr.fredonne.core.music.Fraction
import fr.fredonne.core.music.Instrument
import fr.fredonne.core.music.Instruments
import fr.fredonne.core.music.Issue
import fr.fredonne.core.music.Key
import fr.fredonne.core.music.MeasureTooLong
import fr.fredonne.core.music.MeasureTooShort
import fr.fredonne.core.music.Mode
import fr.fredonne.core.music.Note
import fr.fredonne.core.music.OutOfRange
import fr.fredonne.core.music.Pitch
import fr.fredonne.core.music.Rest
import fr.fredonne.core.music.Step
import kotlin.math.roundToInt

private val PaperBg = Color(0xFFFAF8F4)
private val CardBg = Color(0xFFFFFFFF)
private val InkColor = Color(0xFF1E1D1A)
private val MutedInk = Color(0xFF6B675E)
private val AccentSage = Color(0xFF285D4D)
private val WarnRed = Color(0xFFB33A3A)
private val WarnAmber = Color(0xFFB87018)
private val CursorTeal = Color(0xFF1F6E8C)

private val FredonneColorScheme = lightColorScheme(
    primary = AccentSage,
    onPrimary = Color.White,
    background = PaperBg,
    surface = CardBg,
    onBackground = InkColor,
    onSurface = InkColor,
    surfaceVariant = Color(0xFFEEECE6),
    onSurfaceVariant = MutedInk,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = FredonneColorScheme) {
                FredonneApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FredonneApp() {
    val context = LocalContext.current
    val prefs = remember { FredonnePrefs(context) }
    var onboarded by remember { mutableStateOf(prefs.onboarded) }
    var state by remember {
        mutableStateOf(
            EditorState(instrument = prefs.preferredInstrumentId?.let(Instruments::byId))
        )
    }

    if (!onboarded) {
        OnboardingScreen(
            initialInstrument = state.instrument,
            onFinish = { instrument, withSample ->
                prefs.preferredInstrumentId = instrument?.id
                prefs.onboarded = true
                state = EditorState(
                    melody = if (withSample) EditorState.sampleMelody() else EditorState.defaultMelody(),
                    instrument = instrument,
                    cursorMeasure = if (withSample) 1 else 0,
                    cursorEventIndex = if (withSample) 3 else 0,
                )
                onboarded = true
            },
        )
        return
    }

    val player = remember { TonePlayer() }
    var isPlaying by remember { mutableStateOf(false) }
    var playingPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    var showKeyDialog by remember { mutableStateOf(false) }
    var showInstrumentDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var baseOctave by remember { mutableIntStateOf(4) }

    DisposableEffect(Unit) {
        onDispose { player.stop() }
    }

    Scaffold(
        containerColor = PaperBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PaperBg,
                    titleContentColor = InkColor,
                ),
                title = {
                    Column {
                        Text(
                            text = "Fredonne",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 19.sp,
                        )
                        Text(
                            text = "${formatKeyLabel(state.displayedMelody.key)} · ${state.melody.time.beats}/${state.melody.time.unit}" +
                                (state.instrument?.let { " · ${instrumentLabel(it)}" } ?: " · Sons réels (Ut)"),
                            fontSize = 12.sp,
                            color = MutedInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                player.stop()
                                isPlaying = false
                                playingPosition = null
                            } else {
                                isPlaying = true
                                player.playMelody(
                                    melody = state.melody,
                                    bpm = state.tempoBpm,
                                    onEventPlaying = { m, e -> playingPosition = m to e },
                                    onFinished = {
                                        isPlaying = false
                                        playingPosition = null
                                    },
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Arrêter" else "Écouter",
                            tint = AccentSage,
                        )
                    }
                    IconButton(
                        onClick = { state = state.undo() },
                        enabled = state.canUndo && !isPlaying,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Annuler")
                    }
                    IconButton(
                        onClick = { state = state.redo() },
                        enabled = state.canRedo && !isPlaying,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Rétablir")
                    }
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "À propos")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AccentSage.copy(alpha = 0.08f),
                            PaperBg,
                            Color(0xFFF3EFE6),
                        ),
                    )
                )
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Barre de réglages : Tonalité / Transposition, Instrument, Exemple / Vider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = { showKeyDialog = true },
                    label = {
                        Text("Tonalité : ${formatKeyLabel(state.melody.key)} (${state.melody.time.beats}/${state.melody.time.unit})")
                    },
                )
                AssistChip(
                    onClick = { showInstrumentDialog = true },
                    label = {
                        Text(state.instrument?.let { instrumentLabel(it) } ?: "Instrument : Concert (Ut)")
                    },
                )
                AssistChip(
                    onClick = { state = state.loadSampleMelody() },
                    leadingIcon = {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    label = { Text("Exemple") },
                )
                AssistChip(
                    onClick = {
                        player.stop()
                        isPlaying = false
                        playingPosition = null
                        state = state.clearAll()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    label = { Text("Effacer") },
                )
            }

            // Portée interactive
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = CardBg,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, Color(0xFFE0DDD5)),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    StaffCanvas(
                        state = state,
                        playingPosition = playingPosition,
                        onSelectCursor = { m, e -> state = state.setCursor(m, e) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    HorizontalDivider(color = Color(0xFFECE9E2))
                    VerifierBar(issues = state.issues, tempoBpm = state.tempoBpm)
                }
            }

            // Barre de tempo compacte + déplacement du curseur
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Tempo ${state.tempoBpm}", fontSize = 12.sp, color = MutedInk, modifier = Modifier.width(72.dp))
                Slider(
                    value = state.tempoBpm.toFloat(),
                    onValueChange = { state = state.setTempo(it.roundToInt()) },
                    valueRange = 50f..160f,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { state = state.moveCursorLeft() },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Curseur gauche")
                }
                IconButton(
                    onClick = { state = state.moveCursorRight() },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Curseur droit")
                }
                IconButton(
                    onClick = { state = state.deleteAtCursor() },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Supprimer note")
                }
            }

            // Barre de durées + silence + point + mesure
            DurationToolbar(
                selectedDuration = state.selectedDuration,
                dotted = state.dotted,
                onSelectDuration = { state = state.setDuration(it) },
                onToggleDotted = { state = state.toggleDotted() },
                onInsertRest = { state = state.insertRest() },
                onAddMeasure = { state = state.addMeasure() },
            )

            // Mini clavier piano pour saisie directe avec retour sonore immédiat
            PianoKeyboard(
                baseOctave = baseOctave,
                onShiftOctave = { delta -> baseOctave = (baseOctave + delta).coerceIn(3, 5) },
                onKeyPressed = { pitch ->
                    player.playSingleNote(pitch.midi)
                    state = state.insertNote(pitch)
                },
            )
        }
    }

    if (showKeyDialog) {
        KeyAndTransposeDialog(
            state = state,
            onDismiss = { showKeyDialog = false },
            onUpdateState = { state = it },
        )
    }

    if (showInstrumentDialog) {
        InstrumentDialog(
            current = state.instrument,
            onSelect = {
                state = state.setInstrument(it)
                showInstrumentDialog = false
            },
            onDismiss = { showInstrumentDialog = false },
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
private fun StaffCanvas(
    state: EditorState,
    playingPosition: Pair<Int, Int>?,
    onSelectCursor: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val melody = state.displayedMelody
    val issues = state.issues
    val textMeasurer = rememberTextMeasurer()
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // Calcul des positions X de chaque mesure et événement
    val clefAreaPx = with(density) { 86.dp.toPx() }
    val eventStepPx = with(density) { 46.dp.toPx() }
    val measurePadPx = with(density) { 26.dp.toPx() }
    val minMeasureWidthPx = with(density) { 128.dp.toPx() }

    val measureLayouts = remember(melody, clefAreaPx, eventStepPx, measurePadPx, minMeasureWidthPx) {
        var x = clefAreaPx
        melody.measures.mapIndexed { mIdx, measure ->
            val startX = x
            val contentWidth = maxOf(minMeasureWidthPx, measure.events.size * eventStepPx + measurePadPx * 2)
            val eventXs = measure.events.indices.map { eIdx ->
                startX + measurePadPx + eIdx * eventStepPx + eventStepPx * 0.45f
            }
            val endX = startX + contentWidth
            x = endX
            MeasureLayout(mIdx, startX, endX, eventXs)
        }
    }

    val totalWidthDp = with(density) {
        maxOf(360f, (measureLayouts.lastOrNull()?.endX ?: clefAreaPx) + 40f).toDp()
    }

    Box(
        modifier = modifier
            .horizontalScroll(scrollState)
            .width(totalWidthDp)
            .fillMaxHeight()
            .pointerInput(measureLayouts, melody) {
                detectTapGestures { offset ->
                    val clickedMeasure = measureLayouts.firstOrNull { offset.x in it.startX..it.endX }
                        ?: measureLayouts.lastOrNull()
                    if (clickedMeasure != null) {
                        val events = clickedMeasure.eventXs
                        val targetEvent = if (events.isEmpty()) {
                            0
                        } else {
                            var idx = events.indexOfFirst { offset.x < it }
                            if (idx == -1) idx = events.size
                            idx
                        }
                        onSelectCursor(clickedMeasure.index, targetEvent)
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val staffTop = size.height * 0.30f
            val lineSpacing = (size.height * 0.085f).coerceIn(12f, 22f)
            val stepHalf = lineSpacing / 2f
            // Ligne du bas (Mi4 en clé de Sol) = diatonic(E4) = 4*7 + 2 = 30
            val bottomLineDiatonic = Pitch(Step.E, 4).diatonic
            val bottomLineY = staffTop + 4 * lineSpacing

            fun yForPitch(pitch: Pitch): Float {
                val delta = pitch.diatonic - bottomLineDiatonic
                return bottomLineY - delta * stepHalf
            }

            // 5 lignes de la portée
            for (line in 0..4) {
                val y = staffTop + line * lineSpacing
                drawLine(
                    color = InkColor.copy(alpha = 0.78f),
                    start = Offset(16f, y),
                    end = Offset(size.width - 16f, y),
                    strokeWidth = 2f,
                )
            }

            // Clé de Sol stylisée + armure + chiffrage
            drawText(
                textMeasurer = textMeasurer,
                text = "G",
                topLeft = Offset(22f, staffTop + lineSpacing * 0.6f),
                style = TextStyle(
                    color = AccentSage,
                    fontSize = 26.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                ),
            )
            val keyFifths = melody.key.fifths
            val accLabel = when {
                keyFifths > 0 -> "${keyFifths}#"
                keyFifths < 0 -> "${-keyFifths}b"
                else -> ""
            }
            if (accLabel.isNotEmpty()) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = accLabel,
                    topLeft = Offset(46f, staffTop - lineSpacing * 0.7f),
                    style = TextStyle(color = MutedInk, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                )
            }
            drawText(
                textMeasurer = textMeasurer,
                text = "${melody.time.beats}\n${melody.time.unit}",
                topLeft = Offset(58f, staffTop + lineSpacing * 0.3f),
                style = TextStyle(
                    color = InkColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 14.sp,
                ),
            )

            // Mesures, barres, notes, silences et curseur
            measureLayouts.forEach { layout ->
                val measure = melody.measures[layout.index]
                val measureIssues = issues.filter { it.measure == layout.index }
                val hasLengthIssue = measureIssues.any { it is MeasureTooShort || it is MeasureTooLong }

                // Soulignement discret si la mesure est incomplète ou trop longue
                if (hasLengthIssue && measure.events.isNotEmpty()) {
                    drawLine(
                        color = WarnAmber.copy(alpha = 0.75f),
                        start = Offset(layout.startX + 4f, bottomLineY + lineSpacing * 2.1f),
                        end = Offset(layout.endX - 4f, bottomLineY + lineSpacing * 2.1f),
                        strokeWidth = 3.5f,
                    )
                }

                // Barre de mesure à droite
                drawLine(
                    color = InkColor.copy(alpha = 0.7f),
                    start = Offset(layout.endX, staffTop),
                    end = Offset(layout.endX, bottomLineY),
                    strokeWidth = if (layout.index == measureLayouts.lastIndex) 3.5f else 2f,
                )

                // Numéro de mesure
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${layout.index + 1}",
                    topLeft = Offset(layout.startX + 6f, staffTop - lineSpacing * 1.4f),
                    style = TextStyle(color = MutedInk, fontSize = 10.sp),
                )

                // Événements
                measure.events.forEachIndexed { eIdx, event ->
                    val cx = layout.eventXs[eIdx]
                    val isPlayingNow = playingPosition == (layout.index to eIdx)
                    val isOutOfRange = measureIssues.any { it is OutOfRange && it.eventIndex == eIdx }

                    when (event) {
                        is Note -> {
                            val cy = yForPitch(event.pitch)
                            val noteColor = when {
                                isPlayingNow -> CursorTeal
                                isOutOfRange -> WarnRed
                                else -> InkColor
                            }
                            // Lignes supplémentaires (en dessous de Mi4 ou au dessus de Fa5)
                            val d = event.pitch.diatonic
                            val lowLineD = bottomLineDiatonic // E4 = 30
                            val highLineD = bottomLineDiatonic + 8 // F5 = 38
                            if (d <= lowLineD - 2) {
                                var ld = lowLineD - 2
                                while (ld >= d) {
                                    val ly = bottomLineY - (ld - bottomLineDiatonic) * stepHalf
                                    drawLine(InkColor, Offset(cx - 13f, ly), Offset(cx + 13f, ly), strokeWidth = 2f)
                                    ld -= 2
                                }
                            } else if (d >= highLineD + 2) {
                                var ld = highLineD + 2
                                while (ld <= d) {
                                    val ly = bottomLineY - (ld - bottomLineDiatonic) * stepHalf
                                    drawLine(InkColor, Offset(cx - 13f, ly), Offset(cx + 13f, ly), strokeWidth = 2f)
                                    ld += 2
                                }
                            }

                            // Altération (# ou b)
                            if (event.pitch.alter != 0) {
                                val symbol = if (event.pitch.alter > 0) "#" else "b"
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = symbol,
                                    topLeft = Offset(cx - 21f, cy - 12f),
                                    style = TextStyle(color = noteColor, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                )
                            }

                            val isWhole = event.written >= Durations.WHOLE
                            val isHalf = event.written >= Durations.HALF && event.written < Durations.WHOLE
                            val rx = lineSpacing * 0.62f
                            val ry = lineSpacing * 0.44f

                            if (isWhole || isHalf) {
                                drawOval(
                                    color = noteColor,
                                    topLeft = Offset(cx - rx, cy - ry),
                                    size = Size(rx * 2, ry * 2),
                                    style = Stroke(width = 3f),
                                )
                            } else {
                                drawOval(
                                    color = noteColor,
                                    topLeft = Offset(cx - rx, cy - ry),
                                    size = Size(rx * 2, ry * 2),
                                )
                            }

                            // Hampe (sauf ronde)
                            if (!isWhole) {
                                val stemUp = d < bottomLineDiatonic + 4 // < Si4
                                val stemX = if (stemUp) cx + rx - 1.5f else cx - rx + 1.5f
                                val stemEndY = if (stemUp) cy - lineSpacing * 2.8f else cy + lineSpacing * 2.8f
                                drawLine(noteColor, Offset(stemX, cy), Offset(stemX, stemEndY), strokeWidth = 2.4f)

                                // Crochets (croche / double-croche)
                                val flags = when {
                                    event.written <= Durations.SIXTEENTH -> 2
                                    event.written <= Durations.EIGHTH -> 1
                                    else -> 0
                                }
                                repeat(flags) { f ->
                                    val fy = if (stemUp) stemEndY + f * 7f else stemEndY - f * 7f
                                    val path = Path().apply {
                                        moveTo(stemX, fy)
                                        quadraticTo(stemX + 12f, fy + if (stemUp) 8f else -8f, stemX + 8f, fy + if (stemUp) 16f else -16f)
                                    }
                                    drawPath(path, color = noteColor, style = Stroke(width = 2.2f))
                                }
                            }

                            // Point de prolongation
                            if (event.written.numerator == 3L && event.written.denominator >= 4L) {
                                drawCircle(noteColor, radius = 3.2f, center = Offset(cx + rx + 6f, cy - 2f))
                            }
                        }
                        is Rest -> {
                            val restColor = if (isPlayingNow) CursorTeal else MutedInk
                            drawRect(
                                color = restColor,
                                topLeft = Offset(cx - 7f, staffTop + lineSpacing * 1.5f),
                                size = Size(14f, 6f),
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "z",
                                topLeft = Offset(cx - 5f, staffTop + lineSpacing * 2.2f),
                                style = TextStyle(color = restColor, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }

                // Curseur d'insertion
                if (state.cursorMeasure == layout.index) {
                    val cursorX = when {
                        measure.events.isEmpty() -> layout.startX + measurePadPx
                        state.cursorEventIndex == 0 -> layout.eventXs.first() - eventStepPx * 0.48f
                        state.cursorEventIndex >= measure.events.size -> layout.eventXs.last() + eventStepPx * 0.48f
                        else -> (layout.eventXs[state.cursorEventIndex - 1] + layout.eventXs[state.cursorEventIndex]) / 2f
                    }
                    drawLine(
                        color = CursorTeal,
                        start = Offset(cursorX, staffTop - lineSpacing * 0.9f),
                        end = Offset(cursorX, bottomLineY + lineSpacing * 0.9f),
                        strokeWidth = 3f,
                    )
                }
            }
        }
    }
}

private data class MeasureLayout(
    val index: Int,
    val startX: Float,
    val endX: Float,
    val eventXs: List<Float>,
)

@Composable
private fun VerifierBar(issues: List<Issue>, tempoBpm: Int) {
    val message = when (val first = issues.firstOrNull()) {
        null -> "Mesures et tessiture conformes · $tempoBpm BPM"
        is MeasureTooShort -> "Mesure ${first.measure + 1} : incomplète (manque ${first.missing.numerator}/${first.missing.denominator})"
        is MeasureTooLong -> "Mesure ${first.measure + 1} : dépassement de ${first.excess.numerator}/${first.excess.denominator}"
        is OutOfRange -> "Mesure ${first.measure + 1} : note ${if (first.tooLow) "trop grave" else "trop aiguë"} pour l'instrument"
    }
    val textColor = when (issues.firstOrNull()) {
        null -> AccentSage
        is OutOfRange -> WarnRed
        else -> WarnAmber
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = message,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (issues.size > 1) {
            Text(
                text = "+${issues.size - 1}",
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DurationToolbar(
    selectedDuration: Fraction,
    dotted: Boolean,
    onSelectDuration: (Fraction) -> Unit,
    onToggleDotted: () -> Unit,
    onInsertRest: () -> Unit,
    onAddMeasure: () -> Unit,
) {
    val durations = listOf(
        Durations.WHOLE to "Ronde",
        Durations.HALF to "Blanche",
        Durations.QUARTER to "Noire",
        Durations.EIGHTH to "Croche",
        Durations.SIXTEENTH to "Double",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        durations.forEach { (dur, label) ->
            FilterChip(
                selected = selectedDuration == dur,
                onClick = { onSelectDuration(dur) },
                label = { Text(label, fontSize = 12.sp) },
            )
        }
        FilterChip(
            selected = dotted,
            onClick = onToggleDotted,
            label = { Text("Pointée", fontSize = 12.sp) },
        )
        OutlinedButton(
            onClick = onInsertRest,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text("Silence", fontSize = 12.sp)
        }
        OutlinedButton(
            onClick = onAddMeasure,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Mesure", fontSize = 12.sp)
        }
    }
}

@Composable
private fun PianoKeyboard(
    baseOctave: Int,
    onShiftOctave: (Int) -> Unit,
    onKeyPressed: (Pitch) -> Unit,
) {
    val whiteSteps = listOf(Step.C, Step.D, Step.E, Step.F, Step.G, Step.A, Step.B)
    val whiteKeys = (0..1).flatMap { octOffset ->
        whiteSteps.map { step -> Pitch(step, baseOctave + octOffset) }
    }
    // Touches noires positionnées entre les touches blanches (index de la touche blanche à gauche)
    val blackKeyDefs = listOf(
        0 to Pitch(Step.C, baseOctave, 1),
        1 to Pitch(Step.D, baseOctave, 1),
        3 to Pitch(Step.F, baseOctave, 1),
        4 to Pitch(Step.G, baseOctave, 1),
        5 to Pitch(Step.A, baseOctave, 1),
        7 to Pitch(Step.C, baseOctave + 1, 1),
        8 to Pitch(Step.D, baseOctave + 1, 1),
        10 to Pitch(Step.F, baseOctave + 1, 1),
        11 to Pitch(Step.G, baseOctave + 1, 1),
        12 to Pitch(Step.A, baseOctave + 1, 1),
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Clavier (Do$baseOctave – Si${baseOctave + 1})",
                fontSize = 12.sp,
                color = MutedInk,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onShiftOctave(-1) },
                    enabled = baseOctave > 3,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("-1 oct", fontSize = 12.sp)
                }
                TextButton(
                    onClick = { onShiftOctave(+1) },
                    enabled = baseOctave < 5,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("+1 oct", fontSize = 12.sp)
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
                .shadow(3.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFFD5D1C7), RoundedCornerShape(10.dp)),
        ) {
            val whiteWidth: Dp = maxWidth / whiteKeys.size
            Row(modifier = Modifier.fillMaxSize()) {
                whiteKeys.forEach { pitch ->
                    Box(
                        modifier = Modifier
                            .width(whiteWidth)
                            .fillMaxHeight()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFF6F4EE), Color.White, Color(0xFFEDEAE2)),
                                )
                            )
                            .border(0.5.dp, Color(0xFFD8D5CC))
                            .clickable { onKeyPressed(pitch) }
                            .padding(bottom = 6.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Text(
                            text = frenchNoteName(pitch.step) + if (pitch.step == Step.C) "${pitch.octave}" else "",
                            fontSize = 10.sp,
                            color = MutedInk,
                        )
                    }
                }
            }

            val blackWidth = whiteWidth * 0.62f
            blackKeyDefs.forEach { (leftWhiteIdx, pitch) ->
                val leftOffset = whiteWidth * (leftWhiteIdx + 1) - blackWidth / 2
                Box(
                    modifier = Modifier
                        .offset(x = leftOffset)
                        .width(blackWidth)
                        .fillMaxHeight(0.58f)
                        .shadow(2.dp, RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                        .clip(RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF34322D), Color(0xFF1A1916)),
                            )
                        )
                        .clickable { onKeyPressed(pitch) },
                )
            }
        }
    }
}

@Composable
private fun KeyAndTransposeDialog(
    state: EditorState,
    onDismiss: () -> Unit,
    onUpdateState: (EditorState) -> Unit,
) {
    val keys = listOf(
        Triple(Step.C, 0, "Do majeur"),
        Triple(Step.G, 0, "Sol majeur (1#)"),
        Triple(Step.D, 0, "Ré majeur (2#)"),
        Triple(Step.A, 0, "La majeur (3#)"),
        Triple(Step.F, 0, "Fa majeur (1b)"),
        Triple(Step.B, -1, "Sib majeur (2b)"),
        Triple(Step.E, -1, "Mib majeur (3b)"),
    )
    val signatures = listOf(4 to 4, 3 to 4, 2 to 4, 6 to 8)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tonalité, chiffrage & transposition") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Transposer la mélodie vers :", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                keys.forEach { (step, alter, label) ->
                    val isCurrent = state.melody.key.tonicStep == step && state.melody.key.tonicAlter == alter
                    OutlinedButton(
                        onClick = { onUpdateState(state.transposeToKey(step, alter)) },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, if (isCurrent) AccentSage else Color(0xFFD5D1C7)),
                    ) {
                        Text(label, color = if (isCurrent) AccentSage else InkColor)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onUpdateState(state.transposeOctave(up = false)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("-1 octave")
                    }
                    OutlinedButton(
                        onClick = { onUpdateState(state.transposeOctave(up = true)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("+1 octave")
                    }
                }
                HorizontalDivider()
                Text("Chiffrage de mesure :", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    signatures.forEach { (b, u) ->
                        FilterChip(
                            selected = state.melody.time.beats == b && state.melody.time.unit == u,
                            onClick = { onUpdateState(state.setTimeSignature(b, u)) },
                            label = { Text("$b/$u") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}

@Composable
private fun InstrumentDialog(
    current: Instrument?,
    onSelect: (Instrument?) -> Unit,
    onDismiss: () -> Unit,
) {
    val options: List<Instrument?> = listOf(null) + Instruments.ALL
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Instrument & transposition écrite") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                options.forEach { inst ->
                    val selected = current?.id == inst?.id
                    OutlinedButton(
                        onClick = { onSelect(inst) },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, if (selected) AccentSage else Color(0xFFD5D1C7)),
                    ) {
                        Text(
                            text = inst?.let { instrumentLabel(it) } ?: "Concert (Ut, sons réels)",
                            color = if (selected) AccentSage else InkColor,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}

@Composable
private fun OnboardingScreen(
    initialInstrument: Instrument?,
    onFinish: (Instrument?, withSample: Boolean) -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var chosenInstrument by remember { mutableStateOf(initialInstrument) }
    val quickInstruments: List<Instrument?> = listOf(
        null,
        Instruments.FLUTE,
        Instruments.CLARINET_BB,
        Instruments.SAX_ALTO,
        Instruments.TRUMPET_BB,
        Instruments.HORN_F,
        Instruments.VIOLIN,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AccentSage.copy(alpha = 0.12f),
                        PaperBg,
                        Color(0xFFF3EFE6),
                    ),
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = CardBg,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, Color(0xFFE0DDD5)),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = if (step == 0) "Bienvenue dans Fredonne" else "Votre instrument de départ",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkColor,
                )
                if (step == 0) {
                    Text(
                        text = "Un bloc-notes mélodique à une voix pour saisir vos idées au clavier virtuel, les écouter aussitôt et transposer sans effort.",
                        fontSize = 15.sp,
                        color = MutedInk,
                        lineHeight = 22.sp,
                    )
                    Text(
                        text = "100 % hors-ligne : aucune permission réseau, aucun compte, aucun traceur. Les règles de solfège vérifient mesures et tessiture sans jamais vous bloquer.",
                        fontSize = 14.sp,
                        color = MutedInk,
                        lineHeight = 20.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        androidx.compose.material3.Button(onClick = { step = 1 }) {
                            Text("Continuer")
                        }
                    }
                } else {
                    Text(
                        text = "La portée affichera automatiquement l'écriture transposée pour cet instrument (modifiable à tout moment).",
                        fontSize = 14.sp,
                        color = MutedInk,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        quickInstruments.forEach { inst ->
                            val selected = chosenInstrument?.id == inst?.id
                            OutlinedButton(
                                onClick = { chosenInstrument = inst },
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, if (selected) AccentSage else Color(0xFFD5D1C7)),
                            ) {
                                Text(
                                    text = inst?.let { instrumentLabel(it) } ?: "Concert (Ut, sons réels)",
                                    color = if (selected) AccentSage else InkColor,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onFinish(chosenInstrument, false) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Portée vierge")
                        }
                        androidx.compose.material3.Button(
                            onClick = { onFinish(chosenInstrument, true) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Avec exemple")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fredonne · Pilmil Studio") },
        text = {
            Text(
                "Bloc-notes mélodique à une voix, 100 % hors-ligne.\n\n" +
                    "Aucune permission réseau, aucun traceur, aucun compte.\n" +
                    "Licence libre MPL-2.0.\n\n" +
                    "Contact : pilmil.studio@proton.me",
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}

private fun frenchNoteName(step: Step): String = when (step) {
    Step.C -> "Do"
    Step.D -> "Ré"
    Step.E -> "Mi"
    Step.F -> "Fa"
    Step.G -> "Sol"
    Step.A -> "La"
    Step.B -> "Si"
}

private fun formatKeyLabel(key: Key): String {
    val alter = when {
        key.tonicAlter > 0 -> "#"
        key.tonicAlter < 0 -> "b"
        else -> ""
    }
    val mode = if (key.mode == Mode.MAJOR) "maj" else "min"
    return "${frenchNoteName(key.tonicStep)}$alter $mode"
}

private fun instrumentLabel(instrument: Instrument): String = when (instrument.id) {
    "flute" -> "Flûte (Ut)"
    "oboe" -> "Hautbois (Ut)"
    "clarinet_bb" -> "Clarinette (Sib)"
    "sax_soprano" -> "Saxophone soprano (Sib)"
    "sax_alto" -> "Saxophone alto (Mib)"
    "sax_tenor" -> "Saxophone ténor (Sib)"
    "sax_baritone" -> "Saxophone baryton (Mib)"
    "trumpet_bb" -> "Trompette (Sib)"
    "horn_f" -> "Cor (Fa)"
    "trombone" -> "Trombone (Ut)"
    "violin" -> "Violon (Ut)"
    "guitar" -> "Guitare (Ut, 8va)"
    "voice_soprano" -> "Voix Soprano"
    "voice_alto" -> "Voix Alto"
    "voice_tenor" -> "Voix Ténor"
    "voice_bass" -> "Voix Basse"
    else -> instrument.id
}
