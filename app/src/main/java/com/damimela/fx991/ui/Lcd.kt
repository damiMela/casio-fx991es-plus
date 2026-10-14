package com.damimela.fx991.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damimela.fx991.engine.AngleUnit
import com.damimela.fx991.engine.Formatter
import com.damimela.fx991.engine.MText
import com.damimela.fx991.engine.NumFormat
import com.damimela.fx991.engine.Real
import com.damimela.fx991.model.CalcMode
import com.damimela.fx991.model.CalcState
import com.damimela.fx991.model.GridKind
import com.damimela.fx991.model.GridState
import com.damimela.fx991.model.PendingMem
import com.damimela.fx991.model.Render
import com.damimela.fx991.model.Screen
import com.damimela.fx991.model.Toks

private val Ink = CasioColors.LcdInk
private val Mono = FontFamily.Monospace

/** The liquid-crystal display: status bar plus the current screen (expression/result, menu, grid, ...). */
@Composable
fun Lcd(state: CalcState, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(8.dp)).background(CasioColors.Lcd)
            .border(3.dp, CasioColors.BodyEdge, RoundedCornerShape(8.dp))
    ) {
        val fs = (maxHeight.value * 0.17f).coerceIn(14f, 40f).sp
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp)) {
            StatusBar(state, (fs.value * 0.42f).coerceAtLeast(8f).sp)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val sc = state.screen) {
                    is Screen.Main -> MainScreen(state, fs)
                    is Screen.Menu -> MenuScreen(sc, fs)
                    is Screen.Grid -> GridScreen(sc.g, state.notice, fs)
                    is Screen.Results -> ResultsScreen(sc, state, fs)
                    is Screen.Table -> TableScreen(sc, state, fs)
                    is Screen.Confirm -> ConfirmScreen(sc, fs)
                }
            }
        }
    }
}

private fun statusItems(s: CalcState): List<Pair<String, Boolean>> {
    val sci = s.display.format
    return listOf(
        "S" to s.shift,
        "A" to s.alphaActive,
        "M" to s.memoryUsed,
        "STO" to (s.pending == PendingMem.STO),
        "RCL" to (s.pending == PendingMem.RCL),
        s.mode.label to (s.mode != CalcMode.COMP),
        "Math" to s.display.mathIO,
        when (s.angle) { AngleUnit.DEG -> "D"; AngleUnit.RAD -> "R"; AngleUnit.GRA -> "G" } to true,
        "FIX" to (sci == NumFormat.FIX),
        "SCI" to (sci == NumFormat.SCI),
        (if (s.insertMode) "" else "OVR") to !s.insertMode,
        "▲" to (s.history.isNotEmpty() && s.historyPos < s.history.lastIndex),
        "▼" to (s.historyPos >= 0)
    )
}

@Composable
private fun StatusBar(s: CalcState, size: TextUnit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        statusItems(s).filter { it.second && it.first.isNotEmpty() }.forEach { (t, _) ->
            Text(t, color = Ink, fontSize = size, fontWeight = FontWeight.Bold, fontFamily = Mono, maxLines = 1)
        }
    }
    Spacer(Modifier.height(1.dp))
}

@Composable
private fun MainScreen(s: CalcState, fs: TextUnit) {
    val err = s.error
    val node = Toks.toNode(s.tokens, s.cursor, s.insertMode)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.fillMaxWidth().weight(1f).horizontalScroll(rememberScrollState())) {
            MathView(node, fs, Ink, blink = true)
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 2.dp), contentAlignment = Alignment.BottomEnd) {
            when {
                err != null -> Column(horizontalAlignment = Alignment.End) {
                    Text(err.kind.title, color = Ink, fontSize = fs, fontWeight = FontWeight.Bold)
                    Text("[AC] Cancel   [◀][▶] Goto", color = Ink, fontSize = (fs.value * 0.42f).sp, fontFamily = Mono)
                }
                s.replayText != null -> Text(s.replayText, color = Ink, fontSize = fs, maxLines = 1)
                s.result != null -> ResultBlock(s, fs)
            }
        }
    }
}

@Composable
private fun ResultBlock(s: CalcState, fs: TextUnit) {
    val rv = s.result ?: return
    if (s.mode == CalcMode.BASE && rv.value is Real && rv.value.isInteger) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Render.baseLines(s).forEach { (name, text) ->
                Row {
                    Text("$name ", color = CasioColors.LcdDim, fontSize = (fs.value * 0.5f).sp, fontFamily = Mono)
                    Text(text, color = Ink, fontSize = (fs.value * 0.62f).sp, fontFamily = Mono, maxLines = 1)
                }
            }
        }
        return
    }
    val lines = Render.resultLines(s)
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.horizontalScroll(rememberScrollState())) {
        lines.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                line.label?.let { Text("$it ", color = Ink, fontSize = (fs.value * 0.7f).sp, fontFamily = Mono) }
                MathView(line.node, if (lines.size > 1) (fs.value * 0.8f).sp else fs, Ink, blink = false)
            }
        }
    }
}

@Composable
private fun MenuScreen(sc: Screen.Menu, fs: TextUnit) {
    val def = sc.def
    val small = (fs.value * 0.62f).sp
    val perPage = if (def.columns > 1) 4 * def.columns else 4
    val page = sc.sel / perPage.coerceAtLeast(1)
    val from = page * perPage
    val shown = def.items.withIndex().toList().drop(from).take(perPage)
    Column(Modifier.fillMaxSize()) {
        Text(def.title, color = Ink, fontSize = (small.value * 0.8f).sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
        if (def.columns > 1) {
            shown.chunked(def.columns).forEach { rowItems ->
                Row(Modifier.fillMaxWidth()) {
                    rowItems.forEach { (i, it) -> MenuCell(i, it.label, i == sc.sel, small, Modifier.weight(1f)) }
                }
            }
        } else {
            shown.forEach { (i, it) -> MenuCell(i, it.label, i == sc.sel, small, Modifier.fillMaxWidth()) }
        }
        if (def.digitEntry && sc.digits.isNotEmpty()) Text("> ${sc.digits}", color = Ink, fontSize = small, fontFamily = Mono)
    }
}

@Composable
private fun MenuCell(i: Int, label: String, selected: Boolean, size: TextUnit, modifier: Modifier) {
    val bg = if (selected) Ink else CasioColors.Lcd
    val fg = if (selected) CasioColors.Lcd else Ink
    Text(
        "${i + 1}:$label", color = fg, fontSize = size, fontFamily = Mono, maxLines = 1,
        modifier = modifier.background(bg).padding(horizontal = 3.dp)
    )
}

@Composable
private fun GridScreen(g: GridState, notice: String?, fs: TextUnit) {
    val small = (fs.value * 0.62f).sp
    val cellW = if (g.kind == GridKind.STAT || g.kind == GridKind.MAT || g.kind == GridKind.VEC) 78.dp else 110.dp
    val maxRows = 4
    val first = (g.r - (maxRows - 1)).coerceAtLeast(0)
    Column(Modifier.fillMaxSize()) {
        Text(
            notice ?: g.title, color = Ink, fontSize = (small.value * 0.8f).sp, fontWeight = FontWeight.Bold,
            fontFamily = Mono, maxLines = 1
        )
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            if (g.colLabels.isNotEmpty()) Row {
                if (g.rowLabels.isNotEmpty() && g.kind != GridKind.EQN_POLY) Spacer(Modifier.width(24.dp))
                g.colLabels.forEach { Text(it, color = CasioColors.LcdDim, fontSize = small, fontFamily = Mono, modifier = Modifier.width(cellW)) }
            }
            for (r in first until minOf(g.rows, first + maxRows)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    g.rowLabels.getOrNull(r)?.let { Text(it, color = Ink, fontSize = small, fontFamily = Mono, maxLines = 1) }
                    for (c in 0 until g.cols) {
                        val sel = r == g.r && c == g.c
                        val text = g.cell(r, c) + if (sel) "▏" else ""
                        Text(
                            text, fontSize = small, fontFamily = Mono, maxLines = 1,
                            color = if (sel) CasioColors.Lcd else Ink,
                            modifier = Modifier.width(if (g.cols == 1) 240.dp else cellW)
                                .background(if (sel) Ink else CasioColors.Lcd).padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsScreen(sc: Screen.Results, s: CalcState, fs: TextUnit) {
    val page = sc.pages[sc.idx]
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Text(
            if (sc.pages.size > 1) "${sc.title}  ${sc.idx + 1}/${sc.pages.size}" else sc.title,
            color = Ink, fontSize = (fs.value * 0.5f).sp, fontFamily = Mono, fontWeight = FontWeight.Bold
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Text(page.label + " = ", color = Ink, fontSize = (fs.value * 0.9f).sp, fontFamily = Mono)
            page.value?.let { MathView(Render.valueNode(it, s, null, sc.decimal), fs, Ink, blink = false) }
        }
        page.note?.let { Text(it, color = Ink, fontSize = (fs.value * 0.5f).sp, fontFamily = Mono) }
    }
}

@Composable
private fun TableScreen(sc: Screen.Table, s: CalcState, fs: TextUnit) {
    val small = (fs.value * 0.6f).sp
    val first = (sc.sel - 3).coerceAtLeast(0)
    fun fmt(d: Double?): String = if (d == null) "ERROR" else Formatter.text(Real.of(d), s.display, false)
    Column(Modifier.fillMaxSize()) {
        Row {
            listOf("x", "f(x)").plus(if (sc.hasG) listOf("g(x)") else emptyList()).forEach {
                Text(it, color = CasioColors.LcdDim, fontSize = small, fontFamily = Mono, modifier = Modifier.weight(1f))
            }
        }
        sc.rows.withIndex().drop(first).take(4).forEach { (i, r) ->
            Row(Modifier.fillMaxWidth().background(if (i == sc.sel) Ink else CasioColors.Lcd)) {
                val fg = if (i == sc.sel) CasioColors.Lcd else Ink
                Text(fmt(r.x), color = fg, fontSize = small, fontFamily = Mono, maxLines = 1, modifier = Modifier.weight(1f))
                Text(fmt(r.f), color = fg, fontSize = small, fontFamily = Mono, maxLines = 1, modifier = Modifier.weight(1f))
                if (sc.hasG) Text(fmt(r.g), color = fg, fontSize = small, fontFamily = Mono, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ConfirmScreen(sc: Screen.Confirm, fs: TextUnit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(sc.message, color = Ink, fontSize = (fs.value * 0.7f).sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
        Text("[=]:Yes  [AC]:Cancel", color = Ink, fontSize = (fs.value * 0.6f).sp, fontFamily = Mono)
    }
}
