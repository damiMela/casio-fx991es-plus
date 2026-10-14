package com.damimela.fx991.engine

import java.math.BigInteger

/**
 * Exact "closed form" number: sum of q_k * sqrt(k) (k square-free), optionally multiplied by pi.
 * Covers the values the Casio shows exactly in natural display: fractions, 2√2, (-1+√5)/2, 2π/3 ...
 */
class Ex private constructor(val terms: Map<BigInteger, Frac>, val pi: Boolean) {

    val isZero get() = terms.isEmpty()
    val isRational get() = !pi && terms.keys.all { it == BigInteger.ONE }

    /** The rational part (coefficient of sqrt(1)). */
    fun rational(): Frac = terms[BigInteger.ONE] ?: Frac.ZERO

    fun toDouble(): Double {
        var s = 0.0
        for ((k, c) in terms) s += c.toDouble() * (if (k == BigInteger.ONE) 1.0 else Math.sqrt(k.toDouble()))
        return if (pi) s * Math.PI else s
    }

    fun bits(): Int = terms.values.maxOfOrNull { maxOf(it.num.bitLength(), it.den.bitLength()) } ?: 0

    operator fun unaryMinus(): Ex = Ex(terms.mapValues { -it.value }, pi)

    operator fun plus(o: Ex): Ex? {
        if (isZero) return o
        if (o.isZero) return this
        if (pi != o.pi) return null
        val m = HashMap(terms)
        for ((k, c) in o.terms) m[k] = (m[k] ?: Frac.ZERO) + c
        return of(m, pi)
    }

    operator fun minus(o: Ex): Ex? = this + (-o)

    operator fun times(o: Ex): Ex? {
        if (pi && o.pi) return null
        val m = HashMap<BigInteger, Frac>()
        for ((k1, c1) in terms) for ((k2, c2) in o.terms) {
            val (s, r) = squareFreeSplit(k1 * k2)
            m[r] = (m[r] ?: Frac.ZERO) + c1 * c2 * Frac.of(s)
        }
        val res = of(m, pi || o.pi)
        return if (res.terms.size > 4) null else res
    }

    operator fun div(o: Ex): Ex? {
        if (o.isZero) throw CalcError(ErrorKind.MATH)
        if (o.pi && !pi && !isZero) return null
        val resultPi = pi && !o.pi
        val a = Ex(terms, false)
        val b = Ex(o.terms, false)
        val inv: Ex = when (b.terms.size) {
            1 -> {
                val (k, c) = b.terms.entries.first()
                of(mapOf(k to (Frac.ONE / (c * Frac.of(k)))), false)
            }
            2 -> {
                val (e1, e2) = b.terms.entries.toList()
                val conj = of(mapOf(e1.key to e1.value, e2.key to -e2.value), false)
                val denom = e1.value * e1.value * Frac.of(e1.key) - e2.value * e2.value * Frac.of(e2.key)
                if (denom.isZero) return null
                val c = conj.times(of(mapOf(BigInteger.ONE to (Frac.ONE / denom)), false)) ?: return null
                c
            }
            else -> return null
        }
        val r = a.times(inv) ?: return null
        return of(r.terms, resultPi)
    }

    override fun equals(other: Any?) = other is Ex && pi == other.pi && terms == other.terms
    override fun hashCode() = terms.hashCode() * 2 + if (pi) 1 else 0
    override fun toString() = terms.entries.joinToString("+") { "${it.value}√${it.key}" } + if (pi) "π" else ""

    companion object {
        val ZERO = Ex(emptyMap(), false)
        val ONE = rational(Frac.ONE)

        fun of(terms: Map<BigInteger, Frac>, pi: Boolean): Ex {
            val clean = terms.filterValues { !it.isZero }
            return if (clean.isEmpty()) ZERO else Ex(clean.toSortedMap(), pi)
        }

        fun rational(f: Frac): Ex = if (f.isZero) ZERO else Ex(mapOf(BigInteger.ONE to f), false)

        fun pi(f: Frac = Frac.ONE): Ex = if (f.isZero) ZERO else Ex(mapOf(BigInteger.ONE to f), true)

        /** sqrt of a non-negative rational as an exact value. */
        fun sqrt(f: Frac): Ex {
            if (f.signum < 0) throw CalcError(ErrorKind.MATH)
            if (f.isZero) return ZERO
            val (s, r) = squareFreeSplit(f.num * f.den)
            return Ex(mapOf(r to Frac.of(s, f.den)), false)
        }

        /** Returns (s, r) with n = s^2 * r and r square-free (best effort for very large n). */
        fun squareFreeSplit(n: BigInteger): Pair<BigInteger, BigInteger> {
            if (n.bitLength() > 100) return BigInteger.ONE to n
            var rem = n
            var s = BigInteger.ONE
            var r = BigInteger.ONE
            var p = 2L
            while (p <= 100_000L && BigInteger.valueOf(p * p) <= rem) {
                val bp = BigInteger.valueOf(p)
                var e = 0
                while ((rem % bp).signum() == 0) { rem /= bp; e++ }
                if (e > 0) {
                    s *= bp.pow(e / 2)
                    if (e % 2 == 1) r *= bp
                }
                p += if (p == 2L) 1 else 2
            }
            if (rem > BigInteger.ONE) {
                val sq = rem.sqrt()
                if (sq * sq == rem) s *= sq else r *= rem
            }
            return s to r
        }
    }
}
