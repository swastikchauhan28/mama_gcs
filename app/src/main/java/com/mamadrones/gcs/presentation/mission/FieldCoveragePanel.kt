@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mamadrones.gcs.presentation.mission

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.mamadrones.gcs.domain.model.*
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.math.min
import kotlin.math.ceil
import kotlin.math.floor

/** Volatile preview only. Input/outline changes cancel work and hide its old result immediately. */
@Stable
internal class FieldCoverageState(
    private val scope: CoroutineScope,
    private val outline: List<DraftWaypoint>,
    val available: Boolean,
) {
    var width by mutableStateOf(""); private set
    var overlap by mutableStateOf("10"); private set
    var headland by mutableStateOf("0"); private set
    var eastWest by mutableStateOf(false); private set
    var result by mutableStateOf<CoverageCalculation?>(null); private set
    var comparison by mutableStateOf<CoverageDirectionComparison?>(null); private set
    var busy by mutableStateOf(false); private set
    private var job: Job? = null
    val inputError: String? get() = FieldCoveragePlanner.settingsError(width.trim().toDoubleOrNull(), overlap.trim().toDoubleOrNull(), headland.trim().toDoubleOrNull())
    fun updateWidth(value: String) { invalidate(); width = value.take(16) }
    fun updateOverlap(value: String) { invalidate(); overlap = value.take(16) }
    fun updateHeadland(value: String) { invalidate(); headland = value.take(16) }
    fun setDirection(value: Boolean) { invalidate(); eastWest = value }
    fun invalidate() { job?.cancel(); job = null; busy = false; result = null; comparison = null }
    fun generate() {
        if (!available || inputError != null || busy) {
            return
        }
        val requestedWidth = width.trim().toDouble()
        val requestedOverlap = overlap.trim().toDouble()
        val requestedHeadland = headland.trim().toDouble()
        val requestedDirection = eastWest
        busy = true
        result = null
        job = scope.launch {
            val calculated = withContext(Dispatchers.Default) {
                FieldCoveragePlanner.calculate(outline, requestedWidth, requestedOverlap, requestedDirection, requestedHeadland)
            }
            ensureActive()
            result = calculated
            busy = false
        }
    }
    fun compareDirections() {
        if (!available || inputError != null || busy) {
            return
        }
        val requestedWidth = width.trim().toDouble()
        val requestedOverlap = overlap.trim().toDouble()
        val requestedHeadland = headland.trim().toDouble()
        comparison = null
        busy = true
        job = scope.launch {
            val calculated = withContext(Dispatchers.Default) {
                CoverageDirectionComparisonPlanner.calculate(outline, requestedWidth, requestedOverlap, requestedHeadland)
            }
            ensureActive()
            comparison = calculated
            busy = false
        }
    }
}

@Composable
internal fun rememberFieldCoverageState(outline: List<DraftWaypoint>, available: Boolean): FieldCoverageState {
    val scope = rememberCoroutineScope()
    val state = remember(outline, available) { FieldCoverageState(scope, outline.toList(), available) }
    DisposableEffect(state) { onDispose { state.invalidate() } }
    return state
}

@Composable
internal fun FieldCoverageInputs(state: FieldCoverageState) {
    var showSettings by remember(state) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("coverage-inputs"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Field coverage preview", style = MaterialTheme.typography.titleMedium)
        Text("Uses your Field outline. Enter the tool's working width to preview separate passes. This preview is not saved as a route.")
        if (state.inputError == null) Text("Tool: ${state.width} m · Overlap: ${state.overlap}% · Headland: ${state.headland} m · ${if (state.eastWest) "East / west" else "North / south"}")
        if (!state.available) Text("Wait for the field draft to load before previewing coverage.")
        Button(onClick = { showSettings = true }, enabled = state.available && !state.busy,
            modifier = Modifier.testTag("coverage-settings")) { Text("Coverage settings") }
        OutlinedButton(onClick = state::compareDirections, enabled = state.available && state.inputError == null && !state.busy,
            modifier = Modifier.testTag("coverage-compare")) { Text("Compare directions") }
        if (state.busy) {
            Text("Calculating…")
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        title = { Text("Coverage settings") },
        text = {
        // Use the dialog's keyboard controller: the parent window cannot reliably dismiss its IME.
        val keyboard = LocalSoftwareKeyboardController.current
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(state.width, state::updateWidth, label = { Text("Tool width (m)") },
            supportingText = { Text("Working width, not rover width · 0.1–50 m") },
            singleLine = true, enabled = state.available,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().testTag("coverage-width"))
        OutlinedTextField(state.overlap, state::updateOverlap, label = { Text("Overlap (%)") },
            supportingText = { Text("Planning allowance · 0–80%") }, singleLine = true, enabled = state.available,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().testTag("coverage-overlap"))
        OutlinedTextField(state.headland, state::updateHeadland, label = { Text("Headland width (m)") },
            supportingText = { Text("Reserved inside the boundary · 0–100 m") }, singleLine = true, enabled = state.available,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().testTag("coverage-headland"))
        Text("Pass direction", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(!state.eastWest, { state.setDirection(false) }, { Text("North / south") },
                enabled = state.available, modifier = Modifier.testTag("coverage-north-south"))
            FilterChip(state.eastWest, { state.setDirection(true) }, { Text("East / west") },
                enabled = state.available, modifier = Modifier.testTag("coverage-east-west"))
        }
        state.inputError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("coverage-input-error")) }
        }
        },
        confirmButton = {
            TextButton(onClick = { state.generate(); showSettings = false }, enabled = state.available && state.inputError == null && !state.busy,
                modifier = Modifier.testTag("coverage-generate")) { Text("Generate preview") }
        },
        dismissButton = { TextButton(onClick = { showSettings = false }, modifier = Modifier.testTag("coverage-close-settings")) { Text("Close") } },
    )
}

@Composable
internal fun FieldCoverageComparisonPanel(comparison: CoverageDirectionComparison?) {
    if (comparison == null) return
    Column {
        FieldCoverageComparisonHeader(comparison)
        FieldCoverageComparisonCards(comparison)
    }
}

@Composable
internal fun FieldCoverageComparisonHeader(comparison: CoverageDirectionComparison?) {
    if (comparison == null) return
    Column(Modifier.fillMaxWidth().testTag("coverage-comparison"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Compare directions · preview only", style = MaterialTheme.typography.titleMedium)
        Text("Same field and settings. Swipe sideways to see both cards. No direction is selected automatically.")
        Text("Working length excludes turns. Ranges describe modeled gaps, not actual soil or positioning accuracy. Overlapping ranges do not show which direction has fewer gaps.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("coverage-comparison-warning"))
        Text("Diagrams: green is planned strips, pink is unplanned area, amber is reserved headland.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun FieldCoverageComparisonCards(comparison: CoverageDirectionComparison?) {
    if (comparison == null) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // At enlarged text sizes, use a full-width card to keep labels readable.
        val cardWidth = if (LocalDensity.current.fontScale >= 1.8f) maxWidth.coerceAtLeast(240.dp)
            else ((maxWidth - 8.dp) / 2).coerceAtLeast(180.dp)
        Row(Modifier.horizontalScroll(rememberScrollState()).testTag("coverage-comparison-row"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DirectionComparisonCard("North / south", "north", comparison.northSouth, cardWidth)
            DirectionComparisonCard("East / west", "east", comparison.eastWest, cardWidth)
        }
    }
}

@Composable
private fun DirectionComparisonCard(title: String, id: String, calculation: CoverageCalculation, width: Dp) {
    Surface(Modifier.width(width).testTag("coverage-comparison-$id"), tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            when (calculation) {
                is CoverageCalculation.Invalid -> Text("Unavailable: ${calculation.reason}", color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("coverage-comparison-$id-error"))
                is CoverageCalculation.Ready -> {
                    val preview = calculation.preview
                    Text("${preview.passes.size} working passes", modifier = Modifier.testTag("coverage-comparison-$id-passes"))
                    Text(String.format(Locale.US, "Working length: %.1f m", preview.workingLengthMeters), modifier = Modifier.testTag("coverage-comparison-$id-length"))
                    val area = preview.areaEstimate
                    Text(if (area == null) "Modeled unplanned range: unavailable" else
                        "Modeled unplanned range: ${coverageRange(area.mainGapMinimum, area.mainGapMaximum)} m²",
                        modifier = Modifier.testTag("coverage-comparison-$id-range"))
                    CoverageDiagram(preview, 150.dp, "coverage-comparison-$id-diagram")
                }
            }
        }
    }
}

@Composable
internal fun FieldCoverageResult(result: CoverageCalculation?, diagramHeight: Dp = 260.dp) {
    when (result) {
        null -> Text("Generate a preview after setting the width and overlap. Changes clear the previous preview.",
            modifier = Modifier.testTag("coverage-empty"))
        is CoverageCalculation.Invalid -> Text(result.reason, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("coverage-error"))
        is CoverageCalculation.Ready -> {
            val preview = result.preview
            Column(Modifier.fillMaxWidth().testTag("coverage-result"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${preview.passes.size} working passes · preview only", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("coverage-pass-count"))
                Text(String.format(Locale.US, "Maximum pass spacing: %.2f m", preview.maximumSpacingMeters),
                    modifier = Modifier.testTag("coverage-spacing"))
                Text(String.format(Locale.US, "Reserved headland: %.2f m", preview.headlandMeters), modifier = Modifier.testTag("coverage-headland-result"))
                Text(String.format(Locale.US, "Field: %.1f m² · Working length: %.1f m", preview.fieldAreaSquareMeters, preview.workingLengthMeters))
                val estimate = preview.areaEstimate
                if (estimate == null) {
                    Text("Area estimates unavailable: no grid samples fell inside this field.", modifier = Modifier.testTag("coverage-area-unavailable"))
                } else {
                    val headlandArea = if (preview.headlandMeters == 0.0) "none (0 m width)" else
                        if (estimate.headlandSamples == 0) "none detected" else String.format(Locale.US, "≈ %.3g m²", estimate.headlandSquareMeters)
                    val gapArea = if (estimate.unplannedMainSamples == 0) "none detected" else String.format(Locale.US, "≈ %.3g m²", estimate.unplannedMainSquareMeters)
                    Text("Estimated reserved headland: $headlandArea", modifier = Modifier.testTag("coverage-area-headland"))
                    Text("Estimated unplanned main area: $gapArea", modifier = Modifier.testTag("coverage-area-gap"))
                    Text("Modeled headland range: ${coverageRange(estimate.headlandMinimum, estimate.headlandMaximum)} m²", modifier = Modifier.testTag("coverage-range-headland"))
                    Text("Modeled unplanned main range: ${coverageRange(estimate.mainGapMinimum, estimate.mainGapMaximum)} m²", modifier = Modifier.testTag("coverage-range-gap"))
                    Text("Ranges include uncertain grid cells. They describe the model, not actual soil or positioning accuracy.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("coverage-range-warning"))
                    Text(String.format(Locale.US, "Sampling cells: %.4f × %.4f m. Estimates can be inaccurate. Narrow gaps may be missed; none detected does not mean complete coverage.", estimate.cellWidthMeters, estimate.cellHeightMeters),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("coverage-area-resolution"))
                }
                Text("North ↑ · Local field diagram", style = MaterialTheme.typography.labelLarge)
                CoverageDiagram(preview, diagramHeight)
                Text("Green: planned tool strips. Pink: unplanned main area. Amber: reserved headland. Arrows: working direction.")
                Text("The headland is not worked by these passes. Conservative clearance may leave extra gaps near corners and narrow areas.")
                Text("Strips are clipped to the field in this diagram. Pink corners and edges need extra passes. Small gaps may be hard to see at this scale.")
                Text("Turning space and connecting paths are not planned. Tool overhang, obstacles, slope and position error still need review. This diagram does not confirm that soil has been worked.",
                    style = MaterialTheme.typography.bodySmall)
                Text("A final pass may be closer than the maximum spacing to cover the far edge.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Round outward, including a small allowance for floating-point area accumulation. */
internal fun coverageRange(minimum: Double, maximum: Double): String {
    val low = if (minimum == 0.0) 0.0 else floor(maxOf(0.0, minimum - 1e-7) * 10) / 10
    val high = if (maximum == 0.0) 0.0 else ceil((maximum + 1e-7) * 10) / 10
    return String.format(Locale.US, "%.1f–%.1f", low, high)
}

@Composable
private fun CoverageDiagram(preview: FieldCoveragePreview, diagramHeight: Dp, tag: String = "coverage-diagram") {
    val outlineColor = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(diagramHeight).testTag(tag).semantics {
        contentDescription = "North-up field coverage diagram with ${preview.passes.size} separate working passes; green tool strips, pink unplanned area and amber reserved headland. Turns are not planned."
    }) {
        val minX = preview.outline.minOf { it.x }; val maxX = preview.outline.maxOf { it.x }
        val minY = preview.outline.minOf { it.y }; val maxY = preview.outline.maxOf { it.y }
        val scale = min((size.width - 24.dp.toPx()) / (maxX - minX), (size.height - 24.dp.toPx()) / (maxY - minY))
        val left = (size.width - (maxX - minX) * scale) / 2
        val top = (size.height - (maxY - minY) * scale) / 2
        fun point(p: CoveragePoint) = Offset((left + (p.x - minX) * scale).toFloat(), (top + (maxY - p.y) * scale).toFloat())
        val boundary = Path().apply {
            preview.outline.forEachIndexed { i, p -> val v = point(p); if (i == 0) moveTo(v.x, v.y) else lineTo(v.x, v.y) }
            close()
        }
        drawPath(boundary, Color(0xFFF0A6B1))
        clipPath(boundary) {
            // Butt caps model only the ideal rectangular tool strip, not a turning radius.
            preview.passes.forEach { pass ->
                drawLine(Color(0xFF76BE9C), point(pass.start), point(pass.end),
                    strokeWidth = (preview.toolWidthMeters * scale).toFloat(), cap = StrokeCap.Butt)
            }
            preview.passes.forEach { pass ->
                val a = point(pass.start); val b = point(pass.end)
                drawLine(Color(0xFF144A3A), a, b, strokeWidth = 1.dp.toPx())
                val length = (b - a).getDistance()
                if (length > 18.dp.toPx()) {
                    val direction = (b - a) / length
                    val center = (a + b) / 2f
                    val side = Offset(-direction.y, direction.x)
                    val tail = center - direction * 5.dp.toPx()
                    drawLine(Color(0xFF144A3A), tail + side * 3.dp.toPx(), center, strokeWidth = 1.5.dp.toPx())
                    drawLine(Color(0xFF144A3A), tail - side * 3.dp.toPx(), center, strokeWidth = 1.5.dp.toPx())
                }
            }
            if (preview.headlandMeters > 0) {
                // A round stroke clipped to the polygon depicts the distance-to-boundary band.
                drawPath(boundary, Color(0xFFF2C16D), style = Stroke(
                    width = (2 * preview.headlandMeters * scale).toFloat(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        drawPath(boundary, outlineColor, style = Stroke(2.dp.toPx()))
    }
}
