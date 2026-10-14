package com.damimela.fx991.ui

import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import com.damimela.fx991.model.Key
import androidx.compose.ui.input.key.Key as HwKey
import androidx.compose.ui.input.key.KeyEvent as HwKeyEvent

/** Hardware keyboard shortcuts: (shortcut, description) pairs for the help screen. */
val keyboardShortcuts = listOf(
    "0-9  .  +  -  *  /  ^  (  )" to "Digits, operators and brackets",
    "Enter  or  =" to "Equals (=)",
    "Backspace / Delete" to "DEL",
    "Esc" to "AC",
    "Arrow keys" to "REPLAY pad (◀ ▶ ▲ ▼)",
    "Tab  or  [" to "SHIFT",
    "]" to "ALPHA",
    "m" to "MODE",
    "s  c  t" to "sin  cos  tan",
    "l  n" to "log  ln",
    "r  q" to "√  x²",
    "a" to "Ans"
)

/** Maps a hardware key press to a calculator key, or null when the event is not handled. */
fun mapKeyEvent(e: HwKeyEvent): Key? {
    if (e.type != KeyEventType.KeyDown) return null
    when (e.key) {
        HwKey.Enter, HwKey.NumPadEnter -> return Key.EQ
        HwKey.Backspace, HwKey.Delete -> return Key.DEL
        HwKey.Escape -> return Key.AC
        HwKey.DirectionLeft -> return Key.LEFT
        HwKey.DirectionRight -> return Key.RIGHT
        HwKey.DirectionUp -> return Key.UP
        HwKey.DirectionDown -> return Key.DOWN
        HwKey.Tab -> return Key.SHIFT
        else -> {}
    }
    return when (e.utf16CodePoint.toChar()) {
        '0' -> Key.D0; '1' -> Key.D1; '2' -> Key.D2; '3' -> Key.D3; '4' -> Key.D4
        '5' -> Key.D5; '6' -> Key.D6; '7' -> Key.D7; '8' -> Key.D8; '9' -> Key.D9
        '.', ',' -> Key.DOT
        '+' -> Key.ADD
        '-' -> Key.SUB
        '*' -> Key.MUL
        '/' -> Key.DIV
        '^' -> Key.POW
        '(' -> Key.LPAR
        ')' -> Key.RPAR
        '=' -> Key.EQ
        's' -> Key.SIN
        'c' -> Key.COS
        't' -> Key.TAN
        'l' -> Key.LOG
        'n' -> Key.LN
        'r' -> Key.SQRT
        'q' -> Key.SQUARE
        'a' -> Key.ANS
        'm' -> Key.MODE
        '[' -> Key.SHIFT
        ']' -> Key.ALPHA
        else -> null
    }
}
