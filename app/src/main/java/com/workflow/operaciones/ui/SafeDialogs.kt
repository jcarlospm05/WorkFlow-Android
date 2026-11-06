package com.workflow.operaciones.ui

import android.view.WindowManager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat

/** A dialog has its own Android window: activity insets do not protect its buttons. */
@Composable private fun DialogWindowInsets() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.let {
            WindowCompat.setDecorFitsSystemWindows(it, false)
            // Compose 1.7 can measure WRAP_CONTENT dialogs using the physical display height
            // while Android 15 clips that window to a smaller visible frame.
            val matchParent = WindowManager.LayoutParams.MATCH_PARENT
            if (it.attributes.width != matchParent || it.attributes.height != matchParent) {
                it.setLayout(matchParent, matchParent)
                ViewCompat.requestApplyInsets(it.decorView)
            }
        }
    }
}

@Composable internal fun SafeFullscreenDialog(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogWindowInsets()
        Surface(Modifier.fillMaxSize(), color = Brand.Background) {
            Column(Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("safe-dialog"), content = content)
        }
    }
}

/** Bounded text area scrolls independently; the action row always remains in safe bounds. */
@Composable internal fun SafeAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    scrollText: Boolean = true
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogWindowInsets()
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)).padding(20.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().testTag("safe-alert"), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProvideTextStyle(MaterialTheme.typography.titleLarge, content = title)
                    Box(Modifier.weight(1f, fill = false).then(if (scrollText) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
                        ProvideTextStyle(MaterialTheme.typography.bodyLarge, content = text)
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("dialog-actions"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (dismissButton != null) Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { dismissButton() }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { confirmButton() }
                    }
                }
            }
        }
    }
}
