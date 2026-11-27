package com.mamadrones.gcs

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** A local, disconnected dialog check: no caster or vehicle socket is opened. */
class NtripPromptUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun remoteHostPromptRequiresExplicitHttpOptInAndCanBeCancelled() {
        compose.onNodeWithTag("station-menu").performClick()
        compose.onNodeWithTag("menu-general").performClick()
        compose.onNodeWithTag("category-tcp").performScrollTo().performClick()
        compose.onNodeWithTag("ntrip-start").performScrollTo().performClick()

        compose.onNodeWithText("NTRIP remote host").assertIsDisplayed()
        compose.onNodeWithTag("ntrip-url").performTextInput("http://demo:sample@127.0.0.1:2105/TEST")
        compose.onNodeWithText("HTTP is unencrypted.", substring = true).assertExists()
        compose.onNodeWithTag("ntrip-http-opt-in").assertIsOff()
        compose.onNodeWithTag("ntrip-gga").assertIsOff()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithText("NTRIP remote host").assertDoesNotExist()
        compose.onNodeWithTag("ntrip-status").performScrollTo().assertTextEquals("NTRIP: Stopped")
    }
}
