package com.damimela.fx991.ui

import com.damimela.fx991.model.Key

enum class KeyStyle { FUNC, NUM, OP, DELAC, MODIFIER, SMALL }

/** One physical key: primary label, yellow SHIFT legend and red ALPHA legend (as printed on the fx-991ES PLUS). */
data class KeySpec(
    val key: Key,
    val label: String,
    val shift: String = "",
    val alpha: String = "",
    val style: KeyStyle = KeyStyle.FUNC
)

object KeyLayout {
    val shiftKey = KeySpec(Key.SHIFT, "SHIFT", style = KeyStyle.MODIFIER)
    val alphaKey = KeySpec(Key.ALPHA, "ALPHA", style = KeyStyle.MODIFIER)
    val modeKey = KeySpec(Key.MODE, "MODE", shift = "SETUP", style = KeyStyle.SMALL)
    val onKey = KeySpec(Key.ON, "ON", style = KeyStyle.SMALL)
    val calcKey = KeySpec(Key.CALC, "CALC", shift = "SOLVE", alpha = "=", style = KeyStyle.SMALL)
    val integralKey = KeySpec(Key.INTEGRAL, "∫dx", shift = "d/dx", style = KeyStyle.SMALL)
    val invKey = KeySpec(Key.INV, "x⁻¹", shift = "x!")
    val logaKey = KeySpec(Key.LOGA, "logₐ▫", shift = "Σ")

    /** Rows of six function keys below the pad. */
    val functionRows: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec(Key.FRAC, "▫/▫", shift = "a b/c"),
            KeySpec(Key.SQRT, "√▫", shift = "∛▫"),
            KeySpec(Key.SQUARE, "x²", shift = "x³"),
            KeySpec(Key.POW, "^", shift = "ˣ√▫"),
            KeySpec(Key.LOG, "log", shift = "10ˣ"),
            KeySpec(Key.LN, "ln", shift = "eˣ")
        ),
        listOf(
            KeySpec(Key.NEG, "(−)", shift = "∠", alpha = "A"),
            KeySpec(Key.DMS, "°′″", shift = "▶DMS", alpha = "B"),
            KeySpec(Key.HYP, "hyp", alpha = "C"),
            KeySpec(Key.SIN, "sin", shift = "sin⁻¹", alpha = "D"),
            KeySpec(Key.COS, "cos", shift = "cos⁻¹", alpha = "E"),
            KeySpec(Key.TAN, "tan", shift = "tan⁻¹", alpha = "F")
        ),
        listOf(
            KeySpec(Key.RCL, "RCL", shift = "STO"),
            KeySpec(Key.ENG, "ENG", shift = "←"),
            KeySpec(Key.LPAR, "(", shift = "Abs", alpha = "Y"),
            KeySpec(Key.RPAR, ")", shift = ",", alpha = "X"),
            KeySpec(Key.SD, "S⇔D", shift = "a b/c⇔d/c"),
            KeySpec(Key.MPLUS, "M+", shift = "M−", alpha = "M")
        )
    )

    /** Number block: five keys per row. */
    val numberRows: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec(Key.D7, "7", shift = "CONST", style = KeyStyle.NUM),
            KeySpec(Key.D8, "8", shift = "CONV", style = KeyStyle.NUM),
            KeySpec(Key.D9, "9", shift = "CLR", style = KeyStyle.NUM),
            KeySpec(Key.DEL, "DEL", shift = "INS", style = KeyStyle.DELAC),
            KeySpec(Key.AC, "AC", style = KeyStyle.DELAC)
        ),
        listOf(
            KeySpec(Key.D4, "4", shift = "MATRIX", style = KeyStyle.NUM),
            KeySpec(Key.D5, "5", shift = "VECTOR", style = KeyStyle.NUM),
            KeySpec(Key.D6, "6", shift = "HELP", style = KeyStyle.NUM),
            KeySpec(Key.MUL, "×", shift = "nPr", style = KeyStyle.OP),
            KeySpec(Key.DIV, "÷", shift = "nCr", style = KeyStyle.OP)
        ),
        listOf(
            KeySpec(Key.D1, "1", shift = "STAT", style = KeyStyle.NUM),
            KeySpec(Key.D2, "2", shift = "CMPLX", style = KeyStyle.NUM),
            KeySpec(Key.D3, "3", shift = "BASE", style = KeyStyle.NUM),
            KeySpec(Key.ADD, "+", shift = "Pol", style = KeyStyle.OP),
            KeySpec(Key.SUB, "−", shift = "Rec", style = KeyStyle.OP)
        ),
        listOf(
            KeySpec(Key.D0, "0", shift = "Ran#", style = KeyStyle.NUM),
            KeySpec(Key.DOT, ".", shift = "RanInt", style = KeyStyle.NUM),
            KeySpec(Key.EXP10, "×10ˣ", shift = "π", alpha = "e", style = KeyStyle.NUM),
            KeySpec(Key.ANS, "Ans", shift = "DRG▶", alpha = "PreAns", style = KeyStyle.NUM),
            KeySpec(Key.EQ, "=", shift = "%", style = KeyStyle.OP)
        )
    )

    /** Every key, for the help screen. */
    val all: List<KeySpec>
        get() = listOf(shiftKey, alphaKey, modeKey, onKey, calcKey, integralKey, invKey, logaKey) +
            functionRows.flatten() + numberRows.flatten()
}
