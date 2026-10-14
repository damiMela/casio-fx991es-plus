package com.damimela.fx991.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import kotlin.math.*

enum class AngleUnit(val symbol: String) { DEG("D"), RAD("R"), GRA("G") }

fun round15(d: Double): Double {
    if (d == 0.0 || d.isNaN() || d.isInfinite()) return d
    return BigDecimal(d).round(MathContext(15)).toDouble()
}

fun r15(d: Double): Real = Real.of(round15(d))

/** Plain double complex helper for transcendental functions. */
data class C(val re: Double, val im: Double = 0.0) {
    operator fun plus(o: C) = C(re + o.re, im + o.im)
    operator fun minus(o: C) = C(re - o.re, im - o.im)
    operator fun times(o: C) = C(re * o.re - im * o.im, re * o.im + im * o.re)
    operator fun div(o: C): C {
        val d = o.re * o.re + o.im * o.im
        if (d == 0.0) throw CalcError(ErrorKind.MATH)
        return C((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d)
    }
    operator fun unaryMinus() = C(-re, -im)
    fun abs() = hypot(re, im)
    fun arg() = atan2(im, re)
    fun toValue(): Value = cplx(round15(re), round15(im))

    companion object {
        val I = C(0.0, 1.0)
        val ONE = C(1.0)
        fun exp(z: C) = C(Math.exp(z.re) * cos(z.im), Math.exp(z.re) * sin(z.im))
        fun ln(z: C): C {
            if (z.abs() == 0.0) throw CalcError(ErrorKind.MATH)
            return C(Math.log(z.abs()), z.arg())
        }
        fun sqrt(z: C): C {
            val r = z.abs()
            val re = Math.sqrt((r + z.re) / 2)
            val im = Math.sqrt(max(0.0, (r - z.re) / 2)) * (if (z.im < 0) -1 else 1)
            return C(re, im)
        }
        fun sin(z: C) = C(sin(z.re) * cosh(z.im), cos(z.re) * sinh(z.im))
        fun cos(z: C) = C(cos(z.re) * cosh(z.im), -sin(z.re) * sinh(z.im))
        fun tan(z: C) = sin(z) / cos(z)
        fun sinh(z: C) = C(sinh(z.re) * cos(z.im), cosh(z.re) * sin(z.im))
        fun cosh(z: C) = C(cosh(z.re) * cos(z.im), sinh(z.re) * sin(z.im))
        fun tanh(z: C) = sinh(z) / cosh(z)
        fun asin(z: C): C = -I * ln(I * z + sqrt(ONE - z * z))
        fun acos(z: C): C = C(PI / 2) - asin(z)
        fun atan(z: C): C = I / C(2.0) * (ln(ONE - I * z) - ln(ONE + I * z))
        fun asinh(z: C): C = ln(z + sqrt(z * z + ONE))
        fun acosh(z: C): C = ln(z + sqrt(z + ONE) * sqrt(z - ONE))
        fun atanh(z: C): C = C(0.5) * (ln(ONE + z) - ln(ONE - z))
        fun pow(a: C, b: C): C = if (a.re == 0.0 && a.im == 0.0) throw CalcError(ErrorKind.MATH) else exp(b * ln(a))
    }
}

fun Value.toC(): C = C(re().d, im().d)

object Angles {
    fun toRad(x: Double, u: AngleUnit): Double = when (u) {
        AngleUnit.RAD -> x
        AngleUnit.DEG -> (x % 360.0) * PI / 180.0
        AngleUnit.GRA -> (x % 400.0) * PI / 200.0
    }

    fun fromRad(x: Double, u: AngleUnit): Double = when (u) {
        AngleUnit.RAD -> x
        AngleUnit.DEG -> x * 180.0 / PI
        AngleUnit.GRA -> x * 200.0 / PI
    }

    /** Degrees -> given unit as an exact value when possible. */
    fun fromDegrees(deg: Real, u: AngleUnit): Real = when (u) {
        AngleUnit.DEG -> deg
        AngleUnit.RAD -> deg * Real.of(Ex.pi(Frac.of(1, 180)))
        AngleUnit.GRA -> deg * Real.of(Frac.of(10, 9))
    }

    fun toDegrees(x: Real, u: AngleUnit): Real = when (u) {
        AngleUnit.DEG -> x
        AngleUnit.RAD -> x * Real.of(Frac.of(180)) / Real.PI
        AngleUnit.GRA -> x * Real.of(Frac.of(9, 10))
    }

    /** Exact angle in degrees, if the angle is a rational number of degrees. */
    fun exactDegrees(x: Real, u: AngleUnit): Frac? {
        val ex = x.ex ?: return null
        return when (u) {
            AngleUnit.DEG -> x.q
            AngleUnit.GRA -> x.q?.let { it * Frac.of(9, 10) }
            AngleUnit.RAD -> if (ex.pi && ex.terms.keys.all { it == BigInteger.ONE }) ex.rational() * Frac.of(180) else null
        }
    }
}

object Trig {
    private fun sinRef(a: Int): Real? = when (a) {
        0 -> Real.ZERO
        30 -> Real.of(Frac.of(1, 2))
        45 -> Real.of(Ex.sqrt(Frac.of(1, 2)))
        60 -> Real.of(Ex.sqrt(Frac.of(3, 4)))
        90 -> Real.ONE
        else -> null
    }

    private fun sinExact(deg: Frac): Real? {
        if (!deg.isInteger) return null
        val a = deg.num.mod(BigInteger.valueOf(360)).toInt()
        val neg = a >= 180
        val a1 = a % 180
        val ref = if (a1 > 90) 180 - a1 else a1
        val v = sinRef(ref) ?: return null
        return if (neg) -v else v
    }

    private fun cosExact(deg: Frac) = sinExact(deg + Frac.of(90))

    fun sin(x: Real, u: AngleUnit): Real {
        Angles.exactDegrees(x, u)?.let { d -> sinExact(d)?.let { return it } }
        return r15(sin(Angles.toRad(x.d, u)))
    }

    fun cos(x: Real, u: AngleUnit): Real {
        Angles.exactDegrees(x, u)?.let { d -> cosExact(d)?.let { return it } }
        return r15(cos(Angles.toRad(x.d, u)))
    }

    fun tan(x: Real, u: AngleUnit): Real {
        val d = Angles.exactDegrees(x, u)
        if (d != null) {
            val s = sinExact(d)
            val c = cosExact(d)
            if (s != null && c != null) {
                if (c.isZero) throw CalcError(ErrorKind.MATH)
                return s / c
            }
        }
        val rad = Angles.toRad(x.d, u)
        val c = cos(rad)
        if (abs(c) < 1e-14) throw CalcError(ErrorKind.MATH)
        return r15(sin(rad) / c)
    }

    /** If [x] is exact and the result is an integer number of degrees matching [check], returns it exactly in unit [u]. */
    private fun inverse(x: Real, u: AngleUnit, rad: Double, check: (Int) -> Real?): Real {
        val deg = rad * 180.0 / PI
        val n = deg.roundToInt()
        if (x.isExact && abs(deg - n) < 1e-9) {
            val back = check(n)
            if (back != null && abs(back.d - x.d) < 1e-12) return Angles.fromDegrees(Real.of(n), u)
        }
        return r15(Angles.fromRad(rad, u))
    }

    fun asin(x: Real, u: AngleUnit): Real {
        if (abs(x.d) > 1) throw CalcError(ErrorKind.MATH)
        return inverse(x, u, kotlin.math.asin(x.d)) { n -> sinExact(Frac.of(n.toLong())) }
    }

    fun acos(x: Real, u: AngleUnit): Real {
        if (abs(x.d) > 1) throw CalcError(ErrorKind.MATH)
        return inverse(x, u, kotlin.math.acos(x.d)) { n -> cosExact(Frac.of(n.toLong())) }
    }

    fun atan(x: Real, u: AngleUnit): Real =
        inverse(x, u, kotlin.math.atan(x.d)) { n ->
            val s = sinExact(Frac.of(n.toLong())); val c = cosExact(Frac.of(n.toLong()))
            if (s != null && c != null && !c.isZero) s / c else null
        }
}

object Num {
    fun intValue(v: Value, max: Long = Long.MAX_VALUE): Long {
        val r = v as? Real ?: throw CalcError(ErrorKind.ARGUMENT)
        if (!r.isInteger || abs(r.d) > max.toDouble()) throw CalcError(ErrorKind.ARGUMENT)
        return r.q?.num?.toLong() ?: r.d.toLong()
    }

    fun factorial(v: Value): Real {
        val r = v as? Real ?: throw CalcError(ErrorKind.MATH)
        if (!r.isInteger || r.d < 0 || r.d > 69) throw CalcError(ErrorKind.MATH)
        var f = BigInteger.ONE
        for (i in 2..r.d.toInt()) f *= BigInteger.valueOf(i.toLong())
        return Real.of(f)
    }

    fun nPr(n: Value, r: Value): Real {
        val a = intValue(n); val b = intValue(r)
        if (a < 0 || b < 0 || b > a || a > 9999999999L) throw CalcError(ErrorKind.MATH)
        var p = BigInteger.ONE
        for (k in 0 until b) {
            p *= BigInteger.valueOf(a - k)
            if (p.bitLength() > 333) throw CalcError(ErrorKind.MATH)
        }
        return Real.of(p)
    }

    fun nCr(n: Value, r: Value): Real {
        val a = intValue(n); var b = intValue(r)
        if (a < 0 || b < 0 || b > a || a > 9999999999L) throw CalcError(ErrorKind.MATH)
        if (b > a - b) b = a - b
        var p = BigInteger.ONE
        for (k in 1..b) {
            p = p * BigInteger.valueOf(a - b + k) / BigInteger.valueOf(k)
            if (p.bitLength() > 333) throw CalcError(ErrorKind.MATH)
        }
        return Real.of(p)
    }

    fun gcd(a: Value, b: Value) = Real.of(BigInteger.valueOf(intValue(a)).gcd(BigInteger.valueOf(intValue(b))))

    fun lcm(a: Value, b: Value): Real {
        val x = BigInteger.valueOf(abs(intValue(a))); val y = BigInteger.valueOf(abs(intValue(b)))
        if (x.signum() == 0 || y.signum() == 0) return Real.ZERO
        return Real.of(x / x.gcd(y) * y)
    }

    /** Exact integer n-th root of a non-negative integer, or null. */
    private fun intRoot(v: BigInteger, n: Int): BigInteger? {
        if (v.signum() == 0 || v == BigInteger.ONE) return v
        if (v.bitLength() > 2000) return null
        val r = BigInteger.valueOf(Math.round(Math.pow(v.toDouble(), 1.0 / n)))
        for (d in -1L..1L) {
            val c = r + BigInteger.valueOf(d)
            if (c.signum() >= 0 && c.pow(n) == v) return c
        }
        return null
    }

    fun rootFrac(f: Frac, n: Int): Frac? {
        if (f.signum < 0) return null
        val a = intRoot(f.num, n) ?: return null
        val b = intRoot(f.den, n) ?: return null
        return Frac.of(a, b)
    }

    /** log_base(x) as an exact integer when x == base^k. */
    fun exactLog(base: Frac, x: Frac): Int? {
        if (base <= Frac.ONE || x.signum <= 0) return null
        val k = Math.round(Math.log(x.toDouble()) / Math.log(base.toDouble())).toInt()
        if (abs(k) > 400) return null
        return if (base.pow(k) == x) k else null
    }

    fun pow(a: Value, b: Value, complexMode: Boolean): Value {
        if (a is MatV) {
            val n = (b as? Real)?.takeIf { it.isInteger } ?: throw CalcError(ErrorKind.MATH)
            MatrixOps.square(a)
            return Ops.powInt(a, n.d.toInt())
        }
        if (a !is Real && a !is Cplx) throw CalcError(ErrorKind.MATH)
        if (b !is Real && b !is Cplx) throw CalcError(ErrorKind.MATH)
        if (a is Real && b is Real) return powReal(a, b, complexMode)
        val bq = (b as? Real)?.q
        if (bq != null && bq.isInteger && bq.num.bitLength() < 8) return Ops.powInt(a, bq.num.toInt())
        if (!complexMode) throw CalcError(ErrorKind.MATH)
        return C.pow(a.toC(), b.toC()).toValue()
    }

    private fun powReal(a: Real, b: Real, complexMode: Boolean): Value {
        val bq = b.q
        val aq = a.q
        if (bq != null && bq.isInteger && bq.num.bitLength() < 16) {
            val n = bq.num.toInt()
            if (a.d == 0.0) { if (n <= 0) throw CalcError(ErrorKind.MATH); return Real.ZERO }
            if (n == 0) return Real.ONE
            val approx = a.d.pow(n)
            if (approx.isNaN() || approx.isInfinite() || abs(approx) >= 1e100) throw CalcError(ErrorKind.MATH)
            if (a.isExact && abs(n) <= 64) return Ops.powInt(a, n)
            return r15(approx)
        }
        if (a.d == 0.0) { if (b.d <= 0) throw CalcError(ErrorKind.MATH); return Real.ZERO }
        if (a.d < 0) {
            if (bq != null && bq.den.testBit(0) && bq.den.bitLength() < 16) {
                val v = powReal(a.abs(), b, complexMode) as Real
                return if (bq.num.testBit(0)) -v else v
            }
            if (!complexMode) throw CalcError(ErrorKind.MATH)
            return C.pow(C(a.d), C(b.d)).toValue()
        }
        if (aq != null && bq != null && bq.den.bitLength() < 5) {
            val den = bq.den.toInt()
            val root = rootFrac(aq, den)
            if (root != null && abs(bq.num.toInt()) <= 64) return Real.of(root.pow(bq.num.toInt()))
            if (den == 2 && abs(bq.num.toInt()) <= 16) {
                val s = Ops.sqrtReal(a)
                return Ops.powInt(s, bq.num.toInt())
            }
        }
        return r15(a.d.pow(b.d))
    }

    fun sqrt(v: Value, complexMode: Boolean): Value = when (v) {
        is Real -> if (v.d >= 0) Ops.sqrtReal(v) else if (complexMode) cplx(Real.ZERO, Ops.sqrtReal(-v)) else throw CalcError(ErrorKind.MATH)
        is Cplx -> if (complexMode) C.sqrt(v.toC()).toValue() else throw CalcError(ErrorKind.MATH)
        else -> throw CalcError(ErrorKind.MATH)
    }

    fun cbrt(v: Value): Value {
        val r = v as? Real ?: throw CalcError(ErrorKind.MATH)
        val q = r.q
        if (q != null) {
            val s = rootFrac(q.abs(), 3)
            if (s != null) return Real.of(if (q.signum < 0) -s else s)
        }
        return r15(Math.cbrt(r.d))
    }

    fun log10(v: Value, complexMode: Boolean): Value {
        if (v is Real) {
            val q = v.q
            if (q != null) exactLog(Frac.of(10), q)?.let { return Real.of(it) }
            if (v.d > 0) return r15(Math.log10(v.d))
            if (v.d == 0.0 || !complexMode) throw CalcError(ErrorKind.MATH)
        }
        if (!complexMode) throw CalcError(ErrorKind.MATH)
        val l = C.ln(v.toC())
        return C(l.re / Math.log(10.0), l.im / Math.log(10.0)).toValue()
    }

    fun ln(v: Value, complexMode: Boolean): Value {
        if (v is Real) {
            if (v.q == Frac.ONE) return Real.ZERO
            if (v.d > 0) return r15(Math.log(v.d))
            if (v.d == 0.0 || !complexMode) throw CalcError(ErrorKind.MATH)
        }
        if (!complexMode) throw CalcError(ErrorKind.MATH)
        return C.ln(v.toC()).toValue()
    }

    fun logBase(base: Value, x: Value, complexMode: Boolean): Value {
        if (base is Real && x is Real) {
            val bq = base.q; val xq = x.q
            if (bq != null && xq != null) exactLog(bq, xq)?.let { return Real.of(it) }
            if (base.d <= 0 || base.d == 1.0 || x.d <= 0) {
                if (!complexMode) throw CalcError(ErrorKind.MATH)
            } else return r15(Math.log(x.d) / Math.log(base.d))
        }
        if (!complexMode) throw CalcError(ErrorKind.MATH)
        return (C.ln(x.toC()) / C.ln(base.toC())).toValue()
    }

    fun exp(v: Value): Value = when (v) {
        is Real -> if (v.isZero) Real.ONE else r15(Math.exp(v.d))
        is Cplx -> C.exp(v.toC()).toValue()
        else -> throw CalcError(ErrorKind.MATH)
    }

    /** Casio "Int(": integer part (towards zero). */
    fun intPart(v: Value): Value {
        val r = v as? Real ?: throw CalcError(ErrorKind.MATH)
        val q = r.q
        if (q != null) {
            val f = q.floor()
            return Real.of(if (q.signum < 0 && !q.isInteger) f + BigInteger.ONE else f)
        }
        return Real.of(if (r.d < 0) ceil(r.d) else floor(r.d))
    }

    /** Casio "Intg(": greatest integer not exceeding x. */
    fun floorPart(v: Value): Value {
        val r = v as? Real ?: throw CalcError(ErrorKind.MATH)
        return r.q?.let { Real.of(it.floor()) } ?: Real.of(floor(r.d))
    }
}
