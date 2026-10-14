package com.damimela.fx991.engine

import java.util.Random
import kotlin.math.*

/** Mutable calculation state shared by evaluations (modes, variables, memories). */
class Context {
    var angle = AngleUnit.DEG
    var complex = false
    /** 2/8/10/16 when in BASE-N mode, otherwise 0. */
    var baseMode = 0
    val vars = HashMap<String, Value>()
    var ans: Value = Real.ZERO
    var preAns: Value = Real.ZERO
    val mats = HashMap<String, MatV>()
    val vecs = HashMap<String, VecV>()
    var random = Random()
    var stat: StatResult? = null
    /** Rounding used by Rnd( (matches the display digits). */
    var rnd: (Double) -> Double = { x -> if (x == 0.0) 0.0 else java.math.BigDecimal(x).round(java.math.MathContext(10)).toDouble() }

    fun variable(name: String): Value = vars[name] ?: Real.ZERO
}

class EvalResult(
    val value: Value,
    val outs: List<String> = emptyList(),
    val extras: List<Pair<String, Value>> = emptyList(),
    val store: String? = null
)

class Evaluator(private val ctx: Context) {
    private var extras: List<Pair<String, Value>> = emptyList()
    private val cx get() = ctx.complex

    fun run(src: String): EvalResult {
        val toks = Lexer(src, if (ctx.baseMode == 0) 10 else ctx.baseMode).tokenize()
        val parsed = Parser(toks).parse()
        if (parsed.node is EqN) throw CalcError(ErrorKind.SYNTAX, parsed.node.pos)
        val v = try { eval(parsed.node) } catch (e: CalcError) { throw e.at(parsed.node.pos) }
        return EvalResult(v, parsed.outs, extras, parsed.store)
    }

    fun evalNode(n: Node): Value = eval(n)

    private fun real(v: Value): Real = v as? Real ?: throw CalcError(ErrorKind.MATH)

    fun eval(n: Node): Value = try { evalInner(n) } catch (e: CalcError) { throw e.at(n.pos) }

    private fun evalInner(n: Node): Value = when (n) {
        is NumN -> n.v
        is NameN -> atom(n)
        is UnN -> if (ctx.baseMode != 0) baseNeg(eval(n.x)) else Ops.neg(eval(n.x))
        is EqN -> throw CalcError(ErrorKind.SYNTAX)
        is BinN -> binary(n)
        is PostN -> postfix(n)
        is CallN -> call(n)
    }

    private fun atom(n: NameN): Value = when (n.name) {
        "π" -> Real.PI
        "e" -> Real.of(Math.E)
        "i" -> if (cx) Cplx(Real.ZERO, Real.ONE) else throw CalcError(ErrorKind.SYNTAX)
        "Ans" -> ctx.ans
        "PreAns" -> ctx.preAns
        "Ran#" -> Real.of(floor(ctx.random.nextDouble() * 1000) / 1000.0)
        "MatA", "MatB", "MatC", "MatAns" -> ctx.mats[n.name] ?: throw CalcError(ErrorKind.MATH)
        "VctA", "VctB", "VctC", "VctAns" -> ctx.vecs[n.name] ?: throw CalcError(ErrorKind.MATH)
        else -> StatVars.TABLE.firstOrNull { it.first == n.name }?.let { (_, key) ->
            Real.of(ctx.stat?.values?.get(key)?.takeIf { v -> !v.isNaN() } ?: throw CalcError(ErrorKind.MATH))
        } ?: ctx.variable(n.name)
    }

    // ---- BASE-N -------------------------------------------------------------------------------
    private fun checkBase(v: Long): Real {
        if (v < Int.MIN_VALUE || v > Int.MAX_VALUE) throw CalcError(ErrorKind.MATH)
        return Real.of(v)
    }

    private fun baseInt(v: Value): Long {
        val r = v as? Real ?: throw CalcError(ErrorKind.SYNTAX)
        if (!r.isInteger) throw CalcError(ErrorKind.SYNTAX)
        return r.q!!.num.toLong()
    }

    private fun baseNeg(v: Value) = checkBase(-baseInt(v))

    private fun baseBinary(op: String, a: Value, b: Value): Value {
        val x = baseInt(a); val y = baseInt(b)
        return when (op) {
            "+" -> checkBase(x + y)
            "-" -> checkBase(x - y)
            "×" -> checkBase(x * y)
            "÷" -> { if (y == 0L) throw CalcError(ErrorKind.MATH); checkBase(x / y) }
            "and" -> checkBase(x and y)
            "or" -> checkBase(x or y)
            "xor" -> checkBase(x xor y)
            "xnor" -> checkBase((x xor y).inv())
            else -> throw CalcError(ErrorKind.SYNTAX)
        }
    }

    // ---- operators ----------------------------------------------------------------------------
    private fun binary(n: BinN): Value {
        val a = eval(n.l)
        val b = eval(n.r)
        if (ctx.baseMode != 0) return baseBinary(n.op, a, b)
        return when (n.op) {
            "+" -> Ops.add(a, b)
            "-" -> Ops.sub(a, b)
            "×" -> if (a is VecV && b is VecV) VectorOps.cross(a, b) else Ops.mul(a, b)
            "÷" -> Ops.div(a, b)
            "^" -> Num.pow(a, b, cx)
            "nPr" -> Num.nPr(a, b)
            "nCr" -> Num.nCr(a, b)
            "∠" -> {
                if (!cx) throw CalcError(ErrorKind.SYNTAX)
                val r = real(a); val t = real(b)
                cplx(r * Trig.cos(t, ctx.angle), r * Trig.sin(t, ctx.angle))
            }
            else -> throw CalcError(ErrorKind.SYNTAX)
        }
    }

    private fun postfix(n: PostN): Value {
        if (ctx.baseMode != 0) throw CalcError(ErrorKind.SYNTAX)
        val x = eval(n.x)
        return when (n.op) {
            "²" -> Num.pow(x, Real.of(2), cx)
            "³" -> Num.pow(x, Real.of(3), cx)
            "⁻¹" -> when (x) {
                is MatV -> MatrixOps.inverse(x)
                is VecV -> throw CalcError(ErrorKind.MATH)
                else -> Ops.div(Real.ONE, x)
            }
            "!" -> Num.factorial(x)
            "%" -> Ops.div(x, Real.of(100))
            "°" -> Angles.fromDegrees(real(x), ctx.angle)
            "ʳ" -> Angles.fromRadians(real(x), ctx.angle)
            "ᵍ" -> Angles.fromGradians(real(x), ctx.angle)
            "′" -> Angles.fromDegrees(real(x) / Real.of(60), ctx.angle)
            "″" -> Angles.fromDegrees(real(x) / Real.of(3600), ctx.angle)
            "conv" -> Constants.convert(n.arg, x)
            else -> throw CalcError(ErrorKind.SYNTAX)
        }
    }

    // ---- functions ----------------------------------------------------------------------------
    private fun arity(n: CallN, vararg ok: Int) {
        if (n.args.size !in ok) throw CalcError(ErrorKind.ARGUMENT, n.pos)
    }

    private fun fOfX(node: Node, name: String = "X"): (Double) -> Double = { x ->
        val saved = ctx.vars[name]
        ctx.vars[name] = Real.of(x)
        try { real(eval(node)).d } finally { if (saved == null) ctx.vars.remove(name) else ctx.vars[name] = saved }
    }

    private fun call(n: CallN): Value {
        if (ctx.baseMode != 0) {
            if (n.fn == "Not") { arity(n, 1); return checkBase(baseInt(eval(n.args[0])).inv()) }
            if (n.fn == "Neg") { arity(n, 1); return baseNeg(eval(n.args[0])) }
            throw CalcError(ErrorKind.SYNTAX, n.pos)
        }
        when (n.fn) {
            "∫" -> {
                arity(n, 3)
                val a = real(eval(n.args[1])).d; val b = real(eval(n.args[2])).d
                return r12(Calculus.integrate(fOfX(n.args[0]), a, b))
            }
            "d/dx" -> {
                arity(n, 2)
                val x0 = real(eval(n.args[1])).d
                return r12(Calculus.derivative(fOfX(n.args[0]), x0))
            }
            "Σ" -> {
                arity(n, 4)
                val vn = (n.args[1] as? NameN)?.name?.takeIf { it in setOf("A", "B", "C", "D", "E", "F", "X", "Y", "M") }
                    ?: throw CalcError(ErrorKind.ARGUMENT, n.pos)
                val a = Num.intValue(eval(n.args[2])); val b = Num.intValue(eval(n.args[3]))
                if (b < a) throw CalcError(ErrorKind.ARGUMENT, n.pos)
                if (b - a > 100000) throw CalcError(ErrorKind.ARGUMENT, n.pos)
                val saved = ctx.vars[vn]
                try {
                    var s: Value = Real.ZERO
                    for (k in a..b) { ctx.vars[vn] = Real.of(k); s = Ops.add(s, eval(n.args[0])) }
                    return s
                } finally { if (saved == null) ctx.vars.remove(vn) else ctx.vars[vn] = saved }
            }
        }
        val a = n.args.map { eval(it) }
        val u = ctx.angle
        fun one(): Value { arity(n, 1); return a[0] }
        fun two(): Pair<Value, Value> { arity(n, 2); return a[0] to a[1] }
        return when (n.fn) {
            "sin", "cos", "tan" -> trig(n.fn, one())
            "asin", "acos", "atan" -> invTrig(n.fn, one())
            "sinh", "cosh", "tanh", "asinh", "acosh", "atanh" -> hyper(n.fn, one())
            "log" -> if (a.size == 2) Num.logBase(a[0], a[1], cx) else Num.log10(one(), cx)
            "ln" -> Num.ln(one(), cx)
            "√" -> Num.sqrt(one(), cx)
            "∛" -> Num.cbrt(one())
            "root" -> { val (k, x) = two(); Num.pow(x, Ops.div(Real.ONE, k), cx) }
            "Abs" -> when (val v = one()) {
                is MatV -> MatrixOps.map(v) { Ops.abs(it) }
                else -> Ops.abs(v)
            }
            "Int" -> Num.intPart(one())
            "Intg" -> Num.floorPart(one())
            "Rnd" -> { val v = real(one()); Real.of(ctx.rnd(v.d)) }
            "Sign" -> Real.of(Math.signum(real(one()).d).toInt())
            "GCD" -> { val (x, y) = two(); Num.gcd(x, y) }
            "LCM" -> { val (x, y) = two(); Num.lcm(x, y) }
            "RanInt#" -> {
                val (x, y) = two()
                val lo = Num.intValue(x); val hi = Num.intValue(y)
                if (hi < lo) throw CalcError(ErrorKind.ARGUMENT)
                Real.of(lo + (ctx.random.nextDouble() * (hi - lo + 1)).toLong())
            }
            "Pol" -> {
                val (x, y) = two()
                val xr = real(x); val yr = real(y)
                val r = Ops.sqrtReal(xr * xr + yr * yr)
                val t = r15(Angles.fromRad(atan2(yr.d, xr.d), u))
                ctx.vars["X"] = r; ctx.vars["Y"] = t
                extras = listOf("r" to r, "θ" to t)
                r
            }
            "Rec" -> {
                val (r, t) = two()
                val rr = real(r); val tt = real(t)
                val x = rr * Trig.cos(tt, u); val y = rr * Trig.sin(tt, u)
                ctx.vars["X"] = x; ctx.vars["Y"] = y
                extras = listOf("x" to x, "y" to y)
                x
            }
            "RanMat" -> {
                val (r, c) = two()
                val rr = Num.intValue(r, 4); val cc = Num.intValue(c, 4)
                if (rr < 1 || cc < 1) throw CalcError(ErrorKind.ARGUMENT)
                MatV.build(rr.toInt(), cc.toInt()) { _, _ -> Real.of(floor(ctx.random.nextDouble() * 1000) / 1000.0) }
            }
            "@P" -> Real.of(round15(Distr.p(real(one()).d)))
            "@Q" -> Real.of(round15(Distr.q(real(one()).d)))
            "@R" -> Real.of(round15(Distr.r(real(one()).d)))
            "@t" -> {
                val st = ctx.stat ?: throw CalcError(ErrorKind.MATH)
                Real.of(round15((real(one()).d - st["x̄"]) / st["σx"]))
            }
            "@xhat" -> {
                val st = ctx.stat ?: throw CalcError(ErrorKind.MATH)
                Real.of(round15(st.predictX(real(one()).d).first()))
            }
            "@yhat" -> {
                val st = ctx.stat ?: throw CalcError(ErrorKind.MATH)
                Real.of(round15(st.predictY(real(one()).d)))
            }
            "Conjg" -> Ops.conj(one())
            "Arg" -> { val z = one(); if (!cx) throw CalcError(ErrorKind.SYNTAX); r15(Angles.fromRad(atan2(z.im().d, z.re().d), u)) }
            "ReP" -> one().re()
            "ImP" -> one().im()
            "Det" -> MatrixOps.det(one() as? MatV ?: throw CalcError(ErrorKind.MATH))
            "Trn" -> MatrixOps.transpose(one() as? MatV ?: throw CalcError(ErrorKind.MATH))
            "Ide" -> {
                val k = Num.intValue(one(), MatrixOps.MAX_DIM.toLong())
                if (k < 1) throw CalcError(ErrorKind.ARGUMENT)
                Ops.identity(k.toInt())
            }
            "Dot" -> { val (x, y) = two(); VectorOps.dot(x as? VecV ?: throw CalcError(ErrorKind.MATH), y as? VecV ?: throw CalcError(ErrorKind.MATH)) }
            "Angle" -> {
                val (x, y) = two()
                val rad = VectorOps.angleRad(x as? VecV ?: throw CalcError(ErrorKind.MATH), y as? VecV ?: throw CalcError(ErrorKind.MATH))
                r15(Angles.fromRad(rad, u))
            }
            "UnitV" -> VectorOps.unit(one() as? VecV ?: throw CalcError(ErrorKind.MATH))
            "CONST" -> {
                val k = Num.intValue(one())
                Constants.const(k.toInt())
            }
            "Not", "Neg" -> throw CalcError(ErrorKind.SYNTAX, n.pos)
            else -> throw CalcError(ErrorKind.SYNTAX, n.pos)
        }
    }

    private fun r12(d: Double): Real {
        if (d == 0.0) return Real.ZERO
        return Real.of(java.math.BigDecimal(d).round(java.math.MathContext(12)).toDouble())
    }

    private fun trig(fn: String, v: Value): Value {
        val u = ctx.angle
        if (v is Real) return when (fn) {
            "sin" -> Trig.sin(v, u)
            "cos" -> Trig.cos(v, u)
            else -> Trig.tan(v, u)
        }
        if (!cx || v !is Cplx) throw CalcError(ErrorKind.MATH)
        val z = C(Angles.toRad(v.re.d, u), Angles.toRad(v.im.d, u))
        return when (fn) { "sin" -> C.sin(z); "cos" -> C.cos(z); else -> C.tan(z) }.toValue()
    }

    private fun invTrig(fn: String, v: Value): Value {
        val u = ctx.angle
        if (v is Real && (abs(v.d) <= 1 || fn == "atan")) return when (fn) {
            "asin" -> Trig.asin(v, u)
            "acos" -> Trig.acos(v, u)
            else -> Trig.atan(v, u)
        }
        if (!cx) throw CalcError(ErrorKind.MATH)
        val z = v.toC()
        val r = when (fn) { "asin" -> C.asin(z); "acos" -> C.acos(z); else -> C.atan(z) }
        return C(Angles.fromRad(r.re, u), Angles.fromRad(r.im, u)).toValue()
    }

    private fun hyper(fn: String, v: Value): Value {
        if (v is Real) {
            val x = v.d
            val ok = when (fn) { "acosh" -> x >= 1; "atanh" -> abs(x) < 1; else -> true }
            if (ok) {
                if (x == 0.0 && fn != "cosh" && fn != "acosh") return Real.ZERO
                return r15(when (fn) {
                    "sinh" -> sinh(x); "cosh" -> cosh(x); "tanh" -> tanh(x)
                    "asinh" -> asinh(x); "acosh" -> acosh(x); else -> atanh(x)
                })
            }
        }
        if (!cx) throw CalcError(ErrorKind.MATH)
        val z = v.toC()
        return when (fn) {
            "sinh" -> C.sinh(z); "cosh" -> C.cosh(z); "tanh" -> C.tanh(z)
            "asinh" -> C.asinh(z); "acosh" -> C.acosh(z); else -> C.atanh(z)
        }.toValue()
    }
}
