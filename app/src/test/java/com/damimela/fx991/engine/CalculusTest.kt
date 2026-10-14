package com.damimela.fx991.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculusTest {
    @Test fun integrals() {
        assertEquals("9", dec("∫(X²,0,3)"))
        assertEquals("2", dec("∫(sin(X),0,π)", newCtx(AngleUnit.RAD)))
        assertEquals("1", dec("∫(1÷X,1,e)"))
        assertEquals("0.6666666667", dec("∫(√X,0,1)"))
        assertEquals("-9", dec("∫(X²,3,0)"))
        assertEquals("0.5", dec("∫(X,0,1)"))
        assertEquals("1.772453851", dec("∫(e^(-X²),-10,10)"))
        assertEquals("0.8862269255", dec("∫(e^(-X²),0,10)"))
    }

    @Test fun derivatives() {
        assertEquals("6", dec("d/dx(X²,3)"))
        assertEquals("1", dec("d/dx(sin(X),0)", newCtx(AngleUnit.RAD)))
        assertEquals("0.5", dec("d/dx(√X,1)"))
        assertEquals("12", dec("d/dx(X³,2)"))
        assertEquals("-0.25", dec("d/dx(1÷X,2)"))
    }

    @Test fun summation() {
        assertEquals("55", calc("Σ(X²,X,1,5)"))
        assertEquals("15", calc("Σ(X,X,1,5)"))
        assertEquals("1023", calc("Σ(2^X,X,0,9)"))
        assertEquals("137⌟60", calc("Σ(1÷X,X,1,5)"))
    }

    @Test fun solveAndCalc() {
        val ctx = newCtx()
        val r = Engine.solve("X²-4=0", ctx, 1.0)
        assertEquals(2.0, r.x, 1e-9)
        assertEquals(0.0, r.diff, 1e-9)
        assertEquals(-2.0, Engine.solve("X²-4=0", ctx, -5.0).x, 1e-9)
        assertEquals(0.7390851332, Engine.solve("cos(X)=X", newCtx(AngleUnit.RAD), 0.0).x, 1e-9)
        assertEquals(1.0, Engine.solve("X³+X-2=0", ctx, 10.0).x, 1e-9)
        assertEquals(3.0, Engine.solve("2X+1=7", ctx, 0.0).x, 1e-9)
        assertEquals(2.0, Engine.solve("ln(X)=ln(2)", ctx, 5.0).x, 1e-9)
        assertEquals(1.0, Engine.solve("X²-2X+1", ctx, 3.0).x, 1e-6)
        assertEquals(-1.0, Engine.solve("e^X=1÷e", ctx, 3.0).x, 1e-9)
        val v = Engine.calc("X²+Y", ctx, mapOf("X" to Real.of(3), "Y" to Real.of(1)))
        assertEquals("10", Formatter.text(v, DEFAULT_SETTINGS))
        assertEquals(Real.ZERO.d, ctx.variable("X").let { (it as Real).d }, 0.0)
    }

    @Test fun noRoot() {
        try { Engine.solve("X²+1=0", newCtx(), 0.0); throw AssertionError() } catch (e: CalcError) { assertEquals(ErrorKind.MATH, e.kind) }
    }
}
