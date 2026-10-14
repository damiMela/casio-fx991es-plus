package com.damimela.fx991.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MatrixTest {
    private fun m(vararg rows: List<Int>) = MatV.of(rows.map { r -> r.map { Real.of(it) as Value } })
    private fun ctxWith(vararg named: Pair<String, MatV>) = newCtx(complex = true).also { c -> named.forEach { c.mats[it.first] = it.second } }
    private fun show(v: Value) = Formatter.text(v, DEFAULT_SETTINGS).replace(" ", "")

    @Test fun determinant() {
        assertEquals("-2", show(MatrixOps.det(m(listOf(1, 2), listOf(3, 4)))))
        assertEquals("6", show(MatrixOps.det(m(listOf(2, 0, 1), listOf(1, 3, 2), listOf(1, 1, 2)))))
        assertEquals("0", show(MatrixOps.det(m(listOf(1, 2), listOf(2, 4)))))
        assertEquals("1", show(MatrixOps.det(Ops.identity(4))))
        assertEquals("-30", show(MatrixOps.det(m(listOf(0, 2, 0, 0), listOf(3, 0, 0, 0), listOf(0, 0, 5, 0), listOf(0, 0, 0, 1)))))
    }

    @Test fun inverseExact() {
        val inv = MatrixOps.inverse(m(listOf(1, 2), listOf(3, 4)))
        assertEquals("[-2 1;3⌟2 -1⌟2]".replace(" ", ""), show(inv))
        val prod = Ops.mul(m(listOf(1, 2), listOf(3, 4)), inv)
        assertEquals(show(Ops.identity(2)), show(prod))
    }

    @Test fun singularInverse() {
        try { MatrixOps.inverse(m(listOf(1, 2), listOf(2, 4))); throw AssertionError() } catch (e: CalcError) { assertEquals(ErrorKind.MATH, e.kind) }
    }

    @Test fun multiplyAddTranspose() {
        val a = m(listOf(1, 2), listOf(3, 4)); val b = m(listOf(0, 1), listOf(1, 0))
        assertEquals("[2 1;4 3]".replace(" ", ""), show(Ops.mul(a, b)))
        assertEquals("[1 3;4 4]".replace(" ", ""), show(Ops.add(a, b)))
        assertEquals("[1 3;2 4]".replace(" ", ""), show(MatrixOps.transpose(a)))
        assertEquals("[2 4;6 8]".replace(" ", ""), show(Ops.mul(Real.of(2), a)))
    }

    @Test fun dimensionError() {
        try { Ops.mul(m(listOf(1, 2, 3)), m(listOf(1, 2, 3))); throw AssertionError() } catch (e: CalcError) { assertEquals(ErrorKind.DIMENSION, e.kind) }
        try { Ops.add(m(listOf(1, 2)), m(listOf(1, 2, 3))); throw AssertionError() } catch (e: CalcError) { assertEquals(ErrorKind.DIMENSION, e.kind) }
    }

    @Test fun expressionsWithMatrices() {
        val ctx = ctxWith("MatA" to m(listOf(1, 2), listOf(3, 4)), "MatB" to m(listOf(1, 0), listOf(0, 1)))
        assertEquals("[7 10;15 22]".replace(" ", ""), calc("MatA²", ctx).replace(" ", ""))
        assertEquals("[37 54;81 118]".replace(" ", ""), calc("MatA³", ctx).replace(" ", ""))
        assertEquals("-2", calc("Det(MatA)", ctx))
        assertEquals("[1 3;2 4]".replace(" ", ""), calc("Trn(MatA)", ctx).replace(" ", ""))
        assertEquals("[2 2;3 5]".replace(" ", ""), calc("MatA+MatB", ctx).replace(" ", ""))
        assertEquals("[1 0;0 1]".replace(" ", ""), calc("MatA×MatA⁻¹", ctx).replace(" ", ""))
        assertEquals("[-2 1;3⌟2 -1⌟2]".replace(" ", ""), calc("MatA⁻¹", ctx).replace(" ", ""))
        assertEquals("[2 4;6 8]".replace(" ", ""), calc("2MatA", ctx).replace(" ", ""))
        assertEquals("[1 0 0;0 1 0;0 0 1]".replace(" ", ""), calc("Ide(3)", ctx).replace(" ", ""))
        assertEquals("[7 10;15 22]".replace(" ", ""), calc("MatA^2", ctx).replace(" ", ""))
        assertEquals("[7 10;15 22]".replace(" ", ""), calc("MatAns", ctx).replace(" ", ""))
        assertEquals("[-2 1;3⌟2 -1⌟2]".replace(" ", ""), calc("MatA^(-1)", ctx).replace(" ", ""))
        assertError(ErrorKind.MATH, "MatC", ctx)
    }

    @Test fun fractionalEntriesAndComplexEntries() {
        val a = MatV.of(listOf(listOf(Real.of(Frac.of(1, 2)), Real.of(Frac.of(1, 3))), listOf(Real.of(1), Real.of(1))))
        assertEquals("1⌟6", show(MatrixOps.det(a)))
        val c = MatV.of(listOf(listOf(cplx(Real.ZERO, Real.ONE), Real.ZERO), listOf(Real.ZERO, Real.ONE)))
        assertEquals("i", show(MatrixOps.det(c)))
    }

    @Test fun fourByFourInverse() {
        val a = m(listOf(2, 0, 0, 0), listOf(0, 3, 0, 0), listOf(0, 0, 4, 0), listOf(1, 0, 0, 5))
        val prod = Ops.mul(a, MatrixOps.inverse(a))
        assertEquals(show(Ops.identity(4)), show(prod))
    }

    @Test fun vectors() {
        val a = VecV(listOf(1, 2, 3).map { Real.of(it) })
        val b = VecV(listOf(4, 5, 6).map { Real.of(it) })
        val ctx = newCtx().also { it.vecs["VctA"] = a; it.vecs["VctB"] = b }
        assertEquals("32", calc("Dot(VctA,VctB)", ctx))
        assertEquals("[-3 6 -3]".replace(" ", ""), calc("VctA×VctB", ctx).replace(" ", ""))
        assertEquals("[5 7 9]".replace(" ", ""), calc("VctA+VctB", ctx).replace(" ", ""))
        assertEquals("[2 4 6]".replace(" ", ""), calc("2VctA", ctx).replace(" ", ""))
        assertEquals("√(14)", calc("Abs(VctA)", ctx))
        assertEquals("12.93315449", dec("Angle(VctA,VctB)", ctx))
        assertEquals("[√(14)⌟14 √(14)⌟7 3√(14)⌟14]".replace(" ", ""), calc("UnitV(VctA)", ctx).replace(" ", ""))
        val v2 = VecV(listOf(Real.of(3), Real.of(4)))
        ctx.vecs["VctC"] = v2
        assertEquals("5", calc("Abs(VctC)", ctx))
    }
}
