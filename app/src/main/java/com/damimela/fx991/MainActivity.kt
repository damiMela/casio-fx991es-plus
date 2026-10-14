package com.damimela.fx991

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.damimela.fx991.ui.CalculatorApp

class MainActivity : ComponentActivity() {
    private val vm: CalcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by vm.state.collectAsState()
            var showHelp by remember { mutableStateOf(false) }
            LaunchedEffect(state.prefs.keepScreenOn) {
                if (state.prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            BackHandler(enabled = showHelp) { showHelp = false }
            CalculatorApp(
                state = state,
                showHelp = showHelp,
                onShowHelp = { showHelp = it },
                onKey = vm::press,
                onPrefs = vm::setPrefs,
                onHelpConsumed = vm::consumeHelp
            )
        }
    }
}
