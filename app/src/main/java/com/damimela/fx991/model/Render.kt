package com.damimela.fx991.model

import com.damimela.fx991.engine.BaseN
import com.damimela.fx991.engine.Cplx
import com.damimela.fx991.engine.Formatter
import com.damimela.fx991.engine.MNode
import com.damimela.fx991.engine.MText
import com.damimela.fx991.engine.Real
import com.damimela.fx991.engine.Value

class RenderLine(val label: String?, val node: MNode)

/** Pure helpers that turn calculator state into natural-display trees. */
object Render {
    fun valueNode(v: Value, st: CalcState, rv: ResultView?, decimalOverride: Boolean? = null): MNode {
        val flip = rv?.flipExact ?: false
        val exact = decimalOverride?.let { !it } ?: (st.display.mathIO != flip)
        val polar = when {
            rv != null && "▶r∠θ" in rv.outs -> true
            rv != null && "▶a+bi" in rv.outs -> false
            else -> st.display.polarComplex != (rv?.flipPolar ?: false)
        }
        val disp = st.display.copy(mixedFractions = st.display.mixedFractions != (rv?.flipMixed ?: false))
        return Formatter.format(v, disp, exact, rv?.eng, polar, st.angle)
    }

    /** Base used to show a result in BASE-N mode. */
    fun baseOf(st: CalcState, rv: ResultView): Int? {
        if (st.mode != CalcMode.BASE) return null
        return when {
            "▶HEX" in rv.outs -> 16
            "▶BIN" in rv.outs -> 2
            "▶OCT" in rv.outs -> 8
            "▶DEC" in rv.outs -> 10
            else -> st.baseNum
        }
    }

    fun baseText(v: Value, base: Int): String? = (v as? Real)?.let { if (it.isInteger) BaseN.format(it.d.toLong(), base) else null }

    fun baseLines(st: CalcState): List<Pair<String, String>> {
        val rv = st.result ?: return emptyList()
        val v = rv.value as? Real ?: return emptyList()
        return listOf("HEX" to 16, "DEC" to 10, "BIN" to 2, "OCT" to 8).mapNotNull { (n, b) ->
            baseText(v, b)?.let { n to if (b == 2) groupBinary(it) else it }
        }
    }

    private fun groupBinary(s: String): String = s.reversed().chunked(4).joinToString(" ").reversed()

    fun resultLines(st: CalcState): List<RenderLine> {
        val rv = st.result ?: return emptyList()
        val base = baseOf(st, rv)
        if (base != null) {
            val t = baseText(rv.value, base)
            return listOf(RenderLine(null, MText(t ?: Formatter.text(rv.value, st.display, false))))
        }
        if ("▶DMS" in rv.outs && rv.value is Real) return listOf(RenderLine(null, MText(Formatter.dms(rv.value.d))))
        if (rv.extras.isNotEmpty() && rv.label == null) {
            return rv.extras.map { (l, v) -> RenderLine("$l=", valueNode(v, st, rv)) }
        }
        val lines = ArrayList<RenderLine>()
        lines += RenderLine(rv.label, valueNode(rv.value, st, rv))
        rv.extras.forEach { (l, v) -> lines += RenderLine(l, valueNode(v, st, rv)) }
        return lines
    }

    fun resultText(st: CalcState): String = resultLines(st).joinToString(" ") { Formatter.plain(it.node) }

    fun isComplex(v: Value?) = v is Cplx
}
