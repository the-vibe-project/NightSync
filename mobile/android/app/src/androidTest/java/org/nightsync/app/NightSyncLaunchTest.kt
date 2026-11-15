package org.nightsync.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
class NightSyncLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginScreenLaunchesAndShowsRequiredFields() {
        composeRule.onNodeWithText("NIGHTSYNC").assertIsDisplayed()
        composeRule.onNodeWithText("Connect to your server").assertIsDisplayed()
        composeRule.onNodeWithText("Explore demo workspace").assertIsDisplayed()
        composeRule.onNodeWithText("Server address").assertIsDisplayed()
        composeRule.onNodeWithText("User ID").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
    }

    @Test
    fun demoWorkspaceOpensWithoutServerAndShowsSampleContent() {
        composeRule.onNodeWithText("Explore demo workspace").performClick()
        composeRule.onNodeWithText("Your server at a glance").assertIsDisplayed()
        composeRule.onNodeWithText("ONLINE · DEMO").assertIsDisplayed()
        composeRule.onNodeWithText("SIMULATED").assertIsDisplayed()
        composeRule.onNodeWithText("1.2 TB").assertIsDisplayed()
        composeRule.onNodeWithText("Files").performClick()
        composeRule.onNodeWithText("Explore your cloud").assertIsDisplayed()
        composeRule.onNodeWithText("SAMPLE DATA").assertIsDisplayed()
        composeRule.onNodeWithText("Welcome.pdf").assertIsDisplayed()
        composeRule.onNodeWithText("Documents").performClick()
        composeRule.onNodeWithText("Meeting-notes.docx").assertIsDisplayed()
        composeRule.onNodeWithText("Sample content only. Connect a server to manage your own files.").assertIsDisplayed()
    }

    @Test
    fun demoBackupSettingsAreInteractiveAndSimulationIsSafe() {
        composeRule.onNodeWithText("Explore demo workspace").performClick()
        composeRule.onNodeWithText("Backup").performClick()
        composeRule.onNodeWithText("WHAT TO BACK UP").assertIsDisplayed()
        composeRule.onNodeWithText("Photos").assertIsDisplayed()
        composeRule.onNodeWithText("Videos").assertIsDisplayed()
        composeRule.onNodeWithText("Wi-Fi + mobile data").performClick()
        composeRule.onNodeWithText("Simulate backup").performClick()
        composeRule.onNodeWithText("Simulation complete · 3 categories · Wi-Fi + mobile data · Daily. No files were read or transferred.").assertIsDisplayed()
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

            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithText("hello.txt").fetchSemanticsNodes().isNotEmpty()
            }
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
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithText("Trips").fetchSemanticsNodes().isNotEmpty()
            }
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
