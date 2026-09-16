package com.xrdoge.xrpl.androidsa

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun serverBrowserFlow_canAddAndSelectCustomProfile() {
        composeRule.onNodeWithText("Server browser").assertExists()

        composeRule.onNodeWithText("Profile label").performTextReplacement("Ops Node")
        composeRule.onNodeWithText("Host/IP").performTextReplacement("prod.example.org")
        composeRule.onNodeWithText("Port").performTextReplacement("7778")
        composeRule.onNodeWithText("Add server profile").performClick()

        composeRule.onNodeWithText("Ops Node (prod.example.org:7778)").assertExists()
        composeRule.onAllNodesWithText("Select").onFirst().performClick()
    }

    @Test
    fun runtimeStats_showTxRxRatioAndLatestCommand() {
        composeRule.onNodeWithText("Runtime stats").assertExists()
        composeRule.onNodeWithText("TX/RX ratio").assertExists()
        composeRule.onNodeWithText("Packets sent").assertExists()
        composeRule.onNodeWithText("Packets received").assertExists()
        composeRule.onNodeWithText("Last command").assertExists()
        composeRule.onNodeWithText("0.00").assertExists()
    }

    @Test
    fun manualCommand_busyState_showsInFlightFeedback() {
        composeRule.onNodeWithText("Native command").performTextReplacement("connect:127.0.0.1:7777")
        composeRule.onNodeWithText("Dispatch manual command").performClick()
        composeRule.onNodeWithText("Dispatching command").assertExists()
    }
}
