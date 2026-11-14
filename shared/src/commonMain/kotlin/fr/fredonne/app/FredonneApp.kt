package fr.fredonne.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import fr.fredonne.core.music.Clef
import fr.fredonne.core.music.Direction
import fr.fredonne.core.music.DoubleAlterationOnHarp
import fr.fredonne.core.music.Durations
import fr.fredonne.core.music.Fraction
import fr.fredonne.core.music.HarpPedals
import fr.fredonne.core.music.HighRoughnessInterval
import fr.fredonne.core.music.Instrument
import fr.fredonne.core.music.Instruments
import fr.fredonne.core.music.Issue
import fr.fredonne.core.music.Key
import fr.fredonne.core.music.LeverChangeRequired
import fr.fredonne.core.music.MeasureTooLong
import fr.fredonne.core.music.MeasureTooShort
import fr.fredonne.core.music.Mode
import fr.fredonne.core.music.MusicTheory
import fr.fredonne.core.music.Note
import fr.fredonne.core.music.OutOfRange
import fr.fredonne.core.music.ParallelFifths
import fr.fredonne.core.music.ParallelOctaves
import fr.fredonne.core.music.PedalChangeRequired
import fr.fredonne.core.music.Pitch
import fr.fredonne.core.music.QrCodeEncoder
import fr.fredonne.core.music.Rest
import fr.fredonne.core.music.Step
import fr.fredonne.core.music.VoiceCrossing
import kotlinx.coroutines.delay
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

@Composable
fun FredonneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FredonneColorScheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FredonneApp(
    initialOnboarded: Boolean? = null,
    initialEditorState: EditorState? = null,
) {
    val prefs = rememberFredonnePrefs()
    var onboarded by remember { mutableStateOf(initialOnboarded ?: prefs.onboarded) }
    var state by remember {
        mutableStateOf(
            initialEditorState ?: EditorState(instrument = prefs.preferredInstrumentId?.let(Instruments::byId))
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
    val exporter = rememberScoreExporter()
    var isPlaying by remember { mutableStateOf(false) }
    var playingPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    var showKeyDialog by remember { mutableStateOf(false) }
    var showInstrumentDialog by remember { mutableStateOf(false) }
    var showFormatsDialog by remember { mutableStateOf(false) }
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
                                    timbre = ToneSynthesizer.timbreForInstrument(state.instrument),
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
                    onClick = { showFormatsDialog = true },
                    label = { Text("MusicXML · MIDI · PDF") },
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
                val backspaceInteraction = remember { MutableInteractionSource() }
                val isBackspacePressed by backspaceInteraction.collectIsPressedAsState()
                val latestDelete by rememberUpdatedState { state = state.deleteAtCursor() }
                LaunchedEffect(isBackspacePressed) {
                    if (isBackspacePressed) {
                        latestDelete()
                        delay(360L)
                        while (isBackspacePressed) {
                            latestDelete()
                            delay(90L)
                        }
                    }
                }
                IconButton(
                    onClick = {},
                    interactionSource = backspaceInteraction,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Supprimer note (maintenir pour effacer en continu)")
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
                onShiftOctave = { delta -> baseOctave = (baseOctave + delta).coerceIn(1, 5) },
                onKeyPressed = { pitch ->
                    player.playSingleNote(
                        midi = pitch.midi,
                        timbre = ToneSynthesizer.timbreForInstrument(state.instrument),
                    )
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
            onSelect = { inst ->
                state = state.setInstrument(inst)
                if (inst != null) {
                    baseOctave = when (inst.id) {
                        "bass_electric", "contrabass" -> 1
                        "voice_bass", "trombone" -> 2
                        "voice_tenor", "harp_celtic", "guitar", "horn_f", "sax_baritone" -> 3
                        else -> 4
                    }
                }
                showInstrumentDialog = false
            },
            onDismiss = { showInstrumentDialog = false },
        )
    }

    if (showFormatsDialog) {
        FormatsDialog(
            state = state,
            exporter = exporter,
            onUpdateState = {
                state = it
                showFormatsDialog = false
            },
            onDismiss = { showFormatsDialog = false },
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
    val verticalScrollState = rememberScrollState()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(320f)
        val viewportHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(150f)

        val clefAreaPx = with(density) { 82.dp.toPx() }
        val eventStepPx = with(density) { 42.dp.toPx() }
        val measurePadPx = with(density) { 20.dp.toPx() }
        val minMeasureWidthPx = with(density) { 112.dp.toPx() }
        val rightMarginPx = with(density) { 16.dp.toPx() }
        val baseSystemHeightPx = with(density) { 142.dp.toPx() }

        // Découpage automatique en lignes de portée (systèmes) dès qu'une mesure dépasse la largeur de l'écran
        val systemLayouts = remember(
            melody,
            viewportWidthPx,
            clefAreaPx,
            eventStepPx,
            measurePadPx,
            minMeasureWidthPx,
            rightMarginPx,
        ) {
            val usableLineWidth = (viewportWidthPx - clefAreaPx - rightMarginPx).coerceAtLeast(minMeasureWidthPx)

            data class RawMeasureSpec(
                val index: Int,
                val eventSteps: List<Float>,
                val naturalWidth: Float,
            )

            val specs = melody.measures.mapIndexed { mIdx, measure ->
                val steps = measure.events.map { event ->
                    eventStepPx * MusicTheory.gourlaySpacingFactor(event.length)
                }
                val natW = maxOf(minMeasureWidthPx, steps.sum() + measurePadPx * 2f)
                RawMeasureSpec(mIdx, steps, natW)
            }

            val rows = mutableListOf<MutableList<RawMeasureSpec>>()
            var currentRow = mutableListOf<RawMeasureSpec>()
            var currentRowWidth = 0f

            for (spec in specs) {
                if (currentRow.isNotEmpty() && currentRowWidth + spec.naturalWidth > usableLineWidth) {
                    rows += currentRow
                    currentRow = mutableListOf(spec)
                    currentRowWidth = spec.naturalWidth
                } else {
                    currentRow += spec
                    currentRowWidth += spec.naturalWidth
                }
            }
            if (currentRow.isNotEmpty()) rows += currentRow

            rows.mapIndexed { sysIdx, rowSpecs ->
                val rawSum = rowSpecs.sumOf { it.naturalWidth.toDouble() }.toFloat().coerceAtLeast(1f)
                // Si la ligne comporte plusieurs mesures ou remplit plus de 75 % de la largeur, on l'étire jusqu'à la marge droite
                val stretch = if (rowSpecs.size > 1 || rawSum > usableLineWidth * 0.75f) {
                    (usableLineWidth / rawSum).coerceIn(0.85f, 1.35f)
                } else {
                    1.0f
                }

                var x = clefAreaPx
                val layouts = rowSpecs.map { spec ->
                    val startX = x
                    val mWidth = spec.naturalWidth * stretch
                    val pad = measurePadPx * stretch
                    var cursorOffset = startX + pad
                    val eventXs = spec.eventSteps.map { stepW ->
                        val scaledStep = stepW * stretch
                        val cx = cursorOffset + scaledStep * 0.45f
                        cursorOffset += scaledStep
                        cx
                    }
                    val endX = startX + mWidth
                    x = endX
                    MeasureLayout(spec.index, sysIdx, startX, endX, eventXs)
                }
                SystemLayout(sysIdx, layouts)
            }
        }

        val systemHeightPx = if (systemLayouts.size <= 1) {
            maxOf(baseSystemHeightPx, viewportHeightPx)
        } else {
            baseSystemHeightPx
        }
        val totalCanvasHeightDp = with(density) {
            (systemLayouts.size * systemHeightPx + 12f).toDp()
        }

        // Autoscroll vertical vers la ligne de portée active lors de la saisie ou de la lecture
        val activeMeasureIndex = playingPosition?.first ?: state.cursorMeasure
        LaunchedEffect(activeMeasureIndex, state.cursorEventIndex, systemLayouts.size) {
            val activeSystemIdx = systemLayouts.indexOfFirst { sys ->
                sys.measures.any { it.index == activeMeasureIndex }
            }
            if (activeSystemIdx >= 0 && systemLayouts.size > 1) {
                val sysTop = (activeSystemIdx * systemHeightPx).roundToInt()
                val sysBottom = ((activeSystemIdx + 1) * systemHeightPx).roundToInt()
                val viewTop = verticalScrollState.value
                val viewBottom = viewTop + viewportHeightPx.roundToInt()
                if (sysBottom > viewBottom) {
                    verticalScrollState.animateScrollTo((sysBottom - viewportHeightPx.roundToInt()).coerceAtLeast(0))
                } else if (sysTop < viewTop) {
                    verticalScrollState.animateScrollTo(sysTop)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalCanvasHeightDp)
                    .pointerInput(systemLayouts, melody, systemHeightPx) {
                        detectTapGestures { offset ->
                            val sysIdx = (offset.y / systemHeightPx).toInt().coerceIn(0, (systemLayouts.size - 1).coerceAtLeast(0))
                            val system = systemLayouts.getOrNull(sysIdx)
                            val clickedMeasure = system?.measures?.firstOrNull { offset.x in it.startX..it.endX }
                                ?: system?.measures?.lastOrNull()
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
                val lineSpacing = (systemHeightPx * 0.095f).coerceIn(12f, 20f)
                val stepHalf = lineSpacing / 2f
                val clef = Clef.resolveFor(melody, state.instrument)
                val bottomLineDiatonic = clef.bottomLinePitch.diatonic

                systemLayouts.forEach { system ->
                    val sysTopOffset = system.systemIndex * systemHeightPx
                    val staffTop = sysTopOffset + systemHeightPx * 0.28f
                    val bottomLineY = staffTop + 4 * lineSpacing
                    val systemEndX = system.measures.lastOrNull()?.endX ?: (size.width - 16f)

                    fun yForPitch(pitch: Pitch): Float {
                        val delta = pitch.diatonic - bottomLineDiatonic
                        return bottomLineY - delta * stepHalf
                    }

                    // 5 lignes de la portée pour ce système
                    for (line in 0..4) {
                        val y = staffTop + line * lineSpacing
                        drawLine(
                            color = InkColor.copy(alpha = 0.78f),
                            start = Offset(16f, y),
                            end = Offset(systemEndX, y),
                            strokeWidth = 2f,
                        )
                    }

                    // Barre de début de système
                    drawLine(
                        color = AccentSage.copy(alpha = 0.85f),
                        start = Offset(16f, staffTop),
                        end = Offset(16f, bottomLineY),
                        strokeWidth = 2.6f,
                    )

                    // Clé (Sol, Sol 8vb ou Fa) + armure + chiffrage
                    drawText(
                        textMeasurer = textMeasurer,
                        text = clef.displaySymbol,
                        topLeft = Offset(20f, staffTop + lineSpacing * (if (clef == Clef.BASS) 0.15f else 0.55f)),
                        style = TextStyle(
                            color = AccentSage,
                            fontSize = if (clef == Clef.TREBLE) 25.sp else 19.sp,
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
                            topLeft = Offset(44f, staffTop - lineSpacing * 0.7f),
                            style = TextStyle(color = MutedInk, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                    if (system.systemIndex == 0) {
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "${melody.time.beats}\n${melody.time.unit}",
                            topLeft = Offset(58f, staffTop + lineSpacing * 0.25f),
                            style = TextStyle(
                                color = InkColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 13.sp,
                            ),
                        )
                    }

                    // Mesures, barres, notes, silences et curseur
                    system.measures.forEach { layout ->
                        val measure = melody.measures[layout.index]
                        val measureIssues = issues.filter { it.measure == layout.index }
                        val hasLengthIssue = measureIssues.any { it is MeasureTooShort || it is MeasureTooLong }

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
                            strokeWidth = if (layout.index == melody.measures.lastIndex) 3.5f else 2f,
                        )

                        // Numéro de mesure
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "${layout.index + 1}",
                            topLeft = Offset(layout.startX + 6f, staffTop - lineSpacing * 1.35f),
                            style = TextStyle(color = MutedInk, fontSize = 10.sp),
                        )

                        measure.events.forEachIndexed { eIdx, event ->
                            val cx = layout.eventXs[eIdx]
                            val isPlayingNow = playingPosition == (layout.index to eIdx)
                            val isOutOfRange = measureIssues.any {
                                (it is OutOfRange && it.eventIndex == eIdx) ||
                                    (it is DoubleAlterationOnHarp && it.eventIndex == eIdx)
                            }
                            val isLeverChange = measureIssues.any { it is LeverChangeRequired && it.eventIndex == eIdx }
                            val pedalIssue = measureIssues.filterIsInstance<PedalChangeRequired>().firstOrNull { it.eventIndex == eIdx }

                            when (event) {
                                is Note -> {
                                    val cy = yForPitch(event.pitch)
                                    val noteColor = when {
                                        isPlayingNow -> CursorTeal
                                        isOutOfRange -> WarnRed
                                        isLeverChange || pedalIssue != null -> WarnAmber
                                        else -> InkColor
                                    }
                                    val d = event.pitch.diatonic
                                    val lowLineD = bottomLineDiatonic
                                    val highLineD = bottomLineDiatonic + 8
                                    if (d <= lowLineD - 2) {
                                        var ld = lowLineD - 2
                                        while (ld >= d) {
                                            val ly = bottomLineY - (ld - bottomLineDiatonic) * stepHalf
                                            drawLine(InkColor, Offset(cx - 12f, ly), Offset(cx + 12f, ly), strokeWidth = 2f)
                                            ld -= 2
                                        }
                                    } else if (d >= highLineD + 2) {
                                        var ld = highLineD + 2
                                        while (ld <= d) {
                                            val ly = bottomLineY - (ld - bottomLineDiatonic) * stepHalf
                                            drawLine(InkColor, Offset(cx - 12f, ly), Offset(cx + 12f, ly), strokeWidth = 2f)
                                            ld += 2
                                        }
                                    }

                                    if (event.pitch.alter != 0) {
                                        val symbol = if (event.pitch.alter > 0) "#" else "b"
                                        drawText(
                                            textMeasurer = textMeasurer,
                                            text = symbol,
                                            topLeft = Offset(cx - 20f, cy - 11f),
                                            style = TextStyle(color = noteColor, fontSize = 12.sp, fontWeight = FontWeight.Bold),
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
                                            style = Stroke(width = 2.8f),
                                        )
                                    } else {
                                        drawOval(
                                            color = noteColor,
                                            topLeft = Offset(cx - rx, cy - ry),
                                            size = Size(rx * 2, ry * 2),
                                        )
                                    }

                                    if (!isWhole) {
                                        val stemUp = d < bottomLineDiatonic + 4
                                        val stemX = if (stemUp) cx + rx - 1.5f else cx - rx + 1.5f
                                        val stemEndY = if (stemUp) cy - lineSpacing * 2.75f else cy + lineSpacing * 2.75f
                                        drawLine(noteColor, Offset(stemX, cy), Offset(stemX, stemEndY), strokeWidth = 2.3f)

                                        val flags = when {
                                            event.written <= Durations.SIXTEENTH -> 2
                                            event.written <= Durations.EIGHTH -> 1
                                            else -> 0
                                        }
                                        repeat(flags) { f ->
                                            val fy = if (stemUp) stemEndY + f * 6.5f else stemEndY - f * 6.5f
                                            val path = Path().apply {
                                                moveTo(stemX, fy)
                                                quadraticTo(stemX + 11f, fy + if (stemUp) 7f else -7f, stemX + 7f, fy + if (stemUp) 15f else -15f)
                                            }
                                            drawPath(path, color = noteColor, style = Stroke(width = 2.1f))
                                        }
                                    }

                                    if (event.written.numerator == 3L && event.written.denominator >= 4L) {
                                        drawCircle(noteColor, radius = 3.0f, center = Offset(cx + rx + 5.5f, cy - 2f))
                                    }

                                    if (pedalIssue != null) {
                                        drawText(
                                            textMeasurer = textMeasurer,
                                            text = pedalIssue.pedalLabel,
                                            topLeft = Offset(cx - 12f, bottomLineY + lineSpacing * 0.55f),
                                            style = TextStyle(color = WarnAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        )
                                    }
                                }
                                is Rest -> {
                                    val restColor = if (isPlayingNow) CursorTeal else MutedInk
                                    drawRect(
                                        color = restColor,
                                        topLeft = Offset(cx - 6.5f, staffTop + lineSpacing * 1.5f),
                                        size = Size(13f, 5.5f),
                                    )
                                    drawText(
                                        textMeasurer = textMeasurer,
                                        text = "z",
                                        topLeft = Offset(cx - 4.5f, staffTop + lineSpacing * 2.2f),
                                        style = TextStyle(color = restColor, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    )
                                }
                            }
                        }

                        // Curseur d'insertion
                        if (state.cursorMeasure == layout.index) {
                            val cursorX = when {
                                measure.events.isEmpty() -> layout.startX + measurePadPx
                                state.cursorEventIndex == 0 -> layout.eventXs.first() - eventStepPx * 0.42f
                                state.cursorEventIndex >= measure.events.size -> layout.eventXs.last() + eventStepPx * 0.42f
                                else -> (layout.eventXs[state.cursorEventIndex - 1] + layout.eventXs[state.cursorEventIndex]) / 2f
                            }
                            drawLine(
                                color = CursorTeal,
                                start = Offset(cursorX, staffTop - lineSpacing * 0.85f),
                                end = Offset(cursorX, bottomLineY + lineSpacing * 0.85f),
                                strokeWidth = 3f,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class MeasureLayout(
    val index: Int,
    val systemIndex: Int,
    val startX: Float,
    val endX: Float,
    val eventXs: List<Float>,
)

private data class SystemLayout(
    val systemIndex: Int,
    val measures: List<MeasureLayout>,
)

@Composable
private fun VerifierBar(issues: List<Issue>, tempoBpm: Int) {
    val message = when (val first = issues.firstOrNull()) {
        null -> "Mesures et tessiture conformes · $tempoBpm BPM"
        is MeasureTooShort -> "Mesure ${first.measure + 1} : incomplète (manque ${first.missing.numerator}/${first.missing.denominator})"
        is MeasureTooLong -> "Mesure ${first.measure + 1} : dépassement de ${first.excess.numerator}/${first.excess.denominator}"
        is OutOfRange -> "Mesure ${first.measure + 1} : note ${if (first.tooLow) "trop grave" else "trop aiguë"} pour l'instrument"
        is DoubleAlterationOnHarp -> "Mesure ${first.measure + 1} : double altération impossible à la harpe"
        is LeverChangeRequired -> "Mesure ${first.measure + 1} : altération hors armure (changement de levier requis)"
        is PedalChangeRequired -> "Mesure ${first.measure + 1} : pédale ${first.pedalLabel} (toutes les octaves)"
        is ParallelFifths -> "Mesure ${first.measure + 1} : quintes parallèles détectées"
        is ParallelOctaves -> "Mesure ${first.measure + 1} : octaves parallèles détectées"
        is VoiceCrossing -> "Mesure ${first.measure + 1} : croisement de voix"
        is HighRoughnessInterval -> "Mesure ${first.measure + 1} : intervalle à forte rugosité acoustique"
    }
    val textColor = when (issues.firstOrNull()) {
        null -> AccentSage
        is OutOfRange, is ParallelFifths, is ParallelOctaves -> WarnRed
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
                    enabled = baseOctave > 1,
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
    var selectedDirection by remember { mutableStateOf(Direction.NEAREST) }
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
                Text(
                    "Ajuster la hauteur à l'oreille (voix / confort) :",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedButton(
                        onClick = { onUpdateState(state.transposeSemitones(-2)) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    ) {
                        Text("-1 ton", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onUpdateState(state.transposeSemitones(-1)) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    ) {
                        Text("-½ ton", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onUpdateState(state.transposeSemitones(1)) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    ) {
                        Text("+½ ton", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onUpdateState(state.transposeSemitones(2)) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    ) {
                        Text("+1 ton", fontSize = 12.sp)
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
                Text("Transposer la mélodie vers une tonalité :", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Direction.NEAREST to "Plus proche",
                        Direction.UP to "Monter ↑",
                        Direction.DOWN to "Descendre ↓",
                    ).forEach { (dir, label) ->
                        FilterChip(
                            selected = selectedDirection == dir,
                            onClick = { selectedDirection = dir },
                            label = { Text(label, fontSize = 11.sp) },
                        )
                    }
                }
                keys.forEach { (step, alter, label) ->
                    val isCurrent = state.melody.key.tonicStep == step && state.melody.key.tonicAlter == alter
                    OutlinedButton(
                        onClick = { onUpdateState(state.transposeToKey(step, alter, selectedDirection)) },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, if (isCurrent) AccentSage else Color(0xFFD5D1C7)),
                    ) {
                        Text(label, color = if (isCurrent) AccentSage else InkColor)
                    }
                }
                OutlinedButton(
                    onClick = { onUpdateState(state.applyDetectedKey()) },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, AccentSage.copy(alpha = 0.6f)),
                ) {
                    Text(
                        "Tonalité estimée : ${formatKeyLabel(state.detectedKey)} (adapter l'armure)",
                        color = AccentSage,
                        fontSize = 12.sp,
                    )
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
private fun FormatsDialog(
    state: EditorState,
    exporter: ScoreExporter,
    onUpdateState: (EditorState) -> Unit,
    onDismiss: () -> Unit,
) {
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var shareCodeText by remember(state.melody, state.instrument, state.tempoBpm) {
        mutableStateOf(state.exportShareCode())
    }
    val qrMatrix = remember(shareCodeText) {
        runCatching { QrCodeEncoder.encode(shareCodeText) }.getOrNull()
    }
    var xmlText by remember(state.melody, state.instrument) {
        mutableStateOf(state.exportMusicXml())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Formats ouverts & QR Code hors-ligne") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Exportez la partition (avec transposition instrumentale et QR code de reprise sur le PDF) ou importez une partition hors-ligne.",
                    fontSize = 13.sp,
                    color = MutedInk,
                )
                OutlinedButton(
                    onClick = {
                        val xml = state.exportMusicXml()
                        xmlText = xml
                        val path = exporter.exportBytes(
                            fileName = "fredonne_partition.musicxml",
                            mimeType = "application/vnd.recordare.musicxml+xml",
                            bytes = xml.encodeToByteArray(),
                        )
                        statusMessage = "MusicXML 4.0 exporté ($path)"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Exporter en MusicXML (.musicxml)")
                }
                OutlinedButton(
                    onClick = {
                        val midi = state.exportMidi()
                        val path = exporter.exportBytes(
                            fileName = "fredonne_melodie.mid",
                            mimeType = "audio/midi",
                            bytes = midi,
                        )
                        statusMessage = "MIDI standard exporté (${midi.size} octets · $path)"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Exporter en MIDI (.mid)")
                }
                OutlinedButton(
                    onClick = {
                        val pdf = state.exportPdf()
                        val path = exporter.exportBytes(
                            fileName = "fredonne_partition.pdf",
                            mimeType = "application/pdf",
                            bytes = pdf,
                        )
                        statusMessage = "PDF A4 avec QR code exporté (${pdf.size} octets · $path)"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Exporter la partition en PDF (.pdf)")
                }

                HorizontalDivider()
                Text("QR Code & code de reprise rapide (imprimé sur le PDF) :", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                if (qrMatrix != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(132.dp)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFD8D5CC), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                        ) {
                            val cellW = size.width / qrMatrix.size
                            val cellH = size.height / qrMatrix.size
                            for (y in 0 until qrMatrix.size) {
                                for (x in 0 until qrMatrix.size) {
                                    if (qrMatrix[x, y]) {
                                        drawRect(
                                            color = InkColor,
                                            topLeft = Offset(x * cellW, y * cellH),
                                            size = Size(cellW + 0.5f, cellH + 0.5f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = shareCodeText,
                    onValueChange = { shareCodeText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                )
                OutlinedButton(
                    onClick = {
                        runCatching { state.importShareCode(shareCodeText) }
                            .onSuccess { updated -> onUpdateState(updated) }
                            .onFailure { statusMessage = "Erreur code QR : ${it.message ?: "code invalide"}" }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, AccentSage),
                ) {
                    Text("Reprendre la partition depuis ce code QR", color = AccentSage)
                }

                HorizontalDivider()
                Text("Aperçu / Import MusicXML 4.0 :", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                OutlinedTextField(
                    value = xmlText,
                    onValueChange = { xmlText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                )
                OutlinedButton(
                    onClick = {
                        runCatching { state.importMusicXml(xmlText) }
                            .onSuccess { updated -> onUpdateState(updated) }
                            .onFailure { statusMessage = "Erreur MusicXML : ${it.message ?: "format non reconnu"}" }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, AccentSage),
                ) {
                    Text("Importer ce MusicXML sur la portée", color = AccentSage)
                }

                statusMessage?.let { msg ->
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        color = AccentSage,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
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
    "trombone" -> "Trombone (Ut, Clé de Fa)"
    "violin" -> "Violon (Ut)"
    "harp_celtic" -> "Harpe celtique (Ut, diatonique)"
    "harp_pedal" -> "Harpe à pédales (Concert, 47 cordes)"
    "guitar" -> "Guitare (Ut, 8va)"
    "bass_electric" -> "Basse électrique (Clé de Fa, 8vb)"
    "contrabass" -> "Contrebasse (Clé de Fa, 8vb)"
    "voice_soprano" -> "Voix Soprano"
    "voice_alto" -> "Voix Alto"
    "voice_tenor" -> "Voix Ténor (Sol 8vb)"
    "voice_bass" -> "Voix Basse (Clé de Fa)"
    else -> instrument.id
}
