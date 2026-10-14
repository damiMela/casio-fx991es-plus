package com.damimela.fx991.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArithmeticTest {
    @Test fun basicOperations() {
        assertEquals("7", calc("1+2×3"))
        assertEquals("1", calc("6÷2(1+2)"))
        assertEquals("-4", calc("-2^2"))
        assertEquals("512", calc("2^3^2"))
        assertEquals("0.3", calc("0.1+0.2"))
        assertEquals("14", calc("2×3+4×2"))
    }

    @Test fun fractionsStayExact() {
        assertEquals("1⌟3", calc("1÷3"))
        assertEquals("5⌟6", calc("1÷2+1÷3"))
        assertEquals("0.3333333333", dec("1÷3"))
        assertEquals("-7⌟3", calc("-7÷3"))
        assertEquals("2", calc("4÷2"))
        assertEquals("1⌟8", calc("2^(-3)"))
    }

    @Test fun mixedFractionDisplay() {
        val s = DisplaySettings(mixedFractions = true)
        assertEquals("2⌟1⌟3", calc("7÷3", settings = s))
        assertEquals("-2⌟1⌟3", calc("-7÷3", settings = s))
    }

    @Test fun radicals() {
        assertEquals("√(2)", calc("√2"))
        assertEquals("2√(2)", calc("√8"))
        assertEquals("√(2)⌟2", calc("1÷√2"))
        assertEquals("3", calc("√9"))
        assertEquals("1⌟2", calc("√(1÷4)"))
        assertEquals("1.414213562", dec("√2"))
        assertEquals("5√(2)", calc("√2×5"))
        assertEquals("(-1+√(5))⌟2", calc("(-1+√5)÷2"))
        assertEquals("3", calc("√2×√2+1"))
        assertEquals("2", calc("∛8"))
        assertEquals("-2", calc("∛(-8)"))
        assertEquals("3", calc("root(4,81)"))
    }

    @Test fun piMultiples() {
        assertEquals("2π", calc("2π"))
        assertEquals("π⌟2", calc("π÷2"))
        assertEquals("3.141592654", dec("π"))
        assertEquals("6.283185307", dec("2π"))
    }

    @Test fun powersAndPostfix() {
        assertEquals("25", calc("5²"))
        assertEquals("27", calc("3³"))
        assertEquals("1⌟4", calc("4⁻¹"))
        assertEquals("120", calc("5!"))
        assertEquals("1", calc("0!"))
        assertEquals("0.5", dec("50%"))
        assertEquals("1⌟2", calc("50%"))
        assertEquals("8", calc("2^3"))
        assertEquals("4", calc("16^(1÷2)"))
        assertEquals("-2", calc("(-8)^(1÷3)"))
    }

    @Test fun implicitMultiplication() {
        assertEquals("6", calc("2(3)"))
        assertEquals("2π", calc("2π"))
        assertEquals("0.5", dec("sin(30)"))
        assertEquals("3⌟2", calc("3sin(30)"))
        assertEquals("12", calc("(1+2)(4)"))
    }

    @Test fun scientificNotation() {
        assertEquals("1500", calc("1.5ᴇ3"))
        assertEquals("1000", calc("ᴇ3"))
        assertEquals("1.5×10⁻³", calc("1.5ᴇ-3"))
        assertEquals("0.015", calc("1.5ᴇ-2"))
        assertEquals("1.5×10²⁰", calc("1.5ᴇ20"))
        assertEquals("2000", calc("2×10^3"))
    }

    @Test fun functionsOfIntegers() {
        assertEquals("2", calc("log(100)"))
        assertEquals("3", calc("log(2,8)"))
        assertEquals("0", calc("ln(1)"))
        assertEquals("1", dec("ln(e)"))
        assertEquals("2.718281828", dec("e"))
        assertEquals("7.389056099", dec("e^2"))
        assertEquals("20", calc("5nPr2"))
        assertEquals("10", calc("5nCr2"))
        assertEquals("6", calc("GCD(12,18)"))
        assertEquals("36", calc("LCM(12,18)"))
        assertEquals("5", calc("Abs(-5)"))
        assertEquals("-2", calc("Int(-2.5)"))
        assertEquals("-3", calc("Intg(-2.5)"))
        assertEquals("2", calc("Int(2.7)"))
    }

    @Test fun limitsAndFormats() {
        assertEquals("1×10¹⁰", calc("10^10"))
        assertEquals("1000000000", calc("10^9"))
        assertEquals("1×10¹¹", calc("10^11"))
        assertEquals("9999999999", calc("9999999999"))
        assertEquals("1.711224524×10⁹⁸", calc("69!"))
        assertEquals("1×10⁻³", calc("0.001"))
        assertEquals("1.234567891×10⁻⁵", calc("0.00001234567891"))
        assertEquals("0.01", calc("0.01"))
    }

    @Test fun fixSciNormDisplay() {
        val fix3 = DisplaySettings(format = NumFormat.FIX, digits = 3)
        assertEquals("3.333", calc("10÷3", settings = fix3, exact = false))
        assertEquals("0.000", calc("0", settings = fix3))
        val sci4 = DisplaySettings(format = NumFormat.SCI, digits = 4)
        assertEquals("3.333×10⁰", calc("10÷3", settings = sci4, exact = false))
        assertEquals("1.235×10⁴", calc("12345.678", settings = sci4))
        val norm2 = DisplaySettings(format = NumFormat.NORM2)
        assertEquals("0.000123", calc("0.000123", settings = norm2))
        val comma = DisplaySettings(decimalComma = true)
        assertEquals("0,5", calc("0.5", settings = comma))
    }

    @Test fun engineeringNotation() {
        val ctx = newCtx()
        val v = Engine.evaluate("12345", ctx).value
        assertEquals("12.345×10³", Formatter.plain(Formatter.format(v, DEFAULT_SETTINGS, eng = 0)))
        assertEquals("12345", Formatter.plain(Formatter.format(v, DEFAULT_SETTINGS, eng = null)))
        assertEquals("0.012345×10⁶", Formatter.plain(Formatter.format(v, DEFAULT_SETTINGS, eng = 1)))
        val sym = DisplaySettings(engSymbols = true)
        assertEquals("12.345k", Formatter.plain(Formatter.format(v, sym, eng = 0)))
    }

    @Test fun dms() {
        assertEquals("30.5", dec("30°30′"))
        assertEquals("1.508333333", dec("1°30′30″"))
        assertEquals("1°30′0″", Formatter.dms(1.5))
        assertEquals("12°34′56″", Formatter.dms(12 + 34 / 60.0 + 56 / 3600.0))
    }

    @Test fun constantsAndConversions() {
        assertEquals("299792458", calc("CONST(28)"))
        assertEquals("6.62606957×10⁻³⁴", calc("CONST(6)"))
        assertEquals("2.54", dec("1CONV(1)"))
        assertEquals("212", dec("100CONV(38)"))
        assertEquals("40", Constants.CONSTS.size.toString())
        assertEquals("40", Constants.CONVERSIONS.size.toString())
    }

    @Test fun variablesAndAns() {
        val ctx = newCtx()
        Engine.evaluate("5→A", ctx)
        assertEquals("15", calc("A×3", ctx))
        assertEquals("15", calc("Ans", ctx))
        assertEquals("30", calc("Ans×2", ctx))
        assertEquals("15", calc("PreAns", ctx))
        Engine.evaluate("2→X", ctx)
        assertEquals("4", calc("X²", ctx))
        assertEquals("10", calc("2A", ctx))
        assertEquals("0", calc("B", ctx))
    }

    @Test fun errors() {
        assertError(ErrorKind.MATH, "1÷0")
        assertError(ErrorKind.MATH, "√(-1)")
        assertError(ErrorKind.MATH, "ln(0)")
        assertError(ErrorKind.MATH, "0^0")
        assertError(ErrorKind.MATH, "70!")
        assertError(ErrorKind.MATH, "10^100")
        assertError(ErrorKind.MATH, "tan(90)")
        assertError(ErrorKind.MATH, "asin(2)")
        assertError(ErrorKind.SYNTAX, "1+")
        assertError(ErrorKind.SYNTAX, "×3")
        assertError(ErrorKind.SYNTAX, "3)")
        assertError(ErrorKind.SYNTAX, "i")
        assertError(ErrorKind.ARGUMENT, "GCD(1)")
        assertError(ErrorKind.STACK, "(".repeat(30) + "1" + ")".repeat(30))
    }

    @Test fun errorPosition() {
        try { Engine.evaluate("2+*3", newCtx()); throw AssertionError() } catch (e: CalcError) { assertEquals(2, e.pos) }
        try { Engine.evaluate("1+1÷0", newCtx()); throw AssertionError() } catch (e: CalcError) { assertTrue(e.pos >= 0) }
    }

    @Test fun missingClosingParenAllowed() {
        assertEquals("7", calc("(3+4"))
        assertEquals("1⌟2", calc("sin(30"))
    }

    @Test fun rndUsesDisplayDigits() {
        val ctx = newCtx()
        ctx.rnd = { x -> Math.round(x * 100) / 100.0 }
        assertEquals("0.67", calc("Rnd(2÷3)", ctx, exact = false))
    }

    @Test fun randomFunctions() {
        val ctx = newCtx()
        repeat(50) {
            val r = num("Ran#", ctx)
            assertTrue(r >= 0 && r < 1)
            val k = num("RanInt#(3,6)", ctx)
            assertTrue(k in 3.0..6.0)
        }
    }
}
