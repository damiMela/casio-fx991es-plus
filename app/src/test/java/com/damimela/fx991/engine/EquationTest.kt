package com.damimela.fx991.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EquationTest {
    private fun r(vararg v: Int) = v.map { Real.of(it) }
    private fun show(v: Value, exact: Boolean = true) = Formatter.text(v, DEFAULT_SETTINGS, exact).replace(" ", "")

    @Test fun quadraticRational() {
        val res = Poly.solve(r(1, -3, 2))
        assertEquals(listOf("2", "1"), res.roots.map { show(it) })
        assertEquals("1", show(res.discriminant!!))
    }

    @Test fun quadraticRadicals() {
        val res = Poly.solve(r(1, 1, -1))
        assertEquals(listOf("(-1+√(5))⌟2", "(-1-√(5))⌟2"), res.roots.map { show(it) })
        assertEquals(listOf("0.6180339887", "-1.618033989"), res.roots.map { show(it, false) })
    }

    @Test fun quadraticComplex() {
        val res = Poly.solve(r(1, 2, 5))
        assertEquals(listOf("-1+2i", "-1-2i"), res.roots.map { show(it) })
        val res2 = Poly.solve(r(1, 1, 1))
        assertEquals(listOf("-1⌟2+√(3)⌟2i", "-1⌟2-√(3)⌟2i"), res2.roots.map { show(it) })
    }

    @Test fun quadraticDoubleRootAndFractions() {
        val res = Poly.solve(r(1, -2, 1))
        assertEquals(listOf("1", "1"), res.roots.map { show(it) })
        assertEquals(listOf("1", "-1⌟2"), Poly.solve(r(2, -1, -1)).roots.map { show(it) })
        assertEquals("0", show(Poly.discriminant(Real.of(1), Real.of(-2), Real.of(1))))
    }

    @Test fun leadingZeroIsMathError() {
        try { Poly.solve(r(0, 1, 2)); throw AssertionError() } catch (e: CalcError) { assertEquals(ErrorKind.MATH, e.kind) }
    }

    @Test fun cubicRationalRoots() {
        val res = Poly.solve(r(1, -6, 11, -6))
        assertEquals(listOf("3", "2", "1"), res.roots.map { show(it) })
        val res2 = Poly.solve(r(1, 0, 0, -8))
        // x^3 = 8 -> 2 and complex pair -1 ± √3 i
        assertEquals(listOf("2", "-1+√(3)i", "-1-√(3)i"), res2.roots.map { show(it) })
    }

    @Test fun cubicIrrational() {
        val res = Poly.solve(r(1, 0, -2, -5))
        val x = res.roots.filterIsInstance<Real>().single().d
        assertEquals(0.0, x * x * x - 2 * x - 5, 1e-9)
        assertEquals(3, res.roots.size)
    }

    @Test fun quarticRoots() {
        assertEquals(listOf("4", "3", "2", "1"), Poly.solve(r(1, -10, 35, -50, 24)).roots.map { show(it) })
        val res = Poly.solve(r(1, 0, 0, 0, 4))   // x^4 = -4 -> ±1±i
        assertEquals(4, res.roots.size)
        for (z in res.roots) {
            val c = z.toC()
            val v = (c * c * c * c).re
            assertEquals(-4.0, v, 1e-9)
        }
    }

    @Test fun linearSystems() {
        val res = Poly.linear(listOf(r(1, 1, 3), r(1, -1, 1)))
        assertEquals(listOf("2", "1"), res.map { show(it) })
        val res3 = Poly.linear(listOf(r(1, 1, 1, 6), r(0, 2, 5, -4), r(2, 5, -1, 27)))
        assertEquals(listOf("5", "3", "-2"), res3.map { show(it) })
        val frac = Poly.linear(listOf(r(2, 3, 1), r(1, 1, 1)))
        assertEquals(listOf("2", "-1"), frac.map { show(it) })
        val f2 = Poly.linear(listOf(r(3, 2, 1), r(1, -1, 0)))
        assertEquals(listOf("1⌟5", "1⌟5"), f2.map { show(it) })
    }

    @Test fun linearSingular() {
        try { Poly.linear(listOf(r(1, 1, 2), r(2, 2, 4))); throw AssertionError() } catch (e: CalcError) { assertEquals(ErrorKind.MATH, e.kind) }
    }

    @Test fun decimalCoefficients() {
        val res = Poly.solve(listOf(Real.of(1.0), Real.of(-3.0), Real.of(2.0)))
        assertTrue(res.roots.all { it is Real })
        assertEquals(listOf("2", "1"), res.roots.map { show(it) })
    }
}
