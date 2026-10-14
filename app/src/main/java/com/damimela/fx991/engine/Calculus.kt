package com.damimela.fx991.engine

import kotlin.math.abs
import kotlin.math.max

object Calculus {
    private val XGK = doubleArrayOf(
        0.991455371120812639206854697526329, 0.949107912342758524526189684047851,
        0.864864423359769072789712788640926, 0.741531185599394439863864773280788,
        0.586087235467691130294144838258730, 0.405845151377397166906606412076961,
        0.207784955007898467600689403773245, 0.0
    )
    private val WGK = doubleArrayOf(
        0.022935322010529224963732008058970, 0.063092092629978553290700663189204,
        0.104790010322250183839876322541518, 0.140653259715525918745189590510238,
        0.169004726639267902826583426598550, 0.190350578064785409913256402421014,
        0.204432940075298892414161999234649, 0.209482141084727828012999174891714
    )
    private val WG = doubleArrayOf(
        0.129484966168869693270611432679082, 0.279705391489276667901467771423780,
        0.381830050505118944950369775488975, 0.417959183673469387755102040816327
    )

    private class Seg(val result: Double, val err: Double)

    private fun gk15(f: (Double) -> Double, a: Double, b: Double): Seg {
        val c = (a + b) / 2
        val h = (b - a) / 2
        val fc = f(c)
        var resG = fc * WG[3]
        var resK = fc * WGK[7]
        for (j in 0 until 3) {
            val k = 2 * j + 1
            val dx = h * XGK[k]
            val s = f(c - dx) + f(c + dx)
            resG += WG[j] * s
            resK += WGK[k] * s
        }
        for (j in 0 until 4) {
            val k = 2 * j
            val dx = h * XGK[k]
            resK += WGK[k] * (f(c - dx) + f(c + dx))
        }
        return Seg(resK * h, abs((resK - resG) * h))
    }

    /** Adaptive Gauss-Kronrod (G7,K15) integration. */
    fun integrate(f: (Double) -> Double, a: Double, b: Double): Double {
        if (a == b) return 0.0
        if (a > b) return -integrate(f, b, a)
        val whole = gk15(f, a, b)
        val tol = 1e-11 * max(1.0, abs(whole.result))
        return adapt(f, a, b, whole, tol, 0)
    }

    private fun adapt(f: (Double) -> Double, a: Double, b: Double, s: Seg, tol: Double, depth: Int): Double {
        if (s.err <= tol || depth >= 18) return s.result
        val m = (a + b) / 2
        val l = gk15(f, a, m)
        val r = gk15(f, m, b)
        return adapt(f, a, m, l, tol / 2, depth + 1) + adapt(f, m, b, r, tol / 2, depth + 1)
    }

    /** Numerical derivative by Ridders' method of polynomial extrapolation. */
    fun derivative(f: (Double) -> Double, x: Double): Double {
        var h = 0.05 * max(1.0, abs(x))
        val n = 10
        val a = Array(n) { DoubleArray(n) }
        a[0][0] = (f(x + h) - f(x - h)) / (2 * h)
        var err = Double.MAX_VALUE
        var ans = a[0][0]
        for (i in 1 until n) {
            h /= 1.4
            a[0][i] = (f(x + h) - f(x - h)) / (2 * h)
            var fac = 1.96
            for (j in 1..i) {
                a[j][i] = (a[j - 1][i] * fac - a[j - 1][i - 1]) / (fac - 1)
                fac *= 1.96
                val e = max(abs(a[j][i] - a[j - 1][i]), abs(a[j][i] - a[j - 1][i - 1]))
                if (e <= err) { err = e; ans = a[j][i] }
            }
            if (abs(a[i][i] - a[i - 1][i - 1]) >= 2 * err) break
        }
        return ans
    }

    private fun safe(f: (Double) -> Double, x: Double): Double =
        try { f(x) } catch (e: CalcError) { Double.NaN }

    /** Finds a root of f near [guess]: Newton/secant iteration, falling back to bracketing + bisection. */
    fun solve(f: (Double) -> Double, guess: Double): Double {
        newton(f, guess)?.let { return it }
        var r = 1.0
        while (r <= 1e8) {
            bracket(f, guess - r, guess + r)?.let { return it }
            r *= 4
        }
        throw CalcError(ErrorKind.MATH)
    }

    private fun converged(f: (Double) -> Double, x: Double): Boolean {
        val fx = safe(f, x)
        return !fx.isNaN() && abs(fx) <= 1e-9 * max(1.0, abs(x)) * 1e-3
    }

    private fun newton(f: (Double) -> Double, x0: Double): Double? {
        var x = x0
        var fx = safe(f, x)
        if (fx.isNaN()) return null
        for (it in 0 until 200) {
            if (fx == 0.0) return x
            val h = 1e-7 * max(1.0, abs(x))
            val d = (safe(f, x + h) - safe(f, x - h)) / (2 * h)
            if (d.isNaN() || d == 0.0) return null
            var step = fx / d
            var nx = x - step
            var nf = safe(f, nx)
            var tries = 0
            while ((nf.isNaN() || abs(nf) > abs(fx)) && tries < 30) {
                step /= 2; nx = x - step; nf = safe(f, nx); tries++
            }
            if (nf.isNaN()) return null
            val moved = abs(nx - x)
            x = nx; fx = nf
            if (moved <= 1e-15 * max(1.0, abs(x)) && converged(f, x)) return x
        }
        return if (converged(f, x)) x else null
    }

    private fun bracket(f: (Double) -> Double, lo: Double, hi: Double): Double? {
        val n = 400
        var px = lo
        var pf = safe(f, px)
        for (i in 1..n) {
            val x = lo + (hi - lo) * i / n
            val fx = safe(f, x)
            if (!pf.isNaN() && !fx.isNaN() && (pf == 0.0 || pf * fx < 0)) return bisect(f, px, x)
            px = x; pf = fx
        }
        return null
    }

    private fun bisect(f: (Double) -> Double, a0: Double, b0: Double): Double {
        var a = a0; var b = b0
        var fa = f(a)
        for (i in 0 until 200) {
            val m = (a + b) / 2
            val fm = f(m)
            if (fm == 0.0 || abs(b - a) < 1e-16 * max(1.0, abs(m))) return m
            if (fa * fm < 0) b = m else { a = m; fa = fm }
        }
        return (a + b) / 2
    }
}
