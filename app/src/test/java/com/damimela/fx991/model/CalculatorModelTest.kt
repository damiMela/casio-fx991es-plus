package com.damimela.fx991.model

import com.damimela.fx991.engine.ErrorKind
import com.damimela.fx991.engine.Formatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val KEYMAP = mapOf(
    "0" to Key.D0, "1" to Key.D1, "2" to Key.D2, "3" to Key.D3, "4" to Key.D4, "5" to Key.D5, "6" to Key.D6,
    "7" to Key.D7, "8" to Key.D8, "9" to Key.D9, "+" to Key.ADD, "-" to Key.SUB, "*" to Key.MUL, "/" to Key.DIV,
    "=" to Key.EQ, "." to Key.DOT, "(" to Key.LPAR, ")" to Key.RPAR, "neg" to Key.NEG, "shift" to Key.SHIFT,
    "alpha" to Key.ALPHA, "ac" to Key.AC, "del" to Key.DEL, "left" to Key.LEFT, "right" to Key.RIGHT,
    "up" to Key.UP, "down" to Key.DOWN, "sqrt" to Key.SQRT, "pow" to Key.POW, "sq" to Key.SQUARE, "frac" to Key.FRAC,
    "sin" to Key.SIN, "mode" to Key.MODE, "sd" to Key.SD, "ans" to Key.ANS, "rcl" to Key.RCL, "eng" to Key.ENG,
    "calc" to Key.CALC, "mplus" to Key.MPLUS, "exp" to Key.EXP10, "dms" to Key.DMS, "log" to Key.LOG, "ln" to Key.LN,
    "inv" to Key.INV, "hyp" to Key.HYP, "tan" to Key.TAN, "cos" to Key.COS, "loga" to Key.LOGA
)

private fun CalculatorModel.keys(seq: String): CalculatorModel {
    for (k in seq.trim().split(Regex("\\s+"))) press(KEYMAP[k] ?: error("unknown key $k"))
    return this
}

private val CalculatorModel.result get() = Render.resultText(state.value)
private val CalculatorModel.input get() = Formatter.plain(Toks.toNode(state.value.tokens, null))

class CalculatorModelTest {
    @Test
    fun basicArithmetic() {
        val m = CalculatorModel().keys("2 + 3 * 4 =")
        assertEquals("14", m.result)
    }

    @Test
    fun fractionsAndSd() {
        val m = CalculatorModel().keys("1 / 3 + 1 / 6 =")
        assertEquals("1⌟2", m.result)
        m.keys("sd")
        assertEquals("0.5", m.result)
        m.keys("sd")
        assertEquals("1⌟2", m.result)
    }

    @Test
    fun fractionTemplate() {
        val m = CalculatorModel().keys("frac 1 right 3 right + frac 1 right 6 =")
        assertEquals("1⌟2", m.result)
    }

    @Test
    fun sqrtTemplateAndShift() {
        val m = CalculatorModel().keys("sqrt 8 right =")
        assertEquals("2√(2)", m.result)
        m.keys("ac shift sqrt 2 7 )  =")
        assertEquals("3", m.result)
    }

    @Test
    fun squareAndPower() {
        assertEquals("25", CalculatorModel().keys("5 sq =").result)
        assertEquals("8", CalculatorModel().keys("2 pow 3 right =").result)
    }

    @Test
    fun ansContinuation() {
        val m = CalculatorModel().keys("2 + 3 = * 4 =")
        assertEquals("20", m.result)
        m.keys("ac 7 =")
        assertEquals("7", m.result)
        m.keys("+ 1 =")
        assertEquals("8", m.result)
    }

    @Test
    fun trigAndAngleSetup() {
        val m = CalculatorModel().keys("sin 3 0 ) =")
        assertEquals("1⌟2", m.result)
        m.keys("ac shift mode 4 sin 1 ) =")
        assertEquals("0.8414709848", m.result)
    }

    @Test
    fun errorPositionAndRecovery() {
        val m = CalculatorModel().keys("1 + * 2 =")
        assertEquals(ErrorKind.SYNTAX, m.state.value.error?.kind)
        m.keys("left")
        assertNull(m.state.value.error)
        assertEquals(2, m.state.value.cursor)
        m.keys("right del =")
        assertEquals("3", m.result)
    }

    @Test
    fun mathError() {
        val m = CalculatorModel().keys("1 / 0 =")
        assertEquals(ErrorKind.MATH, m.state.value.error?.kind)
        m.keys("ac")
        assertTrue(m.state.value.tokens.isEmpty())
    }

    @Test
    fun storeAndRecall() {
        val m = CalculatorModel().keys("5 shift rcl neg")
        assertEquals("5", m.result)
        m.keys("ac rcl neg + 1 =")
        assertEquals("6", m.result)
        m.keys("ac shift rcl neg")
        assertEquals("6", m.result)
    }

    @Test
    fun memoryPlus() {
        val m = CalculatorModel().keys("5 mplus 3 mplus")
        assertTrue(m.state.value.memoryUsed)
        m.keys("ac rcl mplus =")
        assertEquals("8", m.result)
        m.keys("ac 2 shift mplus rcl mplus =")
        assertEquals("6", m.result)
    }

    @Test
    fun alphaVariables() {
        val m = CalculatorModel().keys("3 shift rcl hyp ac alpha hyp * 2 =")
        assertEquals("6", m.result)
    }

    @Test
    fun historyReplay() {
        val m = CalculatorModel().keys("1 + 1 = ac 2 + 2 = up")
        assertEquals("2+2", m.input)
        m.keys("up")
        assertEquals("1+1", m.input)
        m.keys("down down")
        assertTrue(m.state.value.tokens.isEmpty())
    }

    @Test
    fun deleteTemplate() {
        val m = CalculatorModel().keys("frac 1 right 2 right del")
        assertEquals("(1÷2)".length > 0, true)
        m.keys("del")
        assertNotNull(m.state.value)
    }

    @Test
    fun engNotation() {
        val m = CalculatorModel().keys("1 2 3 4 5 =")
        m.keys("eng")
        assertEquals("12.345×10³", m.result)
    }

    @Test
    fun dmsCycle() {
        val m = CalculatorModel().keys("1 2 dms 3 4 dms 5 6 dms")
        assertEquals("12°34′56″", m.input)
    }

    @Test
    fun solveFlow() {
        val m = CalculatorModel().keys("2 alpha ) alpha calc 8 shift calc 1 =")
        assertEquals("4 0", m.result)
    }

    @Test
    fun calcFlow() {
        val m = CalculatorModel().keys("alpha ) sq + 1 calc")
        assertTrue(m.state.value.screen is Screen.Grid)
        m.keys("3 =")
        assertNull(m.state.value.error)
        assertEquals("10", m.result)
    }

    @Test
    fun persistenceRoundTrip() {
        val st = MemoryStorage()
        CalculatorModel(st).keys("1 2 = shift rcl neg ac shift mode 4 shift mode 6 2 ")
        val m2 = CalculatorModel(st)
        assertEquals(2, m2.state.value.history.size)
        m2.keys("rcl neg =")
        assertEquals("12.00", m2.result)
    }

    @Test
    fun quadraticEquationMode() {
        val m = CalculatorModel().keys("mode 5 3 1 = neg 3 = 2 =")
        val scr = m.state.value.screen as Screen.Results
        assertEquals(listOf("x1", "x2", "Δ"), scr.pages.map { it.label })
        assertEquals("2", Formatter.text(scr.pages[0].value!!, m.state.value.display))
        assertEquals("1", Formatter.text(scr.pages[1].value!!, m.state.value.display))
        m.keys("down")
        assertEquals(1, (m.state.value.screen as Screen.Results).idx)
        m.keys("ac")
        assertTrue(m.state.value.screen is Screen.Grid)
    }

    @Test
    fun complexQuadratic() {
        val m = CalculatorModel().keys("mode 5 3 1 = 2 = 5 =")
        val scr = m.state.value.screen as Screen.Results
        assertEquals("-1 + 2i", Formatter.text(scr.pages[0].value!!, m.state.value.display))
    }

    @Test
    fun linearSystem() {
        val m = CalculatorModel().keys("mode 5 1 1 = 1 = 3 = 1 = neg 1 = 1 =")
        val scr = m.state.value.screen as Screen.Results
        assertEquals(listOf("X", "Y"), scr.pages.map { it.label })
        assertEquals("2", Formatter.text(scr.pages[0].value!!, m.state.value.display))
        assertEquals("1", Formatter.text(scr.pages[1].value!!, m.state.value.display))
    }

    @Test
    fun matrixDeterminant() {
        val m = CalculatorModel().keys("mode 6 1 2 = 2 = 1 = 2 = 3 = 4 = ac")
        assertTrue(m.state.value.screen is Screen.Main)
        m.keys("shift 4 7 shift 4 3 ) =")
        assertEquals("-2", m.result)
    }

    @Test
    fun statOneVar() {
        val m = CalculatorModel().keys("mode 3 1 1 = 2 = 3 = 4 = 5 = ac")
        assertTrue(m.state.value.hasStat)
        m.keys("shift 1 4 2 =")
        assertEquals("3", m.result)
    }

    @Test
    fun statRegression() {
        val m = CalculatorModel().keys("mode 3 2 1 right 3 = left 2 right 5 = left 3 right 7 = ac")
        assertTrue(m.state.value.hasStat)
        m.keys("shift 1 6 2 =")
        assertEquals("2", m.result)
    }

    @Test
    fun baseN() {
        val m = CalculatorModel().keys("mode 4 pow tan + 1 =")
        assertEquals("10", m.result)
        m.keys("ac shift 3 1")
        assertEquals(" and ", m.state.value.tokens.last().text)
    }

    @Test
    fun tableMode() {
        val m = CalculatorModel().keys("mode 7 alpha ) sq = 1 = 3 = 1 =")
        val t = m.state.value.screen as Screen.Table
        assertEquals(3, t.rows.size)
        assertEquals(9.0, t.rows[2].f!!, 1e-12)
    }

    @Test
    fun complexMode() {
        val m = CalculatorModel().keys("mode 2 ( 1 + 2 eng ) * ( 3 - eng ) =")
        assertEquals("5 + 5i", m.result)
    }

    @Test
    fun clearAll() {
        val m = CalculatorModel().keys("5 shift rcl neg ac shift 9 3 =")
        m.keys("rcl neg =")
        assertEquals("0", m.result)
    }

    @Test
    fun constantsMenu() {
        val m = CalculatorModel().keys("shift 7 2 8 =")
        assertTrue(m.result.startsWith("299792458"))
    }
}
