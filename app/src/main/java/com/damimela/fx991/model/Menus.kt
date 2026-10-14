package com.damimela.fx991.model

import com.damimela.fx991.engine.Constants
import com.damimela.fx991.engine.StatType

/** Builders for all the numbered menus of the calculator. */
object Menus {
    private fun tk(text: String, display: String = text) = listOf(InTok(text, display))
    private fun item(label: String, id: String) = MenuItem(label, id)
    private fun ins(label: String, text: String, display: String = label) = MenuItem(label, tokens = tk(text, display))
    private val digitLabels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    fun mode() = MenuDef("mode", "MODE", CalcMode.values().map { item(it.label, "mode:${it.name}") }, columns = 2)

    fun setup1() = MenuDef(
        "setup1", "SETUP", listOf(
            item("MthIO", "setup:mathio"), item("LineIO", "setup:lineio"), item("Deg", "setup:deg"),
            item("Rad", "setup:rad"), item("Gra", "setup:gra"), item("Fix", "sub:fixdig"),
            item("Sci", "sub:scidig"), item("Norm", "sub:norm")
        ), columns = 2, next = "setup2"
    )

    fun setup2() = MenuDef(
        "setup2", "SETUP", listOf(
            item("ab/c", "setup:abc"), item("d/c", "setup:dc"), item("CMPLX", "sub:cmplxfmt"),
            item("STAT", "sub:statfmt"), item("Disp", "sub:disp"), item("ENG Symbol", "sub:engsym"),
            item("Table", "sub:tablefmt")
        ), columns = 2, prev = "setup1"
    )

    fun fixDigits() = MenuDef("fixdig", "Fix 0~9?", digitLabels.mapIndexed { i, l -> item(l, "setup:fix:${(i + 1) % 10}") }, columns = 2)
    fun sciDigits() = MenuDef(
        "scidig", "Sci 1~10?",
        digitLabels.mapIndexed { i, l -> item(if (i == 9) "10" else l, "setup:sci:${(i + 1) % 10}") }, columns = 2
    )

    fun norm() = MenuDef("norm", "Norm 1~2?", listOf(item("Norm 1", "setup:norm:1"), item("Norm 2", "setup:norm:2")), columns = 2)
    fun cmplxFmt() = MenuDef("cmplxfmt", "CMPLX", listOf(item("a+bi", "setup:rect"), item("r∠θ", "setup:polar")), columns = 2)
    fun statFmt() = MenuDef("statfmt", "STAT", listOf(item("ON", "setup:freqon"), item("OFF", "setup:freqoff")), columns = 2)
    fun disp() = MenuDef("disp", "Disp", listOf(item("Dot", "setup:dot"), item("Comma", "setup:comma")), columns = 2)
    fun engSym() = MenuDef("engsym", "ENG Symbol", listOf(item("ON", "setup:engon"), item("OFF", "setup:engoff")), columns = 2)
    fun tableFmt() = MenuDef("tablefmt", "Table", listOf(item("f(x)", "setup:tablef"), item("f(x),g(x)", "setup:tableg")), columns = 2)

    fun clr() = MenuDef("clr", "CLR", listOf(item("Setup", "clr:setup"), item("Memory", "clr:mem"), item("All", "clr:all")))

    fun hyp() = MenuDef(
        "hyp", "hyp", listOf(
            ins("sinh", "sinh("), ins("cosh", "cosh("), ins("tanh", "tanh("),
            ins("sinh⁻¹", "asinh(", "sinh⁻¹("), ins("cosh⁻¹", "acosh(", "cosh⁻¹("), ins("tanh⁻¹", "atanh(", "tanh⁻¹(")
        ), columns = 2
    )

    fun cmplx() = MenuDef(
        "cmplx", "CMPLX", listOf(
            ins("Arg", "Arg("), ins("Conjg", "Conjg("), ins("ReP", "ReP("), ins("ImP", "ImP("),
            ins("▶r∠θ", "▶r∠θ"), ins("▶a+bi", "▶a+bi")
        ), columns = 2
    )

    fun base() = MenuDef(
        "base", "BASE", listOf(
            ins("and", " and "), ins("or", " or "), ins("xor", " xor "), ins("xnor", " xnor "),
            ins("Not", "Not("), ins("Neg", "Neg(")
        ), columns = 2
    )

    fun optn() = MenuDef(
        "optn", "OPTN", listOf(
            ins("Pol(", "Pol("), ins("Rec(", "Rec("), ins("GCD(", "GCD("), ins("LCM(", "LCM("),
            ins("Int(", "Int("), ins("Intg(", "Intg("), ins("Rnd(", "Rnd("), ins("Abs(", "Abs("),
            ins("Ran#", "Ran#"), ins("RanInt#(", "RanInt#(")
        ), columns = 2
    )

    fun drg() = MenuDef("drg", "DRG▶", listOf(ins("°", "°"), ins("ʳ", "ʳ", "r"), ins("ᵍ", "ᵍ", "g")))

    fun statType() = MenuDef(
        "stattype", "STAT",
        StatType.values().map { item(it.label, "stat:type:${it.name}") }, columns = 2
    )

    fun statMenu(type: StatType): MenuDef {
        val items = ArrayList<MenuItem>()
        items += item("Type", "statm:type")
        items += item("Data", "statm:data")
        items += item("Sum", "statm:sum")
        items += item("Var", "statm:var")
        items += item("MinMax", "statm:minmax")
        if (type.twoVar) items += item("Reg", "statm:reg")
        items += item("Distr", "statm:distr")
        return MenuDef("statm", "STAT", items, columns = 2)
    }

    private fun sv(internal: String, label: String) = ins(label, internal, label)

    fun statSub(kind: String, type: StatType): MenuDef {
        val two = type.twoVar
        val items = when (kind) {
            "sum" -> buildList {
                add(sv("@sumx2", "Σx²")); add(sv("@sumx", "Σx"))
                if (two) { add(sv("@sumy2", "Σy²")); add(sv("@sumy", "Σy")); add(sv("@sumxy", "Σxy")) }
            }
            "var" -> buildList {
                add(sv("@n", "n")); add(sv("@xbar", "x̄")); add(sv("@sigx", "σx")); add(sv("@sx", "sx"))
                if (two) { add(sv("@ybar", "ȳ")); add(sv("@sigy", "σy")); add(sv("@sy", "sy")) }
            }
            "minmax" -> buildList {
                add(sv("@minx", "minX")); add(sv("@maxx", "maxX"))
                if (!two) { add(sv("@q1", "Q1")); add(sv("@med", "Med")); add(sv("@q3", "Q3")) }
                else { add(sv("@miny", "minY")); add(sv("@maxy", "maxY")) }
            }
            "reg" -> buildList {
                add(sv("@a", "A")); add(sv("@b", "B"))
                if (type == StatType.QUAD) add(sv("@c", "C"))
                add(sv("@r", "r"))
                add(ins("x̂", "@xhat(", "x̂(")); add(ins("ŷ", "@yhat(", "ŷ("))
            }
            else -> listOf(ins("P(", "@P(", "P("), ins("Q(", "@Q(", "Q("), ins("R(", "@R(", "R("), ins("▶t", "@t(", "▶t("))
        }
        return MenuDef("stat$kind", kind.replaceFirstChar { it.uppercase() }, items, columns = 2)
    }

    fun eqnType() = MenuDef(
        "eqn", "EQUATION", listOf(
            item("anX+bnY=cn", "eqn:lin2"), item("anX+bnY+cnZ=dn", "eqn:lin3"),
            item("aX²+bX+c=0", "eqn:p2"), item("aX³+bX²+cX+d=0", "eqn:p3"), item("aX⁴+…+e=0", "eqn:p4")
        )
    )

    fun matrixMenu() = MenuDef(
        "matm", "MATRIX", listOf(
            item("Dim", "matm:dim"), item("Data", "matm:data"),
            ins("MatA", "MatA"), ins("MatB", "MatB"), ins("MatC", "MatC"), ins("MatAns", "MatAns"),
            ins("Det(", "Det("), ins("Trn(", "Trn("), ins("Ide(", "Ide("), ins("RanMat(", "RanMat(")
        ), columns = 2
    )

    fun matrixPick(purpose: String) = MenuDef(
        "matpick:$purpose", "Mat?", listOf("A", "B", "C").map { item("Mat$it", "matpick:$purpose:$it") }
    )

    fun vectorMenu() = MenuDef(
        "vctm", "VECTOR", listOf(
            item("Dim", "vctm:dim"), item("Data", "vctm:data"),
            ins("VctA", "VctA"), ins("VctB", "VctB"), ins("VctC", "VctC"), ins("VctAns", "VctAns"),
            ins("Dot(", "Dot("), ins("Angle(", "Angle("), ins("UnitV(", "UnitV(")
        ), columns = 2
    )

    fun vectorPick(purpose: String) = MenuDef(
        "vctpick:$purpose", "Vct?", listOf("A", "B", "C").map { item("Vct$it", "vctpick:$purpose:$it") }
    )

    fun constMenu() = MenuDef(
        "const", "CONST [01-40]",
        Constants.CONSTS.mapIndexed { i, c ->
            MenuItem("%02d %s: %s".format(i + 1, c.symbol, c.name), tokens = tk("CONST(${i + 1})", c.symbol))
        }, digitEntry = true
    )

    fun convMenu() = MenuDef(
        "conv", "CONV [01-40]",
        Constants.CONVERSIONS.mapIndexed { i, c ->
            MenuItem("%02d %s".format(i + 1, c.label), tokens = tk("CONV(${i + 1})", c.label))
        }, digitEntry = true
    )

    fun byId(id: String, type: StatType): MenuDef? = when {
        id == "setup1" -> setup1()
        id == "setup2" -> setup2()
        else -> null
    }
}
