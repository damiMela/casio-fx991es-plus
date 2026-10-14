package com.damimela.fx991.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val keyCombos = listOf(
    "SHIFT" to "Next key uses its yellow legend. Press SHIFT again to cancel.",
    "ALPHA" to "Next key uses its red legend (A-F, X, Y, M, =). SHIFT then ALPHA locks ALPHA.",
    "SHIFT + MODE" to "SETUP: Math/Line I/O, angle unit, number format, fraction style, statistics frequency, decimal mark, ENG symbols, table g(x).",
    "MODE" to "COMP, CMPLX, STAT, BASE-N, EQN, MATRIX, TABLE, VECTOR.",
    "SHIFT + RCL" to "STO: then press A-F, X, Y or M (with ALPHA) to store the value.",
    "RCL" to "Recall a variable; also M+ / SHIFT M+ (M−) add to / subtract from M.",
    "SHIFT + 9" to "CLR: Setup, Memory or All.",
    "SHIFT + 7 / 8" to "CONST (40 constants) / CONV (40 unit conversions).",
    "SHIFT + 1" to "STAT menu (in STAT mode).  SHIFT + 3: BASE menu (BASE-N mode).",
    "SHIFT + 2" to "CMPLX menu: Abs, Arg, Conjg, ReP, ImP, ▶r∠θ, ▶a+bi.",
    "SHIFT + 4 / 5" to "MATRIX / VECTOR menus (dimensions, data, operations).",
    "SHIFT + 6" to "This help screen.",
    "SHIFT + CALC" to "SOLVE: Newton/secant solver for an equation containing X.",
    "CALC" to "Evaluate the expression for entered variable values.",
    "SHIFT + ∫dx" to "d/dx (numerical derivative).  SHIFT + logₐ▫ : Σ (summation).",
    "SHIFT + × / ÷" to "nPr / nCr.",
    "SHIFT + + / −" to "Pol( / Rec( conversions.",
    "SHIFT + Ans" to "DRG▶ angle conversion menu.  ALPHA + Ans: PreAns.",
    "SHIFT + DEL" to "Toggle insert / overwrite.",
    "S⇔D" to "Toggle exact (fraction, √, π) and decimal result.  SHIFT + S⇔D: improper ⇔ mixed fraction (polar ⇔ rectangular for complex).",
    "°′″" to "Enter degrees, minutes, seconds; SHIFT + °′″ converts the result to DMS.",
    "ENG / SHIFT + ENG" to "Shift the result's engineering exponent up / down (in CMPLX mode ENG types i).",
    "OPTN (pad centre)" to "Function menu with hyperbolic functions, Int, Intg, Rnd, GCD, LCM and more.",
    "◀ ▶ after an error" to "Moves the cursor to the position of the error.",
    "▲ ▼" to "Browse the calculation history (REPLAY); ◀ ▶ then edits the entry."
)

@Composable
fun HelpScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Help / About", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Section("Key combinations")
            keyCombos.forEach { (k, d) -> Entry(k, d) }
            Section("Key legends (primary · SHIFT · ALPHA)")
            KeyLayout.all.filter { it.shift.isNotEmpty() || it.alpha.isNotEmpty() }.forEach { k ->
                Entry(k.label, listOf(k.shift.takeIf { it.isNotEmpty() }?.let { "SHIFT: $it" }, k.alpha.takeIf { it.isNotEmpty() }?.let { "ALPHA: $it" }).filterNotNull().joinToString("    "))
            }
            Section("Hardware keyboard")
            keyboardShortcuts.forEach { (k, d) -> Entry(k, d) }
            Section("About")
            Text(
                "FX-991 Calc — an open-source scientific calculator inspired by the Casio fx-991ES PLUS. " +
                    "Not affiliated with or endorsed by Casio. Released under the MIT license.",
                color = MaterialTheme.colorScheme.onBackground
            )
            Box(Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Entry(key: String, desc: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(key, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.width(150.dp))
        Text(desc, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
    }
}
