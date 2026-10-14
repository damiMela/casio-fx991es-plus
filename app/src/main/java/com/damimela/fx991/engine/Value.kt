package com.damimela.fx991.engine

import java.math.BigInteger
import kotlin.math.abs

sealed interface Value

/** Real number: double value plus an optional exact closed form ([ex]) used for fraction / radical display. */
class Real private constructor(val d: Double, val ex: Ex?) : Value {

    val q: Frac? get() = if (ex != null && ex.isRational) ex.rational() else null
    val isZero get() = d == 0.0
    val isExact get() = ex != null
    val isInteger: Boolean get() = q?.isInteger ?: (d == Math.floor(d) && abs(d) < 1e15)

    operator fun plus(o: Real): Real = combine(d + o.d, ex, o.ex) { a, b -> a + b }
    operator fun minus(o: Real): Real = combine(d - o.d, ex, o.ex) { a, b -> a - b }
    operator fun times(o: Real): Real = combine(d * o.d, ex, o.ex) { a, b -> a * b }
    operator fun div(o: Real): Real {
        if (o.d == 0.0) throw CalcError(ErrorKind.MATH)
        return combine(d / o.d, ex, o.ex) { a, b -> a / b }
    }
    operator fun unaryMinus(): Real = if (ex != null) Real(-d, -ex) else of(-d)
    fun abs(): Real = if (d < 0) -this else this

    override fun toString() = if (ex != null) "$ex" else "$d"

    companion object {
        val ZERO = Real(0.0, Ex.ZERO)
        val ONE = Real(1.0, Ex.ONE)
        val PI = Real(Math.PI, Ex.pi())

        private inline fun combine(raw: Double, a: Ex?, b: Ex?, op: (Ex, Ex) -> Ex?): Real {
            if (a != null && b != null) {
                val r = op(a, b)
                if (r != null) return of(r)
            }
            return of(raw)
        }

        fun of(d: Double): Real {
            if (d.isNaN() || d.isInfinite() || abs(d) >= 1e100) throw CalcError(ErrorKind.MATH)
            if (abs(d) < 1e-99) return Real(0.0, null)
            return Real(if (d == 0.0) 0.0 else d, null)
        }

        fun of(e: Ex): Real {
            if (e.bits() > 400) return of(e.toDouble())
            val d = e.toDouble()
            if (d.isNaN() || d.isInfinite() || abs(d) >= 1e100) throw CalcError(ErrorKind.MATH)
            return Real(d, e)
        }

        fun of(f: Frac): Real = of(Ex.rational(f))
        fun of(n: Long): Real = of(Frac.of(n))
        fun of(n: Int): Real = of(Frac.of(n.toLong()))
        fun of(n: BigInteger): Real = of(Frac.of(n))
    }
}

/** Complex number with real and imaginary parts that may each be exact. Never created with zero imaginary part. */
class Cplx(val re: Real, val im: Real) : Value {
    override fun toString() = "($re, $im)"
}

class MatV(val rows: Int, val cols: Int, val data: List<Value>) : Value {
    operator fun get(r: Int, c: Int): Value = data[r * cols + c]
    fun row(r: Int) = (0 until cols).map { this[r, it] }

    companion object {
        fun of(rows: List<List<Value>>): MatV {
            val r = rows.size
            val c = rows.firstOrNull()?.size ?: 0
            return MatV(r, c, rows.flatten())
        }
        fun build(rows: Int, cols: Int, f: (Int, Int) -> Value) =
            MatV(rows, cols, (0 until rows).flatMap { r -> (0 until cols).map { c -> f(r, c) } })
    }
}

class VecV(val items: List<Value>) : Value {
    val size get() = items.size
}

fun cplx(re: Real, im: Real): Value = if (im.isZero && (im.isExact || im.d == 0.0)) re else Cplx(re, im)
fun cplx(re: Double, im: Double): Value = cplx(Real.of(re), Real.of(im))

fun Value.re(): Real = when (this) { is Real -> this; is Cplx -> re; else -> throw CalcError(ErrorKind.MATH) }
fun Value.im(): Real = when (this) { is Real -> Real.ZERO; is Cplx -> im; else -> throw CalcError(ErrorKind.MATH) }
fun Value.isScalar() = this is Real || this is Cplx
fun Value.isZeroValue(): Boolean = when (this) {
    is Real -> d == 0.0
    is Cplx -> re.isZero && im.isZero
    else -> false
}

/** Arithmetic on all value kinds (reals, complex, matrices, vectors). */
object Ops {
    fun neg(a: Value): Value = when (a) {
        is Real -> -a
        is Cplx -> cplx(-a.re, -a.im)
        is MatV -> MatV(a.rows, a.cols, a.data.map { neg(it) })
        is VecV -> VecV(a.items.map { neg(it) })
    }

    fun add(a: Value, b: Value): Value = when {
        a is Real && b is Real -> a + b
        a.isScalar() && b.isScalar() -> cplx(a.re() + b.re(), a.im() + b.im())
        a is MatV && b is MatV -> {
            if (a.rows != b.rows || a.cols != b.cols) throw CalcError(ErrorKind.DIMENSION)
            MatV(a.rows, a.cols, a.data.zip(b.data) { x, y -> add(x, y) })
        }
        a is VecV && b is VecV -> {
            if (a.size != b.size) throw CalcError(ErrorKind.DIMENSION)
            VecV(a.items.zip(b.items) { x, y -> add(x, y) })
        }
        else -> throw CalcError(ErrorKind.MATH)
    }

    fun sub(a: Value, b: Value): Value = add(a, neg(b))

    fun mul(a: Value, b: Value): Value = when {
        a is Real && b is Real -> a * b
        a.isScalar() && b.isScalar() -> {
            val ar = a.re(); val ai = a.im(); val br = b.re(); val bi = b.im()
            cplx(ar * br - ai * bi, ar * bi + ai * br)
        }
        a.isScalar() && b is MatV -> MatV(b.rows, b.cols, b.data.map { mul(a, it) })
        a is MatV && b.isScalar() -> MatV(a.rows, a.cols, a.data.map { mul(it, b) })
        a.isScalar() && b is VecV -> VecV(b.items.map { mul(a, it) })
        a is VecV && b.isScalar() -> VecV(a.items.map { mul(it, b) })
        a is MatV && b is MatV -> {
            if (a.cols != b.rows) throw CalcError(ErrorKind.DIMENSION)
            MatV.build(a.rows, b.cols) { r, c ->
                var s: Value = Real.ZERO
                for (k in 0 until a.cols) s = add(s, mul(a[r, k], b[k, c]))
                s
            }
        }
        else -> throw CalcError(ErrorKind.MATH)
    }

    fun div(a: Value, b: Value): Value = when {
        a is Real && b is Real -> a / b
        a.isScalar() && b.isScalar() -> {
            val c = b.re(); val d = b.im()
            val den = c * c + d * d
            if (den.isZero) throw CalcError(ErrorKind.MATH)
            val n = mul(a, cplx(c, -d))
            cplx(n.re() / den, n.im() / den)
        }
        (a is MatV || a is VecV) && b.isScalar() -> mul(a, div(Real.ONE, b))
        else -> throw CalcError(ErrorKind.MATH)
    }

    fun abs(a: Value): Real = when (a) {
        is Real -> a.abs()
        is Cplx -> sqrtReal(a.re * a.re + a.im * a.im)
        is VecV -> sqrtReal(a.items.fold(Real.ZERO) { s, x -> s + sq(x) })
        is MatV -> throw CalcError(ErrorKind.MATH)
    }

    private fun sq(x: Value): Real = when (x) { is Real -> x * x; else -> abs(x).let { it * it } }

    /** Square root of a non-negative real, exact when possible. */
    fun sqrtReal(x: Real): Real {
        if (x.d < 0) throw CalcError(ErrorKind.MATH)
        val q = x.q
        if (q != null) return Real.of(Ex.sqrt(q))
        return Real.of(Math.sqrt(x.d))
    }

    fun isNegative(x: Real) = x.d < 0

    fun conj(a: Value): Value = when (a) {
        is Real -> a
        is Cplx -> Cplx(a.re, -a.im)
        else -> throw CalcError(ErrorKind.MATH)
    }

    /** Integer power by repeated squaring (exact where the operands are exact). */
    fun powInt(a: Value, n: Int): Value {
        if (n == 0) {
            if (a.isScalar() && a.isZeroValue()) throw CalcError(ErrorKind.MATH)
            return if (a is MatV) identity(a.rows) else Real.ONE
        }
        if (n < 0) {
            val p = powInt(a, -n)
            return if (p is MatV) inverseMat(p) else div(Real.ONE, p)
        }
        var result: Value? = null
        var base = a
        var e = n
        while (e > 0) {
            if (e and 1 == 1) result = if (result == null) base else mul(result, base)
            e = e shr 1
            if (e > 0) base = mul(base, base)
        }
        return result!!
    }

    fun identity(n: Int) = MatV.build(n, n) { r, c -> if (r == c) Real.ONE else Real.ZERO }

    fun inverseMat(m: MatV): MatV = MatrixOps.inverse(m)
}
