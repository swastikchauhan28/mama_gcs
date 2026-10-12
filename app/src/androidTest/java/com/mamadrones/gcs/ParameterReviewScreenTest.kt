package com.mamadrones.gcs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mamadrones.gcs.data.parameters.ParameterFileReader
import com.mamadrones.gcs.data.parameters.ParameterFileComparison
import com.mamadrones.gcs.presentation.screens.ParameterReviewScreen
import com.mamadrones.gcs.presentation.screens.ParameterReviewState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** File fixtures only; no vehicle connection or file-picker automation. */
class ParameterReviewScreenTest {
    @get:Rule val compose = createComposeRule()
    private val snapshot = ParameterFileReader.read("SERVO1_FUNCTION,74\nRC1_TRIM,1500".byteInputStream())

    @Test fun comparisonShowsReferenceAndCurrentAndSupportsSearch() {
        val reference = ParameterFileReader.read("SERVO1_FUNCTION,73\nRC1_TRIM,1500".byteInputStream())
        val comparison = ParameterFileComparison.compare(reference, snapshot)
        compose.setContent { MaterialTheme {
            ParameterReviewScreen(ParameterReviewState(snapshot, reference = reference, comparison = comparison), {}, {}, { null })
        } }
        compose.onNodeWithTag("parameter-review-list").performScrollToNode(hasTestTag("parameter-difference-SERVO1_FUNCTION"))
        compose.onNodeWithText("Reference: 73").assertIsDisplayed()
        compose.onNodeWithText("Current: 74").assertIsDisplayed()
        compose.onNodeWithTag("parameter-review-list").performScrollToNode(hasTestTag("parameter-show-unchanged"))
        compose.onNodeWithTag("parameter-show-unchanged").performClick()
        compose.onNodeWithTag("parameter-review-list").performScrollToNode(hasTestTag("parameter-search"))
        compose.onNodeWithTag("parameter-search").performTextInput("RC1")
        compose.onNodeWithTag("parameter-review-list").performScrollToNode(hasTestTag("parameter-difference-RC1_TRIM"))
        compose.onNodeWithText("SAME VALUE TEXT").assertIsDisplayed()
        compose.onNodeWithText("Current: 74").assertDoesNotExist()
    }

    @Test fun referenceRemovalIsExplicit() {
        var removed = false
        val reference = snapshot
        compose.setContent { MaterialTheme {
            ParameterReviewScreen(ParameterReviewState(snapshot, reference = reference,
                comparison = ParameterFileComparison.compare(reference, snapshot)), {}, {}, { null },
                onClearReference = { removed = true })
        } }
        compose.onNodeWithTag("parameter-review-list").performScrollToNode(hasTestTag("parameter-reference-clear"))
        compose.onNodeWithTag("parameter-reference-clear").performClick()
        compose.runOnIdle { assertTrue(removed) }
    }

    @Test fun referenceActionsAreDisabledDuringImport() {
        compose.setContent { MaterialTheme {
            ParameterReviewScreen(ParameterReviewState(snapshot, loading = true, reference = snapshot,
                comparison = ParameterFileComparison.compare(snapshot, snapshot)), {}, {}, { null })
        } }
        compose.onNodeWithTag("parameter-review-list").performScrollToNode(hasTestTag("parameter-reference-clear"))
        compose.onNodeWithTag("parameter-reference-clear").assertIsNotEnabled()
        compose.onNodeWithTag("parameter-reference-import").assertIsNotEnabled()
    }

    @Test fun searchShowsRawFileValue() {
        compose.setContent { MaterialTheme {
            ParameterReviewScreen(ParameterReviewState(snapshot), {}, {}, { null })
        } }
        compose.onNodeWithTag("parameter-review-list").performScrollToNode(hasTestTag("parameter-search"))
        compose.onNodeWithTag("parameter-search").performTextInput("SERVO1")
        compose.onNodeWithTag("parameter-review-list").performScrollToNode(hasText("File value: 74"))
        compose.onNodeWithText("File value: 74").assertIsDisplayed()
        compose.onNodeWithText("File value: 1500").assertDoesNotExist()
    }

    @Test fun importErrorRetainsPreviousFileAndClearIsAvailable() {
        var cleared = false
        compose.setContent { MaterialTheme {
            ParameterReviewScreen(ParameterReviewState(snapshot, error = "Malformed file."), {}, { cleared = true }, { null })
        } }
        compose.onNodeWithTag("parameter-review-list").performScrollToNode(hasText("Malformed file. Previous file remains displayed below."))
        compose.onNodeWithText("Malformed file. Previous file remains displayed below.").assertIsDisplayed()
        compose.onNodeWithTag("parameter-review-list").performScrollToNode(hasTestTag("parameter-clear"))
        compose.onNodeWithTag("parameter-clear").performClick()
        compose.runOnIdle { assertTrue(cleared) }
    }
}
