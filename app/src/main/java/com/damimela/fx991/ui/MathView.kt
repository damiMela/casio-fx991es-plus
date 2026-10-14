package com.damimela.fx991.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.damimela.fx991.engine.MCursor
import com.damimela.fx991.engine.MFrac
import com.damimela.fx991.engine.MMat
import com.damimela.fx991.engine.MMixed
import com.damimela.fx991.engine.MNode
import com.damimela.fx991.engine.MPlace
import com.damimela.fx991.engine.MRow
import com.damimela.fx991.engine.MSqrt
import com.damimela.fx991.engine.MSup
import com.damimela.fx991.engine.MText

/** Renders a natural-display layout tree: stacked fractions, radicals, superscripts, matrices and the input cursor. */
@Composable
fun MathView(node: MNode, fontSize: TextUnit, color: Color, blink: Boolean = true, modifier: Modifier = Modifier) {
    Box(modifier) { MathNodeView(node, fontSize, color, blink) }
}

@Composable
private fun MathNodeView(n: MNode, fs: TextUnit, color: Color, blink: Boolean) {
    val density = LocalDensity.current
    fun TextUnit.toDpValue() = with(density) { toDp() }
    when (n) {
        is MText -> Text(n.s, fontSize = fs, color = color, maxLines = 1, softWrap = false)
        is MRow -> Row(verticalAlignment = Alignment.CenterVertically) {
            n.items.forEach { MathNodeView(it, fs, color, blink) }
        }
        is MFrac -> Column(
            Modifier.padding(horizontal = 2.dp).width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally)) { MathNodeView(n.num, fs * 0.85f, color, blink) }
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(color))
            Box(Modifier.align(Alignment.CenterHorizontally)) { MathNodeView(n.den, fs * 0.85f, color, blink) }
        }
        is MMixed -> Row(verticalAlignment = Alignment.CenterVertically) {
            MathNodeView(n.whole, fs, color, blink)
            MathNodeView(MFrac(n.num, n.den), fs, color, blink)
        }
        is MSqrt -> Row(Modifier.height(IntrinsicSize.Min)) {
            if (n.index != null) {
                Box(Modifier.align(Alignment.Top)) { MathNodeView(n.index, fs * 0.6f, color, blink) }
            }
            Canvas(Modifier.width((fs.value * 0.6f).dp).fillMaxHeight()) {
                val w = size.width
                val h = size.height
                val p = Path().apply {
                    moveTo(0f, h * 0.58f)
                    lineTo(w * 0.28f, h * 0.5f)
                    lineTo(w * 0.62f, h)
                    lineTo(w, 1f)
                }
                drawPath(p, color, style = Stroke(width = 1.6f * density.density))
            }
            Box(
                Modifier.drawBehind {
                    drawLine(color, Offset(0f, 1f), Offset(size.width, 1f), strokeWidth = 1.6f * density.density)
                }.padding(top = 3.dp, start = 1.dp, end = 2.dp)
            ) { MathNodeView(n.inner, fs, color, blink) }
        }
        is MSup -> Row(verticalAlignment = Alignment.CenterVertically) {
            MathNodeView(n.base, fs, color, blink)
            val shift = fs.toDpValue() * (if (n.down) 0.3f else -0.45f)
            Box(Modifier.offset(y = shift)) { MathNodeView(n.exp, fs * 0.65f, color, blink) }
        }
        is MMat -> Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Bracket(left = true, color = color, fs = fs)
            val rows = n.cells.size
            val cols = n.cells.firstOrNull()?.size ?: 0
            MatrixGrid(rows, cols) { n.cells.forEach { r -> r.forEach { MathNodeView(it, fs * 0.9f, color, blink) } } }
            Bracket(left = false, color = color, fs = fs)
        }
        is MCursor -> if (blink) {
            if (n.insert) Box(Modifier.width(2.dp).height(fs.toDpValue() * 1.15f).background(color))
            else Box(Modifier.width(fs.toDpValue() * 0.6f).height(fs.toDpValue() * 1.15f).background(color.copy(alpha = 0.45f)))
        } else Box(Modifier.width(2.dp).height(fs.toDpValue() * 1.15f))
        is MPlace -> Box(
            Modifier.padding(horizontal = 1.dp).width(fs.toDpValue() * 0.55f).height(fs.toDpValue() * 0.8f)
                .border(1.dp, color.copy(alpha = 0.6f))
        )
    }
}

@Composable
private fun Bracket(left: Boolean, color: Color, fs: TextUnit) {
    val density = LocalDensity.current
    Canvas(Modifier.width((fs.value * 0.35f).dp).fillMaxHeight().padding(vertical = 2.dp)) {
        val w = size.width
        val h = size.height
        val sw = 1.6f * density.density
        val x = if (left) w * 0.8f else w * 0.2f
        val tip = if (left) 0f else w
        drawLine(color, Offset(x, 0f), Offset(x, h), strokeWidth = sw)
        drawLine(color, Offset(x, 0f), Offset(tip, 0f), strokeWidth = sw)
        drawLine(color, Offset(x, h), Offset(tip, h), strokeWidth = sw)
    }
}

/** Lays children (row-major) out in aligned columns and rows. */
@Composable
private fun MatrixGrid(rows: Int, cols: Int, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.padding(horizontal = 2.dp)) { measurables, _ ->
        val loose = Constraints()
        val ps: List<Placeable> = measurables.map { it.measure(loose) }
        val gapX = 14.dp.roundToPx()
        val gapY = 4.dp.roundToPx()
        val colW = IntArray(cols) { c -> (0 until rows).maxOfOrNull { r -> ps.getOrNull(r * cols + c)?.width ?: 0 } ?: 0 }
        val rowH = IntArray(rows) { r -> (0 until cols).maxOfOrNull { c -> ps.getOrNull(r * cols + c)?.height ?: 0 } ?: 0 }
        val w = colW.sum() + gapX * maxOf(0, cols - 1)
        val h = rowH.sum() + gapY * maxOf(0, rows - 1)
        layout(w, h) {
            var y = 0
            for (r in 0 until rows) {
                var x = 0
                for (c in 0 until cols) {
                    val p = ps.getOrNull(r * cols + c)
                    p?.placeRelative(x + (colW[c] - p.width) / 2, y + (rowH[r] - p.height) / 2)
                    x += colW[c] + gapX
                }
                y += rowH[r] + gapY
            }
        }
    }
}
