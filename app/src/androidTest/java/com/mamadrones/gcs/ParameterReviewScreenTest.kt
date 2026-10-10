package com.mamadrones.gcs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mamadrones.gcs.data.parameters.ParameterFileReader
import com.mamadrones.gcs.presentation.screens.ParameterReviewScreen
import com.mamadrones.gcs.presentation.screens.ParameterReviewState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** File fixtures only; no vehicle connection or file-picker automation. */
class ParameterReviewScreenTest {
    @get:Rule val compose = createComposeRule()
    private val snapshot = ParameterFileReader.read("SERVO1_FUNCTION,74\nRC1_TRIM,1500".byteInputStream())

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
