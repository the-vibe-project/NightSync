package org.nightsync.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
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
        composeRule.onNodeWithText("Server address").performTextClearance()
        composeRule.onNodeWithText("Server address").performTextInput("http://192.0.2.10:8787")
        composeRule.onNodeWithText("User ID").performTextInput("testuser")
        composeRule.onNodeWithText("Password").performTextInput("SomeStrongPassword123!")
        composeRule.onNodeWithText("Connect").performClick()
        composeRule.onNodeWithText("Use HTTPS except for local emulator testing.").assertIsDisplayed()
    }

    @Test
    fun memberCanSignInBrowseFilesAndCreateFolder() {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.start()
        try {
            server.enqueue(
                okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"token":"test-token","username":"alice","role":"member","must_change_password":false}""")
            )
            server.enqueue(
                okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"items":[{"name":"Documents","path":"Documents","is_dir":true,"size_bytes":0,"modified":"2026-11-10T10:00:00Z"},{"name":"hello.txt","path":"hello.txt","is_dir":false,"size_bytes":12,"modified":"2026-11-10T10:00:00Z"}],"count":2,"path":"","storage_scope":"private-user"}""")
            )

            composeRule.onNodeWithText("Server address").performTextClearance()
            composeRule.onNodeWithText("Server address").performTextInput(server.url("/").toString().trimEnd('/'))
            composeRule.onNodeWithText("User ID").performTextInput("alice")
            composeRule.onNodeWithText("Password").performTextInput("StrongTestPassword123!")
            composeRule.onNodeWithText("Connect").performClick()

            // The server response is delivered on Dispatchers.IO and updates Compose state
            // asynchronously. Use the Compose test rule's synchronized node wait instead
            // of querying the semantics tree from the waitUntil predicate.
            composeRule.waitUntilAtLeastOneExists(hasText("hello.txt"), 15_000)
            composeRule.onNodeWithText("Documents").assertIsDisplayed()
            composeRule.onNodeWithText("hello.txt").assertIsDisplayed()

            server.enqueue(
                okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(201)
                    .setBody("""{"created":true,"path":"Trips"}""")
            )
            server.enqueue(
                okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"items":[{"name":"Documents","path":"Documents","is_dir":true,"size_bytes":0,"modified":"2026-11-10T10:00:00Z"},{"name":"Trips","path":"Trips","is_dir":true,"size_bytes":0,"modified":"2026-11-10T10:00:00Z"},{"name":"hello.txt","path":"hello.txt","is_dir":false,"size_bytes":12,"modified":"2026-11-10T10:00:00Z"}],"count":3,"path":"","storage_scope":"private-user"}""")
            )

            composeRule.onNodeWithText("New folder name").performTextInput("Trips")
            composeRule.onNodeWithText("Create").performClick()
            composeRule.waitUntilAtLeastOneExists(hasText("Trips"), 15_000)
            composeRule.onNodeWithText("Trips").assertIsDisplayed()

            val loginRequest = server.takeRequest()
            val listRequest = server.takeRequest()
            val createRequest = server.takeRequest()
            val refreshedListRequest = server.takeRequest()

            assertEquals("/api/v1/login", loginRequest.path)
            assertTrue(listRequest.path?.startsWith("/api/v1/files") == true)
            assertEquals("POST", createRequest.method)
            assertEquals("/api/v1/folders", createRequest.path)
            assertEquals("Bearer test-token", createRequest.getHeader("Authorization"))
            assertTrue(refreshedListRequest.path?.startsWith("/api/v1/files") == true)
        } finally {
            server.shutdown()
        }
    }
}
