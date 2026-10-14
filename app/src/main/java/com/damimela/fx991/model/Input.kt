package com.damimela.fx991.model

import com.damimela.fx991.engine.MCursor
import com.damimela.fx991.engine.MFrac
import com.damimela.fx991.engine.MMixed
import com.damimela.fx991.engine.MNode
import com.damimela.fx991.engine.MPlace
import com.damimela.fx991.engine.MRow
import com.damimela.fx991.engine.MSqrt
import com.damimela.fx991.engine.MSup
import com.damimela.fx991.engine.MText

/** Structural role of an input token: templates (fraction, root, exponent...) are made of several tokens. */
enum class Role {
    NORMAL, FRAC_OPEN, FRAC_SEP, MIX_OPEN, MIX_SEP1, MIX_SEP2, SQRT_OPEN, ROOT_OPEN, ROOT_SEP,
    SUP_OPEN, LOGB_OPEN, LOGB_SEP, CLOSE
}

/**
 * One element of the input line. [text] is what the engine parser sees, [display] what the user sees.
 */
data class InTok(val text: String, val display: String = text, val role: Role = Role.NORMAL) {
    val isOpener get() = role != Role.NORMAL && role != Role.CLOSE && role != Role.FRAC_SEP &&
        role != Role.MIX_SEP1 && role != Role.MIX_SEP2 && role != Role.ROOT_SEP && role != Role.LOGB_SEP
    val isSeparator get() = role == Role.FRAC_SEP || role == Role.MIX_SEP1 || role == Role.MIX_SEP2 ||
        role == Role.ROOT_SEP || role == Role.LOGB_SEP
}

object Toks {
    fun t(s: String) = InTok(s)
    fun digit(c: Char) = InTok(c.toString())
    val FRAC = listOf(
        InTok("((", "", Role.FRAC_OPEN), InTok(")÷(", "", Role.FRAC_SEP), InTok("))", "", Role.CLOSE)
    )
    val FRAC_CURSOR = 1
    val MIXED = listOf(
        InTok("(", "", Role.MIX_OPEN), InTok("+((", "", Role.MIX_SEP1), InTok(")÷(", "", Role.MIX_SEP2),
        InTok(")))", "", Role.CLOSE)
    )
    val SQRT = listOf(InTok("√(", "", Role.SQRT_OPEN), InTok(")", "", Role.CLOSE))
    val ROOT = listOf(InTok("root(", "", Role.ROOT_OPEN), InTok(",", "", Role.ROOT_SEP), InTok(")", "", Role.CLOSE))
    val SUP = listOf(InTok("^(", "", Role.SUP_OPEN), InTok(")", "", Role.CLOSE))
    val LOGB = listOf(InTok("log(", "", Role.LOGB_OPEN), InTok(",", "", Role.LOGB_SEP), InTok(")", "", Role.CLOSE))

    /** Concatenates token texts for the engine and returns the start offset of every token. */
    fun serialize(tokens: List<InTok>): Pair<String, IntArray> {
        val sb = StringBuilder()
        val offs = IntArray(tokens.size + 1)
        for ((i, t) in tokens.withIndex()) { offs[i] = sb.length; sb.append(t.text) }
        offs[tokens.size] = sb.length
        return sb.toString() to offs
    }

    /** Maps a character offset in the serialized expression back to the index of the token containing it. */
    fun tokenAt(tokens: List<InTok>, charPos: Int): Int {
        val (_, offs) = serialize(tokens)
        if (tokens.isEmpty()) return 0
        for (i in tokens.indices) if (charPos < offs[i + 1]) return i
        return tokens.size
    }

    /** Index of the token that closes (or separates) the template opened at [open]; -1 if malformed. */
    fun matching(tokens: List<InTok>, open: Int): List<Int> {
        val idx = ArrayList<Int>()
        var depth = 0
        for (i in open until tokens.size) {
            val t = tokens[i]
            if (t.isOpener) depth++
            if (depth == 1 && t.isSeparator) idx += i
            if (t.role == Role.CLOSE) { depth--; if (depth == 0) { idx += i; return idx } }
        }
        return emptyList()
    }

    /** Builds the natural-display tree of the input line with the cursor marker at [cursor]. */
    fun toNode(tokens: List<InTok>, cursor: Int?, insert: Boolean = true): MNode {
        var i = 0
        val n = tokens.size
        fun row(stop: (Role) -> Boolean, boxIfEmpty: Boolean): MNode {
            val items = ArrayList<MNode>()
            while (true) {
                if (cursor != null && i == cursor) items += MCursor(insert)
                if (i >= n) break
                val t = tokens[i]
                if (stop(t.role)) break
                i++
                when (t.role) {
                    Role.NORMAL -> items += MText(t.display)
                    Role.FRAC_OPEN -> {
                        val a = row({ it == Role.FRAC_SEP }, true); i++
                        val b = row({ it == Role.CLOSE }, true); i++
                        items += MFrac(a, b)
                    }
                    Role.MIX_OPEN -> {
                        val w = row({ it == Role.MIX_SEP1 }, true); i++
                        val a = row({ it == Role.MIX_SEP2 }, true); i++
                        val b = row({ it == Role.CLOSE }, true); i++
                        items += MMixed(w, a, b)
                    }
                    Role.SQRT_OPEN -> { val a = row({ it == Role.CLOSE }, true); i++; items += MSqrt(a) }
                    Role.ROOT_OPEN -> {
                        val idx = row({ it == Role.ROOT_SEP }, true); i++
                        val a = row({ it == Role.CLOSE }, true); i++
                        items += MSqrt(a, idx)
                    }
                    Role.SUP_OPEN -> { val a = row({ it == Role.CLOSE }, true); i++; items += MSup(MRow(emptyList()), a) }
                    Role.LOGB_OPEN -> {
                        val b = row({ it == Role.LOGB_SEP }, true); i++
                        val a = row({ it == Role.CLOSE }, false); i++
                        items += MText("log"); items += MSup(MRow(emptyList()), b, down = true)
                        items += MText("("); items += a; items += MText(")")
                    }
                    else -> items += MText(t.display)
                }
            }
            if (boxIfEmpty && items.none { it !is MCursor }) items += MPlace
            return MRow(items)
        }
        return row({ false }, false)
    }

    /** Inserts [ins] at [cursor]; returns the new token list. */
    fun insert(tokens: List<InTok>, cursor: Int, ins: List<InTok>): List<InTok> =
        tokens.subList(0, cursor) + ins + tokens.subList(cursor, tokens.size)

    /** Cursor index when a template is inserted: just after the first token. */
    fun templateCursor(role: Role) = 1
}
