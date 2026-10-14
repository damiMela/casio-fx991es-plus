package com.damimela.fx991.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ComplexTest {
    private val cx get() = newCtx(complex = true)

    @Test fun arithmetic() {
        assertEquals("4+6i", calc("(1+2i)+(3+4i)", cx).replace(" ", ""))
        assertEquals("-2+2i", calc("(1+2i)-(3+0i)", cx).replace(" ", ""))
        assertEquals("-5+10i", calc("(1+2i)(3+4i)", cx).replace(" ", ""))
                assertEquals("-1", calc("i²", cx))
        assertEquals("i", calc("√(-1)", cx))
        assertEquals("2i", calc("√(-4)", cx))
        assertEquals("√(2)i", calc("√(-2)", cx))
        assertEquals("1", calc("i^4", cx))
        assertEquals("-i", calc("i³", cx))
        assertEquals("-i", calc("-i", cx))
        assertEquals("1+i", calc("1+i", cx).replace(" ", ""))
        assertEquals("1-i", calc("1-i", cx).replace(" ", ""))
    }

    @Test fun exactComplexDivision() {
        assertEquals("11⌟25+2⌟25i", calc("(1+2i)÷(3+4i)", cx).replace(" ", ""))
        assertEquals("1⌟2-1⌟2i", calc("1÷(1+i)", cx).replace(" ", ""))
    }

    @Test fun functions() {
        assertEquals("5", calc("Abs(3+4i)", cx))
        assertEquals("53.13010235", dec("Arg(3+4i)", cx))
        assertEquals("3-4i", calc("Conjg(3+4i)", cx).replace(" ", ""))
        assertEquals("3", calc("ReP(3+4i)", cx))
        assertEquals("4", calc("ImP(3+4i)", cx))
        assertEquals("90", calc("Arg(i)", cx))
        assertEquals("1.570796327", dec("Arg(i)", newCtx(AngleUnit.RAD, true)))
    }

    @Test fun polarForm() {
        val polar = DisplaySettings(polarComplex = true)
        assertEquals("5∠53.13010235", calc("3+4i", cx, polar, exact = false))
        assertEquals("1+√(3)i", calc("2∠60", cx).replace(" ", ""))
        assertEquals("2∠60", calc("1+√3i", cx, polar).replace(" ", ""))
    }

    @Test fun transcendental() {
        // e^(iπ) = -1
        assertEquals("-1", dec("e^(iπ)", newCtx(AngleUnit.RAD, true)))
        assertEquals("1.543080635", dec("cosh(1)", cx))
        assertEquals("3.141592654i", dec("ln(-1)", cx).replace(" ", ""))
        assertEquals("i", dec("e^(iπ÷2)", newCtx(AngleUnit.RAD, true)).replace(" ", ""))
        assertEquals("-1+i", dec("(-1+i)", cx).replace(" ", ""))
    }

    @Test fun ansHandling() {
        val c = cx
        Engine.evaluate("1+i", c)
        assertEquals("2i", calc("Ans²", c))
        assertEquals("-4", calc("Ans²", c))
    }

    @Test fun compModeRejectsComplex() {
        assertError(ErrorKind.SYNTAX, "2i")
        assertError(ErrorKind.MATH, "√(-4)")
    }
}
