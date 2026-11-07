package com.workflow.operaciones.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workflow.operaciones.MainActivity
import com.workflow.operaciones.PhoneSession
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class PhoneSessionTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun shell(command:String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { fd -> FileInputStream(fd.fileDescriptor).use{it.readBytes()} }
    }
    @After fun restore() {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.activityRule.scenario.onActivity { PhoneSession.unlocked=false }
    }
    @Test fun switching_away_and_recreating_the_activity_keep_an_authenticated_session() {
        compose.activityRule.scenario.onActivity { PhoneSession.unlocked=true }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue(PhoneSession.unlocked)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertTrue(PhoneSession.unlocked)
        compose.onNodeWithTag("nav-0").assertExists()
    }
    @Test fun physical_screen_off_revokes_the_session_even_while_backgrounded() {
        compose.activityRule.scenario.onActivity { PhoneSession.unlocked=true }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        try {
            shell("input keyevent KEYCODE_SLEEP")
            val end=android.os.SystemClock.uptimeMillis()+10_000
            while(PhoneSession.unlocked && android.os.SystemClock.uptimeMillis()<end)android.os.SystemClock.sleep(50)
            assertFalse("Apagar la pantalla debe revocar la sesión",PhoneSession.unlocked)
        } finally { shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard") }
    }
}
