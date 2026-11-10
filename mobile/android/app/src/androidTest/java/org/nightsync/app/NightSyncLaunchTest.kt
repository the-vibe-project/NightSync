package org.nightsync.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NightSyncLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginScreenLaunchesAndShowsRequiredFields() {
        composeRule.onNodeWithText("NIGHTSYNC").assertIsDisplayed()
        composeRule.onNodeWithText("Welcome back").assertIsDisplayed()
        composeRule.onNodeWithText("Server address").assertIsDisplayed()
        composeRule.onNodeWithText("User ID").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
    }

    @Test
    fun invalidRemoteHttpAddressIsRejectedBeforeLogin() {
        composeRule.onNodeWithText("Server address").performTextInput("http://192.0.2.10:8787")
        composeRule.onNodeWithText("User ID").performTextInput("testuser")
        composeRule.onNodeWithText("Password").performTextInput("SomeStrongPassword123!")
        composeRule.onNodeWithText("Connect").performClick()
        composeRule.onNodeWithText("Use HTTPS except for local emulator testing.").assertIsDisplayed()
    }
}
