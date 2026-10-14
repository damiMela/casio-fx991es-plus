package com.damimela.fx991.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damimela.fx991.model.Key
import kotlin.math.abs
import kotlin.math.hypot

/** The complete keypad, arranged like the fx-991ES PLUS. */
@Composable
fun Keypad(
    shiftOn: Boolean,
    alphaOn: Boolean,
    haptics: Boolean,
    onKey: (Key) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val press: (Key) -> Unit = { k ->
        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onKey(k)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Rows 1-2: SHIFT ALPHA [pad] MODE ON / CALC ∫dx [pad] x⁻¹ logₐ
        Row(Modifier.weight(2.3f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CalcKey(KeyLayout.shiftKey, shiftOn, alphaOn, press, Modifier.weight(1f))
                CalcKey(KeyLayout.calcKey, shiftOn, alphaOn, press, Modifier.weight(1f))
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CalcKey(KeyLayout.alphaKey, shiftOn, alphaOn, press, Modifier.weight(1f))
                CalcKey(KeyLayout.integralKey, shiftOn, alphaOn, press, Modifier.weight(1f))
            }
            ReplayPad(press, Modifier.weight(2.1f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CalcKey(KeyLayout.modeKey, shiftOn, alphaOn, press, Modifier.weight(1f))
                CalcKey(KeyLayout.invKey, shiftOn, alphaOn, press, Modifier.weight(1f))
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CalcKey(KeyLayout.onKey, shiftOn, alphaOn, press, Modifier.weight(1f))
                CalcKey(KeyLayout.logaKey, shiftOn, alphaOn, press, Modifier.weight(1f))
            }
        }
        KeyLayout.functionRows.forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { CalcKey(it, shiftOn, alphaOn, press, Modifier.weight(1f)) }
            }
        }
        KeyLayout.numberRows.forEach { row ->
            Row(Modifier.weight(1.12f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { CalcKey(it, shiftOn, alphaOn, press, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun CalcKey(spec: KeySpec, shiftOn: Boolean, alphaOn: Boolean, onKey: (Key) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val legendSp = (minOf(w * 0.17f, h * 0.2f)).coerceIn(7f, 14f).sp
        val labelBase = minOf(w * 0.27f, h * 0.36f).coerceIn(11f, 28f)
        val labelSp = (if (spec.style == KeyStyle.SMALL || spec.style == KeyStyle.MODIFIER || spec.label.length > 3) labelBase * 0.62f else labelBase).sp
        val (bg, fg) = when (spec.style) {
            KeyStyle.NUM -> CasioColors.KeyLight to CasioColors.KeyTextDark
            KeyStyle.DELAC -> CasioColors.KeyBlue to CasioColors.KeyText
            KeyStyle.OP -> CasioColors.KeyDarker to CasioColors.KeyText
            KeyStyle.MODIFIER -> CasioColors.KeyDarker to CasioColors.KeyText
            else -> CasioColors.KeyDark to CasioColors.KeyText
        }
        val lit = (spec.key == Key.SHIFT && shiftOn) || (spec.key == Key.ALPHA && alphaOn)
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().weight(0.36f).padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    spec.shift, color = CasioColors.Shift, fontSize = legendSp, maxLines = 1, softWrap = false,
                    fontWeight = if (shiftOn && spec.shift.isNotEmpty()) FontWeight.ExtraBold else FontWeight.Medium
                )
                Text(
                    spec.alpha, color = CasioColors.Alpha, fontSize = legendSp, maxLines = 1, softWrap = false,
                    fontWeight = if (alphaOn && spec.alpha.isNotEmpty()) FontWeight.ExtraBold else FontWeight.Medium
                )
            }
            Box(
                Modifier.fillMaxWidth().weight(0.64f)
                    .clip(RoundedCornerShape(percent = 22))
                    .background(Brush.verticalGradient(listOf(bg.lighten(0.08f), bg)))
                    .border(
                        BorderStroke(if (lit) 2.dp else 1.dp, if (lit) (if (spec.key == Key.SHIFT) CasioColors.Shift else CasioColors.Alpha) else Color.Black.copy(alpha = 0.5f)),
                        RoundedCornerShape(percent = 22)
                    )
                    .clickable { onKey(spec.key) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    spec.label,
                    color = when {
                        spec.key == Key.SHIFT -> CasioColors.Shift
                        spec.key == Key.ALPHA -> CasioColors.Alpha
                        else -> fg
                    },
                    fontSize = labelSp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun Color.lighten(f: Float) = Color(
    red = red + (1f - red) * f, green = green + (1f - green) * f, blue = blue + (1f - blue) * f, alpha = alpha
)

/** Circular REPLAY pad: arrows around a centre OPTN key. */
@Composable
fun ReplayPad(onKey: (Key) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val d = minOf(maxWidth, maxHeight)
        Box(
            Modifier.size(d).clip(CircleShape)
                .background(Brush.radialGradient(listOf(CasioColors.Pad.lighten(0.12f), CasioColors.Pad)))
                .border(2.dp, Color.Black.copy(alpha = 0.6f), CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures { off ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = off.x - cx
                        val dy = off.y - cy
                        val r = hypot(dx, dy)
                        val key = when {
                            r < size.width * 0.2f -> Key.OPTN
                            abs(dx) > abs(dy) -> if (dx < 0) Key.LEFT else Key.RIGHT
                            else -> if (dy < 0) Key.UP else Key.DOWN
                        }
                        onKey(key)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val s = (d.value * 0.17f).coerceIn(10f, 26f).sp
            Text("▲", color = CasioColors.KeyText, fontSize = s, modifier = Modifier.align(Alignment.TopCenter).padding(top = d * 0.06f))
            Text("▼", color = CasioColors.KeyText, fontSize = s, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = d * 0.06f))
            Text("◀", color = CasioColors.KeyText, fontSize = s, modifier = Modifier.align(Alignment.CenterStart).padding(start = d * 0.08f))
            Text("▶", color = CasioColors.KeyText, fontSize = s, modifier = Modifier.align(Alignment.CenterEnd).padding(end = d * 0.08f))
            Box(
                Modifier.size(d * 0.36f).clip(CircleShape).background(CasioColors.KeyDarker)
                    .border(1.dp, Color.Black.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("OPTN", color = CasioColors.KeyText, fontSize = (d.value * 0.075f).coerceIn(7f, 12f).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
