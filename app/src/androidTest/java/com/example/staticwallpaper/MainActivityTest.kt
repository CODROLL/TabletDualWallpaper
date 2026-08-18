package com.example.staticwallpaper

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()

    @Test fun homeShowsDesktopAndLockCardsAndApplyTargets(){
        rule.onNodeWithText("桌面壁纸").assertIsDisplayed()
        rule.onNodeWithText("锁屏壁纸").assertIsDisplayed()
        rule.onNodeWithText("应用壁纸").performClick()
        rule.onNodeWithText("应用桌面").assertIsDisplayed()
        rule.onNodeWithText("应用锁屏").assertIsDisplayed()
    }
}
