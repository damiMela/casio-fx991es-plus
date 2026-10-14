package com.damimela.fx991.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class StatBaseTest {
    private val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0)

    @Test fun oneVariable() {
        val r = Stat.compute(StatType.ONE_VAR, x, null)
        assertEquals(5.0, r["n"], 1e-12)
        assertEquals(3.0, r["x̄"], 1e-12)
        assertEquals(15.0, r["Σx"], 1e-12)
        assertEquals(55.0, r["Σx²"], 1e-12)
        assertEquals(Math.sqrt(2.0), r["σx"], 1e-12)
        assertEquals(Math.sqrt(2.5), r["sx"], 1e-12)
        assertEquals(1.0, r["minX"], 0.0); assertEquals(5.0, r["maxX"], 0.0)
        assertEquals(1.5, r["Q1"], 1e-12); assertEquals(3.0, r["Med"], 1e-12); assertEquals(4.5, r["Q3"], 1e-12)
    }

    @Test fun frequencies() {
        val r = Stat.compute(StatType.ONE_VAR, listOf(1.0, 2.0), null, listOf(3.0, 1.0))
        assertEquals(4.0, r["n"], 1e-12)
        assertEquals(1.25, r["x̄"], 1e-12)
        assertEquals(1.0, r["Med"], 1e-12)
    }

    @Test fun linearRegression() {
        val y = listOf(2.0, 4.0, 6.0, 8.0, 10.0)
        val r = Stat.compute(StatType.LINEAR, x, y)
        assertEquals(0.0, r["a"], 1e-9); assertEquals(2.0, r["b"], 1e-9); assertEquals(1.0, r["r"], 1e-9)
        assertEquals(14.0, r.predictY(7.0), 1e-9)
        assertEquals(3.5, r.predictX(7.0).single(), 1e-9)
        assertEquals(110.0, r["Σxy"], 1e-9)
    }

    @Test fun quadraticRegression() {
        val y = x.map { 1 + 2 * it + 3 * it * it }
        val r = Stat.compute(StatType.QUAD, x, y)
        assertEquals(1.0, r["a"], 1e-8); assertEquals(2.0, r["b"], 1e-8); assertEquals(3.0, r["c"], 1e-8)
        assertEquals(2, r.predictX(17.0).size)
    }

    @Test fun transformedRegressions() {
        val e = Stat.compute(StatType.EXP, x, x.map { 2 * Math.exp(0.5 * it) })
        assertEquals(2.0, e["a"], 1e-9); assertEquals(0.5, e["b"], 1e-9)
        val ab = Stat.compute(StatType.AB, x, x.map { 3 * Math.pow(2.0, it) })
        assertEquals(3.0, ab["a"], 1e-9); assertEquals(2.0, ab["b"], 1e-9)
        val p = Stat.compute(StatType.POWER, x, x.map { 2 * Math.pow(it, 1.5) })
        assertEquals(2.0, p["a"], 1e-9); assertEquals(1.5, p["b"], 1e-9)
        val l = Stat.compute(StatType.LOG, x, x.map { 1 + 2 * Math.log(it) })
        assertEquals(1.0, l["a"], 1e-9); assertEquals(2.0, l["b"], 1e-9)
        val i = Stat.compute(StatType.INV, x, x.map { 1 + 2 / it })
        assertEquals(1.0, i["a"], 1e-9); assertEquals(2.0, i["b"], 1e-9)
        assertEquals(1.0, i["r"], 1e-9)
    }

    @Test fun baseNFormatting() {
        assertEquals("FF", BaseN.format(255, 16))
        assertEquals("FFFFFFFF", BaseN.format(-1, 16))
        assertEquals("11111111", BaseN.format(255, 2))
        assertEquals("377", BaseN.format(255, 8))
        assertEquals("-1", BaseN.format(-1, 10))
        assertEquals("11111111111111111111111111111111", BaseN.format(-1, 2))
    }

    private fun base(src: String, b: Int): Long {
        val ctx = newCtx().also { it.baseMode = b }
        return BaseN.toLong(Engine.evaluate(src, ctx).value)
    }

    @Test fun baseNArithmeticAndLogic() {
        assertEquals(255L, base("FF", 16))
        assertEquals(256L, base("FF+1", 16))
        assertEquals(10L, base("5+5", 10))
        assertEquals(2L, base("1010and111", 2))
        assertEquals(15L, base("1010or111", 2))
        assertEquals(13L, base("1010xor111", 2))
        assertEquals(-1L, base("Not(0)", 10))
        assertEquals(-5L, base("Neg(5)", 10))
        assertEquals(-1L, base("FFFFFFFF", 16))
        assertEquals(-2L, base("FFFFFFFFxor1", 16))
        assertEquals(-1L, base("0 xnor 0", 16))
        assertEquals(3L, base("10÷3", 10))
        assertEquals(7L, base("A-3", 16))
    }

    @Test fun baseNErrors() {
        val ctx = newCtx().also { it.baseMode = 10 }
        try { Engine.evaluate("2147483647+1", ctx); throw AssertionError() } catch (e: CalcError) { assertEquals(ErrorKind.MATH, e.kind) }
        try { Engine.evaluate("sin(1)", ctx); throw AssertionError() } catch (e: CalcError) { assertEquals(ErrorKind.SYNTAX, e.kind) }
    }

    @Test fun table() {
        val ctx = newCtx()
        val rows = TableGen.generate("X²", "2X", 1.0, 5.0, 1.0, ctx)
        assertEquals(5, rows.size)
        assertEquals(9.0, rows[2].f!!, 0.0); assertEquals(6.0, rows[2].g!!, 0.0)
        val rows2 = TableGen.generate("1÷X", null, -1.0, 1.0, 1.0, ctx)
        assertEquals(null, rows2[1].f)
        assertEquals(null, rows2[1].g)
        try { TableGen.generate("X", null, 1.0, 100.0, 1.0, ctx); throw AssertionError() } catch (e: CalcError) { assertEquals(ErrorKind.ARGUMENT, e.kind) }
        try { TableGen.generate("X+", null, 1.0, 3.0, 1.0, ctx); throw AssertionError() } catch (e: CalcError) { assertEquals(ErrorKind.SYNTAX, e.kind) }
    }
}
