package com.damimela.fx991.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

enum class NumFormat { NORM1, NORM2, FIX, SCI }

data class DisplaySettings(
    val format: NumFormat = NumFormat.NORM1,
    /** Digits for Fix (0-9 decimals) and Sci (1-10 significant digits; 0 means 10). */
    val digits: Int = 0,
    val mathIO: Boolean = true,
    /** true: mixed fractions (a b/c), false: improper fractions (d/c). */
    val mixedFractions: Boolean = false,
    val decimalComma: Boolean = false,
    val engSymbols: Boolean = false,
    val polarComplex: Boolean = false
)

/** Natural-display layout tree. */
sealed class MNode
class MText(val s: String) : MNode()
class MRow(val items: List<MNode>) : MNode()
class MFrac(val num: MNode, val den: MNode) : MNode()
class MSqrt(val inner: MNode, val index: MNode? = null) : MNode()
class MSup(val base: MNode, val exp: MNode, val down: Boolean = false) : MNode()
class MMat(val cells: List<List<MNode>>) : MNode()
class MMixed(val whole: MNode, val num: MNode, val den: MNode) : MNode()
/** Input-line cursor marker (insert = vertical bar, overwrite = block). */
class MCursor(val insert: Boolean = true) : MNode()
/** Empty template slot shown as a small box. */
object MPlace : MNode()

fun mrow(vararg n: MNode): MNode = MRow(n.toList())

object Formatter {
    private val SUPERSCRIPT = mapOf('0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶',
        '7' to '⁷', '8' to '⁸', '9' to '⁹', '-' to '⁻', '.' to '·')
    private val ENG_SYMBOLS = mapOf(-18 to "a", -15 to "f", -12 to "p", -9 to "n", -6 to "μ", -3 to "m",
        3 to "k", 6 to "M", 9 to "G", 12 to "T", 15 to "P", 18 to "E")

    /** Flattens a layout tree into single-line text (used for LineIO mode and tests). */
    fun plain(n: MNode): String = when (n) {
        is MText -> n.s
        is MRow -> n.items.joinToString("") { plain(it) }
        is MFrac -> {
            val nm = plain(n.num)
            (if (nm.indexOfAny(charArrayOf('+', '-'), 1) > 0) "($nm)" else nm) + "⌟" + plain(n.den)
        }
        is MSqrt -> if (n.index == null) "√(${plain(n.inner)})" else "${plain(n.index)}ˣ√(${plain(n.inner)})"
        is MSup -> plain(n.base) + plain(n.exp).map { SUPERSCRIPT[it] ?: it }.joinToString("")
        is MMixed -> "${plain(n.whole)}⌟${plain(n.num)}⌟${plain(n.den)}"
        is MCursor -> ""
        is MPlace -> "□"
        is MMat -> "[" + n.cells.joinToString(";") { r -> r.joinToString(" ") { plain(it) } } + "]"
    }

    /**
     * Formats any value. [exact] selects fractions/radicals (when available) instead of decimals;
     * [eng] shifts the engineering exponent (0 = plain ENG notation, null = not in ENG mode);
     * [polar] overrides the complex display style.
     */
    fun format(v: Value, s: DisplaySettings, exact: Boolean = s.mathIO, eng: Int? = null, polar: Boolean? = null,
               angle: AngleUnit = AngleUnit.DEG): MNode = when (v) {
        is Real -> real(v, s, exact, eng)
        is Cplx -> complex(v, s, exact, eng, polar ?: s.polarComplex, angle)
        is MatV -> MMat((0 until v.rows).map { r -> (0 until v.cols).map { c -> format(v[r, c], s, exact, eng, polar, angle) } })
        is VecV -> MMat(listOf(v.items.map { format(it, s, exact, eng, polar, angle) }))
    }

    fun text(v: Value, s: DisplaySettings, exact: Boolean = s.mathIO): String = plain(format(v, s, exact))

    fun real(r: Real, s: DisplaySettings, exact: Boolean, eng: Int? = null): MNode {
        if (exact && eng == null && r.ex != null && (s.format == NumFormat.NORM1 || s.format == NumFormat.NORM2)) exactNode(r.ex, s)?.let { return it }
        return decimal(r.d, s, eng)
    }

    private fun complex(z: Cplx, s: DisplaySettings, exact: Boolean, eng: Int?, polar: Boolean, angle: AngleUnit): MNode {
        if (polar) {
            val mag = Ops.abs(z)
            val arg = Angles.fromRad(Math.atan2(z.im.d, z.re.d), angle)
            return mrow(real(mag, s, exact, eng), MText("∠"), decimal(round15(arg), s, eng))
        }
        val items = ArrayList<MNode>()
        val reZero = z.re.isZero
        if (!reZero) items += real(z.re, s, exact, eng)
        val neg = z.im.d < 0
        val mag = z.im.abs()
        if (reZero) { if (neg) items += MText("-") } else items += MText(if (neg) " - " else " + ")
        if (mag.d != 1.0) items += real(mag, s, exact, eng)
        items += MText("i")
        return MRow(items)
    }

    // ---- exact -----------------------------------------------------------------------------
    private fun fits(b: BigInteger) = b.abs().toString().length <= 10

    private fun text(i: BigInteger) = MText(i.toString())

    fun exactNode(ex: Ex, s: DisplaySettings): MNode? {
        if (ex.isZero) return MText("0")
        if (ex.isRational) return fraction(ex.rational(), s)
        var lcm = BigInteger.ONE
        for (c in ex.terms.values) lcm = lcm / lcm.gcd(c.den) * c.den
        val nums = ex.terms.mapValues { (it.value * Frac.of(lcm)).num }
        var g = lcm
        for (n in nums.values) g = g.gcd(n)
        val den = lcm / g
        val ns = nums.mapValues { it.value / g }
        if (ns.values.any { !fits(it) } || !fits(den)) return null
        val items = ArrayList<MNode>()
        var first = true
        val single = ns.size == 1
        var outerNeg = false
        for ((k, n) in ns) {
            val neg = n.signum() < 0
            val a = n.abs()
            if (single && den != BigInteger.ONE) { outerNeg = neg } else if (first) { if (neg) items += MText("-") } else items += MText(if (neg) "-" else "+")
            val body = ArrayList<MNode>()
            if (k == BigInteger.ONE) { if (a != BigInteger.ONE || !ex.pi || !single) body += text(a) }
            else { if (a != BigInteger.ONE) body += text(a); body += MSqrt(text(k)) }
            items += body
            first = false
        }
        var numer: MNode = MRow(items)
        if (ex.pi) numer = if (single) MRow(listOf(numer, MText("π"))) else MRow(listOf(MText("("), numer, MText(")π")))
        val res = if (den == BigInteger.ONE) numer else MFrac(numer, text(den))
        return if (outerNeg) MRow(listOf(MText("-"), res)) else res
    }

    private fun fraction(q: Frac, s: DisplaySettings): MNode? {
        if (q.isInteger) return if (fits(q.num)) text(q.num) else null
        if (!fits(q.num) || !fits(q.den)) return null
        val neg = q.signum < 0
        val a = q.abs()
        val prefix = if (neg) listOf<MNode>(MText("-")) else emptyList()
        if (s.mixedFractions && a.num > a.den) {
            val whole = a.num / a.den
            val rem = a.num % a.den
            return MRow(prefix + listOf(MMixed(text(whole), text(rem), text(a.den))))
        }
        return MRow(prefix + listOf(MFrac(text(a.num), text(a.den))))
    }

    // ---- decimal ---------------------------------------------------------------------------
    private fun commaFix(str: String, s: DisplaySettings) = if (s.decimalComma) str.replace('.', ',') else str

    private fun sci(mant: String, exp: Int, s: DisplaySettings, neg: Boolean): MNode =
        mrow(MText((if (neg) "-" else "") + commaFix(mant, s)), MSup(MText("×10"), MText(exp.toString())))

    fun decimal(x: Double, s: DisplaySettings, eng: Int? = null): MNode {
        val neg = x < 0
        val ax = Math.abs(x)
        val sign = if (neg) "-" else ""
        if (ax == 0.0) {
            return when {
                s.format == NumFormat.FIX && s.digits > 0 -> MText("0." + "0".repeat(s.digits)).let { MText(commaFix(plain(it), s)) }
                s.format == NumFormat.SCI -> {
                    val n = if (s.digits == 0) 10 else s.digits
                    sci("0" + if (n > 1) "." + "0".repeat(n - 1) else "", 0, s, false)
                }
                else -> MText("0")
            }
        }
        val rounded = BigDecimal(ax).round(MathContext(10, RoundingMode.HALF_UP))
        val exp10 = rounded.precision() - rounded.scale() - 1
        if (eng != null) return engineering(rounded, exp10, s, neg, eng)
        when (s.format) {
            NumFormat.NORM1, NumFormat.NORM2 -> {
                val lowLimit = if (s.format == NumFormat.NORM1) -2 else -9
                if (exp10 in lowLimit..9) return MText(sign + commaFix(rounded.stripTrailingZeros().toPlainString(), s))
                return sciFromDigits(rounded, exp10, s, neg, stripZeros = true, count = 10)
            }
            NumFormat.FIX -> {
                if (exp10 >= 10) return sciFromDigits(rounded, exp10, s, neg, true, 10)
                val f = BigDecimal(ax).setScale(s.digits, RoundingMode.HALF_UP)
                val str = f.toPlainString()
                val isZero = f.signum() == 0
                return MText((if (neg && !isZero) "-" else "") + commaFix(str, s))
            }
            NumFormat.SCI -> {
                val n = if (s.digits == 0) 10 else s.digits
                val r = BigDecimal(ax).round(MathContext(n, RoundingMode.HALF_UP))
                val e = r.precision() - r.scale() - 1
                return sciFromDigits(r, e, s, neg, stripZeros = false, count = n)
            }
        }
    }

    private fun sciFromDigits(r: BigDecimal, exp: Int, s: DisplaySettings, neg: Boolean, stripZeros: Boolean, count: Int): MNode {
        var digits = r.unscaledValue().abs().toString()
        if (stripZeros) digits = digits.trimEnd('0').ifEmpty { "0" } else digits = digits.padEnd(count, '0').take(count)
        val mant = if (digits.length > 1) digits[0] + "." + digits.substring(1) else digits
        return sci(mant, exp, s, neg)
    }

    private fun engineering(rounded: BigDecimal, exp10: Int, s: DisplaySettings, neg: Boolean, shift: Int): MNode {
        val e3 = Math.floorDiv(exp10, 3) * 3 + 3 * shift
        val m = rounded.movePointLeft(e3).stripTrailingZeros().toPlainString()
        val symbol = if (s.engSymbols) ENG_SYMBOLS[e3] else null
        if (symbol != null) return MText((if (neg) "-" else "") + commaFix(m, s) + symbol)
        if (e3 == 0) return MText((if (neg) "-" else "") + commaFix(m, s))
        return sci(m, e3, s, neg)
    }

    /** ▶DMS output, e.g. 12°34′56″. */
    fun dms(x: Double): String {
        val neg = x < 0
        val a = Math.abs(x)
        var d = Math.floor(a + 1e-12).toLong()
        val minF = (a - d) * 60
        var m = Math.floor(minF + 1e-10).toLong()
        var sec = BigDecimal((minF - m) * 60).setScale(2, RoundingMode.HALF_UP)
        if (sec >= BigDecimal(60)) { sec = BigDecimal.ZERO; m++ }
        if (m >= 60) { m -= 60; d++ }
        val secStr = sec.stripTrailingZeros().toPlainString()
        return (if (neg) "-" else "") + "$d°$m′$secStr″"
    }
}
