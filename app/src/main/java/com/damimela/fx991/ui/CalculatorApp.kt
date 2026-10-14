package com.damimela.fx991.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import com.damimela.fx991.model.AppPrefs
import com.damimela.fx991.model.CalcState
import com.damimela.fx991.model.Key
import com.damimela.fx991.model.ThemeChoice

/** Whole app UI: top bar, calculator body (display + keypad), settings dialog and help screen. */
@Composable
fun CalculatorApp(
    state: CalcState,
    showHelp: Boolean,
    onShowHelp: (Boolean) -> Unit,
    onKey: (Key) -> Unit,
    onPrefs: (AppPrefs) -> Unit,
    onHelpConsumed: () -> Unit = {}
) {
    AppTheme(state.prefs.theme) {
        var showSettings by remember { mutableStateOf(false) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(state.helpRequested) { if (state.helpRequested) { onShowHelp(true); onHelpConsumed() } }
        LaunchedEffect(showHelp, showSettings) { if (!showHelp && !showSettings) runCatching { focus.requestFocus() } }

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (showHelp) {
                HelpScreen(onClose = { onShowHelp(false) })
            } else {
                Column(
                    Modifier.fillMaxSize().focusRequester(focus).focusable()
                        .onKeyEvent { e -> mapKeyEvent(e)?.let { onKey(it); true } ?: false }
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                        Text(
                            "FX-991 Calc", color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f).padding(8.dp)
                        )
                        TextButton(onClick = { showSettings = true }) { Text("⚙ Settings") }
                        TextButton(onClick = { onShowHelp(true) }) { Text("? Help") }
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        // The calculator body keeps a real-device aspect ratio and is centred on wide screens.
                        val bodyW = minOf(maxWidth, maxHeight * 0.66f)
                        Column(
                            Modifier.width(bodyW).height(minOf(maxHeight, bodyW / 0.62f))
                                .clip(RoundedCornerShape(18.dp)).background(CasioColors.Body).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Lcd(state, Modifier.fillMaxWidth().weight(0.26f))
                            Keypad(state.shift, state.alphaActive, state.prefs.haptics, onKey, Modifier.fillMaxWidth().weight(0.74f))
                        }
                    }
                }
            }
        }
        if (showSettings) SettingsDialog(state.prefs, onPrefs) { showSettings = false }
    }
}

@Composable
private fun SettingsDialog(prefs: AppPrefs, onPrefs: (AppPrefs) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SwitchRow("Haptic feedback", prefs.haptics) { onPrefs(prefs.copy(haptics = it)) }
                SwitchRow("Keep screen on", prefs.keepScreenOn) { onPrefs(prefs.copy(keepScreenOn = it)) }
                Text("App theme")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice.values().forEach { t ->
                        FilterChip(
                            selected = prefs.theme == t, onClick = { onPrefs(prefs.copy(theme = t)) },
                            label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Text("Calculator options (angle unit, number format, ...) are in SHIFT + MODE (SETUP).", style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
