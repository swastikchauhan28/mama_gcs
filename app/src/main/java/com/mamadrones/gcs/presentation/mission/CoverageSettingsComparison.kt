@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mamadrones.gcs.presentation.mission

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mamadrones.gcs.domain.model.CoverageCalculation
import com.mamadrones.gcs.domain.model.DraftWaypoint
import com.mamadrones.gcs.domain.model.FieldCoveragePlanner
import kotlinx.coroutines.*
import java.util.Locale

private const val INPUT_LIMIT = 16

/** Volatile pair of independent planner results; never a route or a vehicle command. */
internal data class CoverageSettingsPair(val first: CoverageCalculation, val second: CoverageCalculation)

@Stable
internal class CoverageSettingsComparisonState(
    private val scope: CoroutineScope,
    private val outline: List<DraftWaypoint>,
    initialWidth: String,
    initialOverlap: String,
    private val headland: String,
    private val eastWest: Boolean,
    private val calculator: suspend (List<DraftWaypoint>, Double, Double, Boolean, Double) -> CoverageCalculation =
        { vertices, width, overlap, direction, margin ->
            withContext(Dispatchers.Default) { FieldCoveragePlanner.calculate(vertices, width, overlap, direction, margin) }
        },
) {
    private val baseWidth = initialWidth
    private val baseOverlap = initialOverlap
    var widthA by mutableStateOf(initialWidth); private set
    var overlapA by mutableStateOf(initialOverlap); private set
    var widthB by mutableStateOf(initialWidth); private set
    var overlapB by mutableStateOf(initialOverlap); private set
    var result by mutableStateOf<CoverageSettingsPair?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    private var job: Job? = null

    private fun settingsError(width: String, overlap: String): String? =
        if (width.length > INPUT_LIMIT || overlap.length > INPUT_LIMIT || headland.length > INPUT_LIMIT) "Enter at most 16 characters per value."
        else FieldCoveragePlanner.settingsError(width.trim().toDoubleOrNull(), overlap.trim().toDoubleOrNull(), headland.trim().toDoubleOrNull())
    val errorA: String? get() = settingsError(widthA, overlapA)
    val errorB: String? get() = settingsError(widthB, overlapB)
    fun updateWidthA(value: String) { invalidate(); widthA = value.take(INPUT_LIMIT + 1) }
    fun updateOverlapA(value: String) { invalidate(); overlapA = value.take(INPUT_LIMIT + 1) }
    fun updateWidthB(value: String) { invalidate(); widthB = value.take(INPUT_LIMIT + 1) }
    fun updateOverlapB(value: String) { invalidate(); overlapB = value.take(INPUT_LIMIT + 1) }
    fun invalidate() { job?.cancel(); job = null; result = null; error = null; busy = false }

    fun applyOption(first: Boolean, coverage: FieldCoverageState): Boolean {
        val pair = result ?: return false
        if (busy || (if (first) pair.first else pair.second) !is CoverageCalculation.Ready) return false
        val newWidth = if (first) widthA else widthB
        val newOverlap = if (first) overlapA else overlapB
        if (!coverage.applyComparedSettings(outline, baseWidth, baseOverlap, headland, eastWest, newWidth, newOverlap)) {
            error = "The field or settings changed. Compare again before using an option."
            return false
        }
        invalidate()
        return true
    }

    fun compare() {
        if (busy || errorA != null || errorB != null) return
        val aWidth = widthA.trim().toDouble(); val aOverlap = overlapA.trim().toDouble()
        val bWidth = widthB.trim().toDouble(); val bOverlap = overlapB.trim().toDouble()
        val margin = headland.trim().toDouble()
        result = null; error = null; busy = true
        job = scope.launch {
            try {
                val first = calculator(outline, aWidth, aOverlap, eastWest, margin)
                ensureActive()
                val second = calculator(outline, bWidth, bOverlap, eastWest, margin)
                ensureActive()
                result = CoverageSettingsPair(first, second)
                busy = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                error = "Comparison could not be calculated. Check the inputs and try again."
                busy = false
            }
        }
    }
}

@Composable
internal fun CoverageSettingsComparisonPanel(outline: List<DraftWaypoint>, coverage: FieldCoverageState) {
    val scope = rememberCoroutineScope()
    val state = remember(outline, coverage.available, coverage.width, coverage.overlap, coverage.headland, coverage.eastWest) {
        CoverageSettingsComparisonState(scope, outline.toList(), coverage.width, coverage.overlap, coverage.headland, coverage.eastWest)
    }
    DisposableEffect(state) { onDispose { state.invalidate() } }
    var showSettings by remember(state) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("coverage-options"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showSettings = true }, enabled = coverage.available && !state.busy && !coverage.busy,
            modifier = Modifier.testTag("coverage-options-open")) { Text("Compare tool settings") }
        if (state.busy) { Text("Calculating settings comparison…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.result?.let { pair ->
            Text("Tool settings comparison · preview only", style = MaterialTheme.typography.titleMedium)
            Text("Same field, ${if (coverage.eastWest) "east / west" else "north / south"} direction and ${coverage.headland} m headland. Working length excludes turns. Ranges describe the model, not actual soil or positioning accuracy. Overlapping ranges cannot show which option has fewer gaps. Narrow gaps may be missed. No option is selected automatically.")
            CoverageOptionCard("A", pair.first, coverage.available && !coverage.busy) { state.applyOption(true, coverage) }
            CoverageOptionCard("B", pair.second, coverage.available && !coverage.busy) { state.applyOption(false, coverage) }
        }
    }
    if (showSettings) AlertDialog(
        onDismissRequest = { showSettings = false },
        title = { Text("Compare tool settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Both options use the current field, direction and headland. These values do not change the route or coverage settings.")
                Text("Option A", style = MaterialTheme.typography.titleSmall)
                CoverageOptionInput("Tool width A (m)", state.widthA, state::updateWidthA, "coverage-options-width-a")
                CoverageOptionInput("Overlap A (%)", state.overlapA, state::updateOverlapA, "coverage-options-overlap-a")
                state.errorA?.let { Text("A: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("coverage-options-error-a")) }
                Text("Option B", style = MaterialTheme.typography.titleSmall)
                CoverageOptionInput("Tool width B (m)", state.widthB, state::updateWidthB, "coverage-options-width-b")
                CoverageOptionInput("Overlap B (%)", state.overlapB, state::updateOverlapB, "coverage-options-overlap-b")
                state.errorB?.let { Text("B: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("coverage-options-error-b")) }
            }
        },
        confirmButton = { TextButton(onClick = { state.compare(); showSettings = false },
            enabled = state.errorA == null && state.errorB == null && !state.busy,
            modifier = Modifier.testTag("coverage-options-compare")) { Text("Compare") } },
        dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Close") } },
    )
}

@Composable
private fun CoverageOptionInput(label: String, value: String, onValueChange: (String) -> Unit, tag: String) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(value, onValueChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }), modifier = Modifier.fillMaxWidth().testTag(tag))
}

@Composable
private fun CoverageOptionCard(label: String, calculation: CoverageCalculation, enabled: Boolean, onUse: () -> Unit) {
    Surface(Modifier.fillMaxWidth().testTag("coverage-option-${label.lowercase(Locale.ROOT)}"), tonalElevation = 2.dp, shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Option $label", style = MaterialTheme.typography.titleSmall)
            when (calculation) {
                is CoverageCalculation.Invalid -> Text("Unavailable: ${calculation.reason}", color = MaterialTheme.colorScheme.error)
                is CoverageCalculation.Ready -> {
                    val preview = calculation.preview
                    // Keep enough precision to identify the inputs actually used by the planner.
                    Text("Tool ${preview.toolWidthMeters} m · Overlap ${preview.overlapPercent}%")
                    Text("${preview.passes.size} working passes")
                    Text(String.format(Locale.US, "Working length: %.1f m", preview.workingLengthMeters))
                    val area = preview.areaEstimate
                    Text(if (area == null) "Modeled unplanned range: unavailable" else
                        "Modeled unplanned range: ${coverageRange(area.mainGapMinimum, area.mainGapMaximum)} m²")
                    OutlinedButton(onClick = onUse, enabled = enabled,
                        modifier = Modifier.testTag("coverage-option-use-${label.lowercase(Locale.ROOT)}")) { Text("Use option $label") }
                }
            }
        }
    }
}
