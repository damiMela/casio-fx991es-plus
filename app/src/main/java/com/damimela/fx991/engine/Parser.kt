package com.damimela.fx991.engine

import java.math.BigDecimal

enum class TokType { NUM, NAME, OP, LP, RP, COMMA, POST, OUT, STO, EQ, END }

class Tok(val type: TokType, val text: String, val pos: Int, val num: Real? = null)

object Names {
    val FUNCS = setOf(
        "sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
        "log", "ln", "√", "∛", "root", "Abs", "Int", "Intg", "Rnd", "GCD", "LCM", "Pol", "Rec", "RanInt#",
        "∫", "d/dx", "Σ", "Conjg", "Arg", "ReP", "ImP", "Det", "Trn", "Ide", "Dot", "Angle", "UnitV",
        "Not", "Neg", "CONST", "CONV", "Sign"
    )
    val ATOMS = setOf(
        "π", "e", "i", "Ans", "PreAns", "Ran#", "MatA", "MatB", "MatC", "MatAns", "VctA", "VctB", "VctC", "VctAns",
        "A", "B", "C", "D", "E", "F", "X", "Y", "M"
    )
    val INFIX = setOf("nPr", "nCr", "and", "or", "xor", "xnor")
    val ALL = (FUNCS + ATOMS + INFIX).sortedByDescending { it.length }
    val OUTS = listOf("▶DMS", "▶r∠θ", "▶a+bi", "▶DEC", "▶HEX", "▶BIN", "▶OCT")
}

/** Converts an expression string into tokens. [base] != 10 selects BASE-N literal parsing. */
class Lexer(private val src: String, private val base: Int = 10) {

    fun tokenize(): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c == ' ' -> i++
                c.isDigit() && base == 10 || c == '.' && base == 10 || c == 'ᴇ' && base == 10 -> i = number(i, out)
                base != 10 && digitVal(c) in 0 until base -> {
                    var j = i
                    while (j < src.length && digitVal(src[j]).let { it in 0 until base }) j++
                    val v = src.substring(i, j).toLong(base)
                    if (v > 0xFFFFFFFFL) throw CalcError(ErrorKind.MATH, i)
                    out += Tok(TokType.NUM, src.substring(i, j), i, Real.of(v.toInt().toLong()))
                    i = j
                }
                c == '(' -> { out += Tok(TokType.LP, "(", i); i++ }
                c == ')' -> { out += Tok(TokType.RP, ")", i); i++ }
                c == ',' -> { out += Tok(TokType.COMMA, ",", i); i++ }
                c == '+' -> { out += Tok(TokType.OP, "+", i); i++ }
                c == '-' || c == '−' -> { out += Tok(TokType.OP, "-", i); i++ }
                c == '×' || c == '*' -> { out += Tok(TokType.OP, "×", i); i++ }
                c == '÷' || c == '/' -> { out += Tok(TokType.OP, "÷", i); i++ }
                c == '^' -> { out += Tok(TokType.OP, "^", i); i++ }
                c == '∠' -> { out += Tok(TokType.OP, "∠", i); i++ }
                c == '²' || c == '³' || c == '!' || c == '%' || c == '°' || c == '′' || c == '″' ->
                    { out += Tok(TokType.POST, c.toString(), i); i++ }
                c == '⁻' && i + 1 < src.length && src[i + 1] == '¹' -> { out += Tok(TokType.POST, "⁻¹", i); i += 2 }
                c == '→' -> { out += Tok(TokType.STO, "→", i); i++ }
                c == '=' -> { out += Tok(TokType.EQ, "=", i); i++ }
                c == '▶' -> {
                    val o = Names.OUTS.firstOrNull { src.startsWith(it, i) } ?: throw CalcError(ErrorKind.SYNTAX, i)
                    out += Tok(TokType.OUT, o, i); i += o.length
                }
                else -> {
                    val n = Names.ALL.firstOrNull { src.startsWith(it, i) } ?: throw CalcError(ErrorKind.SYNTAX, i)
                    out += Tok(TokType.NAME, n, i); i += n.length
                }
            }
        }
        out += Tok(TokType.END, "", src.length)
        return out
    }

    private fun digitVal(c: Char) = when (c) {
        in '0'..'9' -> c - '0'
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    private fun number(start: Int, out: MutableList<Tok>): Int {
        var i = start
        val mant = StringBuilder()
        var dot = false
        while (i < src.length && (src[i].isDigit() || src[i] == '.')) {
            if (src[i] == '.') { if (dot) throw CalcError(ErrorKind.SYNTAX, i); dot = true }
            mant.append(src[i]); i++
        }
        var exp = 0
        var hasExp = false
        if (i < src.length && src[i] == 'ᴇ') {
            hasExp = true
            i++
            var neg = false
            if (i < src.length && (src[i] == '-' || src[i] == '−')) { neg = true; i++ }
            val s = i
            while (i < src.length && src[i].isDigit()) i++
            if (s == i) throw CalcError(ErrorKind.SYNTAX, s)
            exp = src.substring(s, i).toIntOrNull() ?: throw CalcError(ErrorKind.MATH, start)
            if (neg) exp = -exp
        }
        if (mant.isEmpty() || mant.toString() == ".") {
            if (!hasExp) throw CalcError(ErrorKind.SYNTAX, start)
            mant.setLength(0); mant.append("1"); dot = false
        }
        if (Math.abs(exp) > 99) throw CalcError(ErrorKind.MATH, start)
        val value: Real = if (dot) {
            Real.of(BigDecimal(mant.toString()).scaleByPowerOfTen(exp).toDouble())
        } else {
            val f = Frac.of(java.math.BigInteger(mant.toString()))
            Real.of(if (exp >= 0) f * Frac.of(java.math.BigInteger.TEN.pow(exp)) else f / Frac.of(java.math.BigInteger.TEN.pow(-exp)))
        }
        out += Tok(TokType.NUM, src.substring(start, i), start, value)
        return i
    }
}

sealed class Node(val pos: Int)
class NumN(val v: Real, pos: Int) : Node(pos)
class NameN(val name: String, pos: Int) : Node(pos)
class UnN(val op: String, val x: Node, pos: Int) : Node(pos)
class BinN(val op: String, val l: Node, val r: Node, pos: Int) : Node(pos)
class PostN(val op: String, val x: Node, val arg: Int = 0, pos: Int) : Node(pos)
class CallN(val fn: String, val args: List<Node>, pos: Int) : Node(pos)
class EqN(val l: Node, val r: Node, pos: Int) : Node(pos)

class Parsed(val node: Node, val outs: List<String>, val store: String?)

class Parser(private val toks: List<Tok>) {
    private var i = 0
    private var depth = 0
    private val cur get() = toks[i]

    fun parse(): Parsed {
        if (cur.type == TokType.END) throw CalcError(ErrorKind.SYNTAX, 0)
        var node = expr()
        if (cur.type == TokType.EQ) {
            val p = cur.pos; i++
            node = EqN(node, expr(), p)
        }
        val outs = ArrayList<String>()
        while (cur.type == TokType.OUT) { outs += cur.text; i++ }
        var store: String? = null
        if (cur.type == TokType.STO) {
            i++
            val t = cur
            if (t.type != TokType.NAME || t.text !in Names.ATOMS || t.text in setOf("π", "e", "i", "Ans", "PreAns", "Ran#")) {
                throw CalcError(ErrorKind.SYNTAX, t.pos)
            }
            store = t.text; i++
        }
        if (cur.type != TokType.END) throw CalcError(ErrorKind.SYNTAX, cur.pos)
        return Parsed(node, outs, store)
    }

    private fun expr(): Node {
        if (++depth > 24) throw CalcError(ErrorKind.STACK, cur.pos)
        val n = logicOr()
        depth--
        return n
    }

    private fun isWord(w: String) = cur.type == TokType.NAME && cur.text == w

    private fun logicOr(): Node {
        var l = logicAnd()
        while (isWord("or") || isWord("xor") || isWord("xnor")) {
            val t = cur; i++
            l = BinN(t.text, l, logicAnd(), t.pos)
        }
        return l
    }

    private fun logicAnd(): Node {
        var l = add()
        while (isWord("and")) {
            val t = cur; i++
            l = BinN("and", l, add(), t.pos)
        }
        return l
    }

    private fun add(): Node {
        var l = mul()
        while (cur.type == TokType.OP && (cur.text == "+" || cur.text == "-")) {
            val t = cur; i++
            l = BinN(t.text, l, mul(), t.pos)
        }
        return l
    }

    private fun mul(): Node {
        var l = perm()
        while (cur.type == TokType.OP && (cur.text == "×" || cur.text == "÷")) {
            val t = cur; i++
            l = BinN(t.text, l, perm(), t.pos)
        }
        return l
    }

    private fun perm(): Node {
        var l = implicit()
        while ((cur.type == TokType.NAME && (cur.text == "nPr" || cur.text == "nCr")) ||
            (cur.type == TokType.OP && cur.text == "∠")) {
            val t = cur; i++
            l = BinN(t.text, l, implicit(), t.pos)
        }
        return l
    }

    private fun startsTerm(t: Tok) = when (t.type) {
        TokType.NUM, TokType.LP -> true
        TokType.NAME -> t.text !in Names.INFIX
        else -> false
    }

    private fun implicit(): Node {
        var l = unary()
        while (startsTerm(cur)) {
            val p = cur.pos
            l = BinN("×", l, power(), p)
        }
        return l
    }

    private fun unary(): Node {
        if (cur.type == TokType.OP && (cur.text == "-" || cur.text == "+")) {
            val t = cur; i++
            val x = unary()
            return if (t.text == "-") UnN("neg", x, t.pos) else x
        }
        return power()
    }

    private fun power(): Node {
        var base = postfix()
        if (cur.type == TokType.OP && cur.text == "^") {
            val t = cur; i++
            if (++depth > 24) throw CalcError(ErrorKind.STACK, t.pos)
            val e = unary()
            depth--
            base = BinN("^", base, e, t.pos)
        }
        return base
    }

    private fun postfix(): Node {
        var x = atom()
        while (true) {
            val t = cur
            if (t.type == TokType.POST) {
                i++
                x = if (t.text == "°") dms(x, t) else PostN(t.text, x, 0, t.pos)
            } else if (t.type == TokType.NAME && t.text == "CONV") {
                i++
                if (cur.type != TokType.LP) throw CalcError(ErrorKind.SYNTAX, cur.pos)
                i++
                val n = cur
                if (n.type != TokType.NUM || n.num?.q?.isInteger != true) throw CalcError(ErrorKind.SYNTAX, n.pos)
                i++
                if (cur.type == TokType.RP) i++ else if (cur.type != TokType.END) throw CalcError(ErrorKind.SYNTAX, cur.pos)
                x = PostN("conv", x, n.num!!.d.toInt(), t.pos)
            } else break
        }
        return x
    }

    /** Handles d°m′s″ entry: after '°' an optional "m′" and "s″" may follow. */
    private fun dms(deg: Node, t: Tok): Node {
        var total: Node = PostN("°", deg, 0, t.pos)
        if (cur.type == TokType.NUM && toks[i + 1].type == TokType.POST && toks[i + 1].text == "′") {
            val m = NumN(cur.num!!, cur.pos)
            i += 2
            total = BinN("+", total, PostN("′", m, 0, t.pos), t.pos)
        }
        if (cur.type == TokType.NUM && toks[i + 1].type == TokType.POST && toks[i + 1].text == "″") {
            val s = NumN(cur.num!!, cur.pos)
            i += 2
            total = BinN("+", total, PostN("″", s, 0, t.pos), t.pos)
        }
        return total
    }

    private fun closeParen() {
        if (cur.type == TokType.RP) i++
        else if (cur.type != TokType.END) throw CalcError(ErrorKind.SYNTAX, cur.pos)
    }

    private fun atom(): Node {
        val t = cur
        when (t.type) {
            TokType.NUM -> { i++; return NumN(t.num!!, t.pos) }
            TokType.LP -> {
                i++
                val e = expr()
                closeParen()
                return e
            }
            TokType.NAME -> {
                if (t.text in Names.ATOMS) { i++; return NameN(t.text, t.pos) }
                if (t.text in Names.FUNCS && t.text != "CONV") {
                    i++
                    if (cur.type == TokType.LP) {
                        i++
                        val args = ArrayList<Node>()
                        if (cur.type == TokType.RP) throw CalcError(ErrorKind.SYNTAX, cur.pos)
                        args += expr()
                        while (cur.type == TokType.COMMA) { i++; args += expr() }
                        closeParen()
                        return CallN(t.text, args, t.pos)
                    }
                    return CallN(t.text, listOf(unary()), t.pos)
                }
            }
            else -> {}
        }
        throw CalcError(ErrorKind.SYNTAX, t.pos)
    }
}
