package com.damimela.fx991.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class TrigTest {
    @Test fun degrees() {
        assertEquals("1⌟2", calc("sin(30)"))
        assertEquals("√(2)⌟2", calc("sin(45)"))
        assertEquals("√(3)⌟2", calc("sin(60)"))
        assertEquals("1", calc("sin(90)"))
        assertEquals("0", calc("sin(180)"))
        assertEquals("-1", calc("cos(180)"))
        assertEquals("0", calc("cos(90)"))
        assertEquals("1", calc("tan(45)"))
        assertEquals("√(3)", calc("tan(60)"))
        assertEquals("-1⌟2", calc("sin(-30)"))
        assertEquals("-1⌟2", calc("sin(210)"))
        assertEquals("0", calc("sin(360)"))
        assertEquals("0.2588190451", dec("sin(15)"))
        assertEquals("0.9659258263", dec("cos(15)"))
    }

    @Test fun radians() {
        val r = newCtx(AngleUnit.RAD)
        assertEquals("1⌟2", calc("sin(π÷6)", r))
        assertEquals("-1", calc("cos(π)", r))
        assertEquals("0.8414709848", dec("sin(1)", r))
        assertEquals("0", calc("sin(π)", r))
        assertEquals("1", calc("sin(π÷2)", r))
        assertEquals("√(3)⌟2", calc("cos(π÷6)", r))
    }

    @Test fun gradians() {
        val g = newCtx(AngleUnit.GRA)
        assertEquals("1", calc("sin(100)", g))
        assertEquals("1", calc("tan(50)", g))
        assertEquals("0", calc("cos(100)", g))
    }

    @Test fun inverse() {
        assertEquals("30", calc("asin(1÷2)"))
        assertEquals("45", calc("atan(1)"))
        assertEquals("60", calc("acos(1÷2)"))
        assertEquals("90", calc("asin(1)"))
        assertEquals("0.3", dec("sin(asin(0.3))"))
        assertEquals("π⌟6", calc("asin(1÷2)", newCtx(AngleUnit.RAD)))
        assertEquals("π⌟4", calc("atan(1)", newCtx(AngleUnit.RAD)))
        assertEquals("100", calc("asin(1)", newCtx(AngleUnit.GRA)))
        assertEquals("63.43494882", dec("atan(2)"))
        assertEquals("0.1", dec("tan(atan(0.1))"))
    }

    @Test fun hyperbolic() {
        assertEquals("1.175201194", dec("sinh(1)"))
        assertEquals("1.543080635", dec("cosh(1)"))
        assertEquals("0.761594156", dec("tanh(1)"))
        assertEquals("1", dec("sinh(asinh(1))"))
        assertEquals("0", calc("sinh(0)"))
        assertEquals("1", calc("cosh(0)"))
        assertEquals("1.316957897", dec("acosh(2)"))
        assertEquals("0.5493061443", dec("atanh(0.5)"))
    }

    @Test fun degreeSuffixAndPolar() {
        assertEquals("1⌟2", calc("sin(30°)", newCtx(AngleUnit.RAD)))
        assertEquals("0.5", dec("sin(30°)", newCtx(AngleUnit.RAD)))
        val ctx = newCtx()
        val pol = Engine.evaluate("Pol(1,1)", ctx)
        assertEquals("√(2)", Formatter.text(pol.value, DEFAULT_SETTINGS))
        assertEquals("45", Formatter.text(pol.extras[1].second, DEFAULT_SETTINGS))
        val rec = Engine.evaluate("Rec(2,60)", ctx)
        assertEquals("1", Formatter.text(rec.value, DEFAULT_SETTINGS))
        assertEquals("√(3)", Formatter.text(rec.extras[1].second, DEFAULT_SETTINGS))
    }
}
