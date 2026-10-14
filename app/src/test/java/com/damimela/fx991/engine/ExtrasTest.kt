package com.damimela.fx991.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtrasTest {
    @Test
    fun statVariablesInExpressions() {
        val ctx = newCtx()
        ctx.stat = Stat.compute(StatType.ONE_VAR, listOf(1.0, 2.0, 3.0, 4.0, 5.0), null)
        assertEquals(3.0, num("@xbar", ctx), 1e-12)
        assertEquals(15.0, num("@sumx", ctx), 1e-12)
        assertEquals(30.0, num("@xbar×10", ctx), 1e-12)
        assertError(ErrorKind.MATH, "@xbar")
    }

    @Test
    fun regressionPrediction() {
        val ctx = newCtx()
        ctx.stat = Stat.compute(StatType.LINEAR, listOf(1.0, 2.0, 3.0), listOf(3.0, 5.0, 7.0))
        assertEquals(11.0, num("@yhat(5)", ctx), 1e-9)
        assertEquals(4.0, num("@xhat(9)", ctx), 1e-9)
    }

    @Test
    fun normalDistribution() {
        assertEquals(0.5, num("@P(0)"), 1e-12)
        assertEquals(0.3413447460685429, num("@Q(1)"), 1e-12)
        assertEquals(0.15865525393145707, num("@R(1)"), 1e-12)
    }

    @Test
    fun angleSuffixes() {
        assertEquals(180.0, num("πʳ"), 1e-9)
        assertEquals(90.0, num("100ᵍ"), 1e-9)
        assertEquals(1.0, num("1ʳ", newCtx(AngleUnit.RAD)), 1e-12)
    }

    @Test
    fun randomMatrix() {
        val v = Engine.evaluate("RanMat(2,3)", newCtx()).value
        assertEquals(2, (v as MatV).rows)
        assertEquals(3, v.cols)
    }
}
