package com.damimela.fx991.engine

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.max

/** EQN mode: polynomial and linear-system solvers. */
object Poly {
    class Result(val roots: List<Value>, val discriminant: Real?)

    /** [coef] are the real coefficients, highest degree first (a, b, c, ...). */
    fun solve(coef: List<Real>): Result {
        if (coef.size < 3 || coef.size > 5) throw CalcError(ErrorKind.ARGUMENT)
        if (coef[0].isZero) throw CalcError(ErrorKind.MATH)
        return when (coef.size) {
            3 -> Result(quadratic(coef[0], coef[1], coef[2]), discriminant(coef[0], coef[1], coef[2]))
            else -> Result(sortRoots(higher(coef)), null)
        }
    }

    fun discriminant(a: Real, b: Real, c: Real): Real = b * b - Real.of(4) * a * c

    /** x1 uses +sqrt(D) as on the Casio. Exact whenever the coefficients are exact. */
    fun quadratic(a: Real, b: Real, c: Real): List<Value> {
        val d = discriminant(a, b, c)
        val two = Real.of(2) * a
        val s: Value = if (d.d >= 0) Ops.sqrtReal(d) else cplx(Real.ZERO, Ops.sqrtReal(-d))
        val mb = -b
        val x1 = Ops.div(Ops.add(mb, s), two)
        val x2 = Ops.div(Ops.sub(mb, s), two)
        return listOf(x1, x2)
    }

    private fun higher(coef: List<Real>): List<Value> {
        val allRational = coef.all { it.q != null }
        if (allRational) {
            val roots = ArrayList<Value>()
            var p = coef.map { it.q!! }
            while (p.size > 3) {
                val r = rationalRoot(p) ?: break
                roots += Real.of(r)
                p = deflate(p, r)
            }
            if (p.size == 3) {
                roots += quadratic(Real.of(p[0]), Real.of(p[1]), Real.of(p[2]))
                return roots
            }
            if (p.size == 2) { roots += Real.of(-p[1] / p[0]); return roots }
            roots += numeric(p.map { it.toDouble() })
            return roots
        }
        return numeric(coef.map { it.d })
    }

    private fun deflate(p: List<Frac>, root: Frac): List<Frac> {
        val out = ArrayList<Frac>()
        var carry = Frac.ZERO
        for (i in 0 until p.size - 1) {
            carry = p[i] + carry * root
            out += carry
        }
        return out
    }

    private fun eval(p: List<Frac>, x: Frac): Frac {
        var r = Frac.ZERO
        for (c in p) r = r * x + c
        return r
    }

    private fun divisors(n: BigInteger): List<BigInteger> {
        val res = ArrayList<BigInteger>()
        val v = n.abs()
        if (v.signum() == 0) return listOf(BigInteger.ONE)
        var i = 1L
        while (BigInteger.valueOf(i * i) <= v) {
            val bi = BigInteger.valueOf(i)
            if ((v % bi).signum() == 0) { res += bi; if (bi * bi != v) res += v / bi }
            i++
        }
        return res
    }

    private fun rationalRoot(p: List<Frac>): Frac? {
        if (eval(p, Frac.ZERO).isZero) return Frac.ZERO
        var lcm = BigInteger.ONE
        for (c in p) lcm = lcm / lcm.gcd(c.den) * c.den
        val ints = p.map { (it * Frac.of(lcm)).num }
        val a0 = ints.last(); val an = ints.first()
        if (a0.abs().bitLength() > 40 || an.abs().bitLength() > 40) return null
        for (pp in divisors(a0)) for (qq in divisors(an)) for (sg in listOf(1, -1)) {
            val cand = Frac.of(pp * BigInteger.valueOf(sg.toLong()), qq)
            if (eval(p, cand).isZero) return cand
        }
        return null
    }

    /** Durand-Kerner iteration followed by Newton polishing. */
    fun numeric(c: List<Double>): List<Value> {
        val n = c.size - 1
        val a = c.map { it / c[0] }
        fun f(z: C): C { var r = C(0.0); for (k in a) r = r * z + C(k); return r }
        fun df(z: C): C {
            var r = C(0.0)
            for (i in 0 until n) r = r * z + C(a[i] * (n - i))
            return r
        }
        val radius = 1 + (a.drop(1).maxOfOrNull { abs(it) } ?: 0.0)
        val z = Array(n) { C(radius * 0.5, 0.0) * C.exp(C(0.0, 2 * Math.PI * it / n + 0.4)) }
        for (iter in 0 until 1000) {
            var delta = 0.0
            for (i in 0 until n) {
                var den = C(1.0)
                for (j in 0 until n) if (j != i) den = den * (z[i] - z[j])
                if (den.abs() == 0.0) { z[i] = z[i] + C(1e-8, 1e-8); continue }
                val step = f(z[i]) / den
                z[i] = z[i] - step
                delta = max(delta, step.abs())
            }
            if (delta < 1e-15) break
        }
        for (i in 0 until n) repeat(5) {
            val d = df(z[i])
            if (d.abs() > 1e-12) { val s = f(z[i]) / d; if (s.abs() < 1e-3) z[i] = z[i] - s }
        }
        return z.map { w ->
            val tol = 1e-9 * max(1.0, w.abs())
            if (abs(w.im) < tol) cplx(round15(w.re), 0.0) else cplx(round15(w.re), round15(w.im))
        }
    }

    private fun sortRoots(r: List<Value>): List<Value> {
        val reals = r.filter { it is Real }.sortedByDescending { (it as Real).d }
        val cx = r.filterIsInstance<Cplx>().sortedWith(compareByDescending<Cplx> { it.re.d }.thenByDescending { it.im.d })
        return reals + cx
    }

    /** Solves a square linear system (2-4 unknowns); rows are [a1, a2, ..., c]. Math ERROR if no unique solution. */
    fun linear(rows: List<List<Real>>): List<Value> {
        val n = rows.size
        if (n < 2 || n > 4 || rows.any { it.size != n + 1 }) throw CalcError(ErrorKind.ARGUMENT)
        val a = MatV.build(n, n) { r, c -> rows[r][c] }
        if (MatrixOps.det(a).isZeroValue()) throw CalcError(ErrorKind.MATH)
        return MatrixOps.solve(a, rows.map { it[n] })
    }
}
