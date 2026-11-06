package com.workflow.operaciones.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.view.View
import android.view.WindowManager
import android.view.inspector.WindowInspector
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.workflow.core.Module
import com.workflow.core.Operation
import com.workflow.operaciones.MainActivity
import com.workflow.operaciones.WorkflowApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.time.LocalDate

/** Actual taps and physical window bounds in both Android navigation modes, with real IME. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class DialogInsetsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val modes = listOf("gestural", "threebutton")
    private var activityDecor: View? = null
    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }
    private fun navigation(mode: String) {
        shell("cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.$mode")
        shell("settings put secure show_ime_with_hard_keyboard 1")
        Thread.sleep(1200)
    }
    private fun show(content: @Composable () -> Unit) {
        compose.activityRule.scenario.onActivity { activity ->
            // Test harness only. The distributed app retains its screen protection and lock.
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            activityDecor = activity.window.decorView
            activity.setContent { WorkflowTheme(content) }
        }
        compose.waitForIdle()
    }
    private fun focusedDialogDecor() = WindowInspector.getGlobalWindowViews().lastOrNull {
        it !== activityDecor && it.isShown && it.hasWindowFocus()
    }
    private fun awaitDialogFocus() {
        // Compose can become idle before Android 15 delivers window focus to a new dialog.
        compose.waitUntil(15_000) { compose.runOnIdle { focusedDialogDecor() != null } }
    }
    private fun dialogDecor() = requireNotNull(focusedDialogDecor()) { "Dialog has not received window focus" }
    private fun imeBottom(): Int = compose.runOnIdle {
        ViewCompat.getRootWindowInsets(dialogDecor())?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
    }
    private fun openKeyboard(tag: String) {
        awaitDialogFocus()
        compose.onNodeWithTag(tag).performScrollTo().performClick().performTextInput("Prueba de teclado")
        compose.runOnIdle {
            val focused = requireNotNull(dialogDecor().findFocus())
            context.getSystemService(InputMethodManager::class.java).showSoftInput(focused, InputMethodManager.SHOW_IMPLICIT)
        }
        compose.waitUntil(10_000) { imeBottom() > 0 }
    }
    private fun assertSafeActions(vararg tags: String) {
        awaitDialogFocus()
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val screenHeight = screenshot.height
        screenshot.recycle()
        var diagnostic = "No frame sampled"
        try {
            // IME insets and the dialog layout are delivered on separate Android frames.
            compose.waitUntil(15_000) {
                runCatching {
                    val window = compose.runOnIdle {
                        val decor = dialogDecor()
                        val insets = requireNotNull(ViewCompat.getRootWindowInsets(decor))
                        val offset = IntArray(2); decor.getLocationOnScreen(offset)
                        Triple(insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom,
                            insets.getInsets(WindowInsetsCompat.Type.ime()).bottom, offset[1])
                    }
                    val bottom = screenHeight - maxOf(window.first, window.second)
                    val bounds = tags.associateWith { tag ->
                        compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInWindow
                    }
                    diagnostic = "window=$window safeBottom=$bottom bounds=$bounds"
                    bounds.values.all { it.height > 0 && it.bottom + window.third <= bottom - 8 && it.top + window.third >= 0 }
                }.onFailure { diagnostic = it.toString() }.getOrDefault(false)
            }
            println("DIALOG_BOUNDS $diagnostic")
        } catch (failure: Throwable) {
            capture("failure-actions-${android.os.Build.VERSION.SDK_INT}")
            val tree = runCatching { compose.onNode(isDialog()).printToString() }.getOrDefault("No dialog semantics")
            val decorInfo = compose.runOnIdle {
                val decor = dialogDecor()
                val visible = android.graphics.Rect()
                val shown = decor.getGlobalVisibleRect(visible)
                "decor=${decor.width}x${decor.height} shown=$shown rect=$visible"
            }
            println("DIALOG_DIAGNOSTIC $diagnostic $decorInfo")
            println(tree)
            throw AssertionError("DIALOG_DIAGNOSTIC $diagnostic $decorInfo ${tree.lineSequence().joinToString("|")}", failure)
        }
    }
    private fun capture(name: String) {
        Thread.sleep(500)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WorkFlowDialogProof")
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        requireNotNull(context.contentResolver.openOutputStream(uri)).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    @Test fun operationCancelAndSaveAreTappableAboveNavigationAndKeyboard() {
        modes.forEach { mode ->
            navigation(mode)
            listOf(false, true).forEach { keyboard ->
                val repo = (context.applicationContext as WorkflowApp).repository
                lateinit var model: WorkflowViewModel
                val open = mutableStateOf(true)
                val original = Operation(day = LocalDate.now().toString(), module = Module.TASK, title = "Prueba de controles", status = "Pendiente")
                compose.activityRule.scenario.onActivity { model = WorkflowViewModel(repo); model.shareAfterSave = false }
                compose.waitUntil(30_000) { !model.busy && model.document != null }
                show { if (open.value) OperationEditor(original, model) { open.value = false } else Text("Formulario cerrado") }
                if (keyboard) openKeyboard("operation-notes")
                assertSafeActions("dialog-cancel", "dialog-confirm", "dialog-save-another")
                capture("editor-$mode-${if (keyboard) "keyboard" else "closed"}")
                compose.onNodeWithTag("dialog-cancel").performClick()
                compose.waitUntil(10_000) { !open.value }
                assertFalse(model.document!!.operations.any { it.id == original.id })
                compose.runOnIdle { open.value = true }
                if (keyboard) openKeyboard("operation-notes")
                assertSafeActions("dialog-cancel", "dialog-confirm", "dialog-save-another")
                compose.onNodeWithTag("dialog-confirm").performClick()
                compose.waitUntil(20_000) { !open.value && !model.busy }
                assertTrue(model.document!!.operations.any { it.id == original.id })
            }
        }
    }
    @Test fun compactCancelAndAcceptRemainTappableWithKeyboard() {
        modes.forEach { mode ->
            navigation(mode)
            listOf(false, true).forEach { keyboard ->
                val open = mutableStateOf(true); val accepted = mutableStateOf(false)
                show {
                    var text by remember { mutableStateOf("") }
                    if (open.value) SafeAlertDialog(onDismissRequest = { open.value = false }, title = { Text("Fecha de trabajo") },
                        text = { OutlinedTextField(text, { text = it }, label = { Text("Fecha") }, modifier = Modifier.testTag("alert-input")) },
                        confirmButton = { TextButton(onClick = { accepted.value = true; open.value = false }, modifier = Modifier.testTag("alert-confirm")) { Text("Aceptar") } },
                        dismissButton = { TextButton(onClick = { open.value = false }, modifier = Modifier.testTag("alert-cancel")) { Text("Cancelar") } })
                }
                if (keyboard) openKeyboard("alert-input")
                assertSafeActions("alert-cancel", "alert-confirm")
                capture("alert-$mode-${if (keyboard) "keyboard" else "closed"}")
                compose.onNodeWithTag("alert-cancel").performClick()
                compose.waitUntil(10_000) { !open.value }
                assertFalse(accepted.value)
                compose.runOnIdle { open.value = true }
                if (keyboard) openKeyboard("alert-input")
                assertSafeActions("alert-cancel", "alert-confirm")
                compose.onNodeWithTag("alert-confirm").performClick()
                compose.waitUntil(10_000) { !open.value }
                assertTrue(accepted.value)
            }
        }
    }
}
