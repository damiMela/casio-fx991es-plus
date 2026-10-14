package com.damimela.fx991.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

enum class StatType(val label: String, val twoVar: Boolean) {
    ONE_VAR("1-VAR", false),
    LINEAR("A+BX", true),
    QUAD("_+CX²", true),
    LOG("ln X", true),
    EXP("e^X", true),
    AB("A·B^X", true),
    POWER("A·X^B", true),
    INV("1/X", true)
}

class StatResult(val type: StatType, val values: LinkedHashMap<String, Double>, private val coef: DoubleArray) {
    operator fun get(k: String) = values[k] ?: throw CalcError(ErrorKind.MATH)

    /** ŷ for a given x. */
    fun predictY(x: Double): Double {
        val a = coef[0]; val b = coef[1]
        return when (type) {
            StatType.LINEAR -> a + b * x
            StatType.QUAD -> a + b * x + coef[2] * x * x
            StatType.LOG -> a + b * ln(x)
            StatType.EXP -> a * exp(b * x)
            StatType.AB -> a * Math.pow(b, x)
            StatType.POWER -> a * Math.pow(x, b)
            StatType.INV -> a + b / x
            StatType.ONE_VAR -> throw CalcError(ErrorKind.MATH)
        }
    }

    /** x̂ for a given y (two values for the quadratic regression). */
    fun predictX(y: Double): List<Double> {
        val a = coef[0]; val b = coef[1]
        return when (type) {
            StatType.LINEAR -> listOf((y - a) / b)
            StatType.QUAD -> {
                val c = coef[2]
                val d = b * b - 4 * c * (a - y)
                if (d < 0) throw CalcError(ErrorKind.MATH)
                listOf((-b + sqrt(d)) / (2 * c), (-b - sqrt(d)) / (2 * c))
            }
            StatType.LOG -> listOf(exp((y - a) / b))
            StatType.EXP -> listOf(ln(y / a) / b)
            StatType.AB -> listOf(ln(y / a) / ln(b))
            StatType.POWER -> listOf(Math.pow(y / a, 1 / b))
            StatType.INV -> listOf(b / (y - a))
            StatType.ONE_VAR -> throw CalcError(ErrorKind.MATH)
        }
    }
}

object Stat {
    private fun median(s: List<Double>): Double {
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
    }

    /**
     * [x] and optional [y] data with optional integer frequencies [freq]. Rows with no x entry should be removed by the caller.
     */
    fun compute(type: StatType, x: List<Double>, y: List<Double>?, freq: List<Double>? = null): StatResult {
        if (x.isEmpty()) throw CalcError(ErrorKind.MATH)
        val f = freq ?: List(x.size) { 1.0 }
        val n = f.sum()
        val out = LinkedHashMap<String, Double>()
        fun sum(g: (Int) -> Double) = x.indices.sumOf { f[it] * g(it) }
        val sx = sum { x[it] }; val sx2 = sum { x[it] * x[it] }
        val mean = sx / n
        out["n"] = n; out["x̄"] = mean; out["Σx"] = sx; out["Σx²"] = sx2
        val ssx = sum { (x[it] - mean) * (x[it] - mean) }
        out["σx"] = sqrt(ssx / n)
        out["sx"] = if (n > 1) sqrt(ssx / (n - 1)) else Double.NaN
        out["minX"] = x.min(); out["maxX"] = x.max()
        if (!type.twoVar) {
            val expanded = x.indices.flatMap { i -> List(f[i].toInt()) { x[i] } }.sorted()
            val m = expanded.size
            out["Q1"] = median(expanded.subList(0, m / 2))
            out["Med"] = median(expanded)
            out["Q3"] = median(expanded.subList((m + 1) / 2, m))
            return StatResult(type, out, doubleArrayOf())
        }
        if (y == null || y.size != x.size) throw CalcError(ErrorKind.MATH)
        val sy = sum { y[it] }; val sy2 = sum { y[it] * y[it] }; val sxy = sum { x[it] * y[it] }
        val my = sy / n
        out["ȳ"] = my; out["Σy"] = sy; out["Σy²"] = sy2; out["Σxy"] = sxy
        val ssy = sum { (y[it] - my) * (y[it] - my) }
        out["σy"] = sqrt(ssy / n)
        out["sy"] = if (n > 1) sqrt(ssy / (n - 1)) else Double.NaN
        out["minY"] = y.min(); out["maxY"] = y.max()
        val coef = DoubleArray(3)
        if (type == StatType.QUAD) {
            val s = DoubleArray(5) { p -> sum { Math.pow(x[it], p.toDouble()) } }
            val t = DoubleArray(3) { p -> sum { Math.pow(x[it], p.toDouble()) * y[it] } }
            val m = MatV.build(3, 3) { r, c -> Real.of(s[r + c]) }
            val sol = try { MatrixOps.solve(m, t.map { Real.of(it) }) } catch (e: CalcError) { throw CalcError(ErrorKind.MATH) }
            for (i in 0 until 3) coef[i] = sol[i].re().d
            out["a"] = coef[0]; out["b"] = coef[1]; out["c"] = coef[2]
            return StatResult(type, out, coef)
        }
        val tx: (Double) -> Double
        val ty: (Double) -> Double
        when (type) {
            StatType.LINEAR -> { tx = { it }; ty = { it } }
            StatType.LOG -> { tx = { ln(it) }; ty = { it } }
            StatType.EXP, StatType.AB -> { tx = { it }; ty = { ln(it) } }
            StatType.POWER -> { tx = { ln(it) }; ty = { ln(it) } }
            else -> { tx = { 1 / it }; ty = { it } }
        }
        val xs = x.map(tx); val ys = y.map(ty)
        if (xs.any { it.isNaN() || it.isInfinite() } || ys.any { it.isNaN() || it.isInfinite() }) throw CalcError(ErrorKind.MATH)
        val mx = x.indices.sumOf { f[it] * xs[it] } / n
        val myy = x.indices.sumOf { f[it] * ys[it] } / n
        val sxx = x.indices.sumOf { f[it] * (xs[it] - mx) * (xs[it] - mx) }
        val syy = x.indices.sumOf { f[it] * (ys[it] - myy) * (ys[it] - myy) }
        val sxyy = x.indices.sumOf { f[it] * (xs[it] - mx) * (ys[it] - myy) }
        if (sxx == 0.0) throw CalcError(ErrorKind.MATH)
        val beta = sxyy / sxx
        val alpha = myy - beta * mx
        val r = if (syy == 0.0) 1.0 else sxyy / sqrt(sxx * syy)
        when (type) {
            StatType.LINEAR, StatType.LOG, StatType.INV -> { coef[0] = alpha; coef[1] = beta }
            StatType.EXP -> { coef[0] = exp(alpha); coef[1] = beta }
            StatType.AB -> { coef[0] = exp(alpha); coef[1] = exp(beta) }
            else -> { coef[0] = exp(alpha); coef[1] = beta }
        }
        out["a"] = coef[0]; out["b"] = coef[1]; out["r"] = r
        return StatResult(type, out, coef)
    }
}

object BaseN {
    /** Formats a signed 32-bit value in the given base (2, 8, 10 or 16). */
    fun format(v: Long, base: Int): String = when (base) {
        10 -> v.toString()
        else -> (v and 0xFFFFFFFFL).toString(base).uppercase()
    }

    fun toLong(v: Value): Long {
        val r = v as? Real ?: throw CalcError(ErrorKind.MATH)
        if (!r.isInteger) throw CalcError(ErrorKind.MATH)
        return r.q!!.num.toLong()
    }
}

object TableGen {
    class Row(val x: Double, val f: Double?, val g: Double?)

    /** Casio TABLE mode: at most 30 rows. Rows whose evaluation fails yield null (shown as ERROR). */
    fun generate(f: String, g: String?, start: Double, end: Double, step: Double, ctx: Context): List<Row> {
        if (step == 0.0 || (end - start) / step < 0) throw CalcError(ErrorKind.ARGUMENT)
        val count = Math.floor((end - start) / step + 1e-9).toInt() + 1
        if (count > 30) throw CalcError(ErrorKind.ARGUMENT)
        fun evalAt(src: String?, x: Double): Double? {
            if (src == null) return null
            val tmp = Context().also { c ->
                c.angle = ctx.angle; c.complex = false; c.vars.putAll(ctx.vars); c.vars["X"] = Real.of(x)
                c.ans = ctx.ans; c.preAns = ctx.preAns; c.mats.putAll(ctx.mats); c.vecs.putAll(ctx.vecs)
            }
            return try { (Evaluator(tmp).run(src).value as? Real)?.d } catch (e: CalcError) { null }
        }
        // Validate syntax up-front so that the user gets Syntax ERROR rather than a column of errors.
        Parser(Lexer(f).tokenize()).parse()
        if (g != null) Parser(Lexer(g).tokenize()).parse()
        return (0 until count).map { i ->
            val x = start + i * step
            Row(x, evalAt(f, x), evalAt(g, x))
        }
    }
}

/** High level entry points used by the UI. */
object Engine {
    fun evaluate(src: String, ctx: Context): EvalResult {
        val res = Evaluator(ctx).run(src)
        ctx.preAns = ctx.ans
        ctx.ans = res.value
        when (val v = res.value) {
            is MatV -> ctx.mats["MatAns"] = v
            is VecV -> ctx.vecs["VctAns"] = v
            else -> {}
        }
        res.store?.let { ctx.vars[it] = res.value }
        return res
    }

    /** CALC: evaluates [src] with the given variable assignments, without touching Ans. */
    fun calc(src: String, ctx: Context, assign: Map<String, Value>): Value {
        val saved = HashMap(ctx.vars)
        ctx.vars.putAll(assign)
        try { return Evaluator(ctx).run(src).value } finally { ctx.vars.clear(); ctx.vars.putAll(saved) }
    }

    class SolveResult(val x: Double, val diff: Double)

    /** SOLVE: finds X so that LHS = RHS (or expression = 0 when no '=' present). */
    fun solve(src: String, ctx: Context, guess: Double): SolveResult {
        val parsed = Parser(Lexer(src).tokenize()).parse()
        val node = parsed.node
        val ev = Evaluator(ctx)
        val saved = ctx.vars["X"]
        fun lr(x: Double): Double {
            ctx.vars["X"] = Real.of(x)
            return if (node is EqN) (ev.eval(node.l) as? Real ?: throw CalcError(ErrorKind.MATH)).d - (ev.eval(node.r) as? Real ?: throw CalcError(ErrorKind.MATH)).d
            else (ev.eval(node) as? Real ?: throw CalcError(ErrorKind.MATH)).d
        }
        try {
            val x = round15(Calculus.solve(::lr, guess))
            val d = lr(x)
            return SolveResult(x, if (abs(d) < 1e-12) 0.0 else d)
        } finally { if (saved == null) ctx.vars.remove("X") else ctx.vars["X"] = saved }
    }
}
