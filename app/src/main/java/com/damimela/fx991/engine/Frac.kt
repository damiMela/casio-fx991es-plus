package com.damimela.fx991.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/** Exact rational number backed by BigInteger, always stored in lowest terms with a positive denominator. */
class Frac private constructor(val num: BigInteger, val den: BigInteger) : Comparable<Frac> {

    val isZero get() = num.signum() == 0
    val isInteger get() = den == BigInteger.ONE
    val signum get() = num.signum()

    operator fun plus(o: Frac) = of(num * o.den + o.num * den, den * o.den)
    operator fun minus(o: Frac) = of(num * o.den - o.num * den, den * o.den)
    operator fun times(o: Frac) = of(num * o.num, den * o.den)
    operator fun div(o: Frac): Frac {
        if (o.isZero) throw CalcError(ErrorKind.MATH)
        return of(num * o.den, den * o.num)
    }
    operator fun unaryMinus() = Frac(num.negate(), den)
    fun abs() = if (num.signum() < 0) -this else this
    fun inverse() = ONE / this

    fun pow(n: Int): Frac = when {
        n >= 0 -> Frac(num.pow(n), den.pow(n))
        isZero -> throw CalcError(ErrorKind.MATH)
        else -> of(den.pow(-n), num.pow(-n))
    }

    fun floor(): BigInteger {
        val qr = num.divideAndRemainder(den)
        return if (qr[1].signum() < 0) qr[0] - BigInteger.ONE else qr[0]
    }

    fun toDouble(): Double {
        if (num.bitLength() < 53 && den.bitLength() < 53) return num.toDouble() / den.toDouble()
        return BigDecimal(num).divide(BigDecimal(den), MathContext(30)).toDouble()
    }

    override fun compareTo(other: Frac) = (num * other.den).compareTo(other.num * den)
    override fun equals(other: Any?) = other is Frac && num == other.num && den == other.den
    override fun hashCode() = num.hashCode() * 31 + den.hashCode()
    override fun toString() = if (isInteger) num.toString() else "$num/$den"

    companion object {
        val ZERO = Frac(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Frac(BigInteger.ONE, BigInteger.ONE)

        fun of(n: BigInteger, d: BigInteger = BigInteger.ONE): Frac {
            if (d.signum() == 0) throw CalcError(ErrorKind.MATH)
            if (n.signum() == 0) return ZERO
            val g = n.gcd(d)
            var nn = n / g
            var dd = d / g
            if (dd.signum() < 0) { nn = nn.negate(); dd = dd.negate() }
            return Frac(nn, dd)
        }

        fun of(n: Long, d: Long = 1L) = of(BigInteger.valueOf(n), BigInteger.valueOf(d))

        /** Parses a plain decimal literal such as "12.5" exactly. */
        fun parseDecimal(s: String): Frac {
            val bd = BigDecimal(s)
            return if (bd.scale() > 0) of(bd.unscaledValue(), BigInteger.TEN.pow(bd.scale()))
            else of(bd.toBigIntegerExact() )
        }
    }
}
