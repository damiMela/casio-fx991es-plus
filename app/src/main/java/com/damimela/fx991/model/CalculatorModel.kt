package com.damimela.fx991.model

import com.damimela.fx991.engine.AngleUnit
import com.damimela.fx991.engine.CalcError
import com.damimela.fx991.engine.Context
import com.damimela.fx991.engine.Cplx
import com.damimela.fx991.engine.DisplaySettings
import com.damimela.fx991.engine.Engine
import com.damimela.fx991.engine.ErrorKind
import com.damimela.fx991.engine.Evaluator
import com.damimela.fx991.engine.Formatter
import com.damimela.fx991.engine.MatV
import com.damimela.fx991.engine.NumFormat
import com.damimela.fx991.engine.Ops
import com.damimela.fx991.engine.Poly
import com.damimela.fx991.engine.Real
import com.damimela.fx991.engine.Stat
import com.damimela.fx991.engine.StatType
import com.damimela.fx991.engine.TableGen
import com.damimela.fx991.engine.Value
import com.damimela.fx991.engine.VecV
import com.damimela.fx991.engine.isZeroValue
import java.math.BigDecimal
import java.math.MathContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The calculator state machine. Pure Kotlin (no Android dependencies): the UI sends [press] events and renders
 * [state]. All math is delegated to the engine package.
 */
class CalculatorModel(private val storage: Storage = MemoryStorage()) {
    private val ctx = Context()
    private var s = CalcState()
    private val flow = MutableStateFlow(s)
    val state: StateFlow<CalcState> get() = flow

    private var tableCells = listOf("", "", "1", "5", "1")

    init {
        s = Persistence.loadSettings(storage, s).copy(history = Persistence.loadHistory(storage))
        Persistence.loadMemory(storage, ctx)
        if (s.mode != CalcMode.COMP && s.mode != CalcMode.CMPLX && s.mode != CalcMode.BASE) s = s.copy(mode = CalcMode.COMP)
        syncCtx()
        publish()
    }

    // ------------------------------------------------------------------ public API

    fun press(key: Key) {
        s = s.copy(notice = null)
        when (key) {
            Key.SHIFT -> s = s.copy(shift = !s.shift, alpha = false)
            Key.ALPHA -> s = when {
                s.shift -> s.copy(shift = false, alphaLock = !s.alphaLock, alpha = false)
                s.alphaLock -> s.copy(alphaLock = false)
                else -> s.copy(alpha = !s.alpha)
            }
            else -> {
                val sh = s.shift
                val al = s.alphaActive
                s = s.copy(shift = false, alpha = false, alphaLock = if (key == Key.AC) false else s.alphaLock)
                syncCtx()
                dispatch(if (key == Key.ON) Key.AC else key, sh, al)
            }
        }
        publish()
    }

    fun setPrefs(p: AppPrefs) { s = s.copy(prefs = p); publish(); Persistence.saveSettings(storage, s) }
    fun consumeHelpRequest() { s = s.copy(helpRequested = false); publish() }
    fun snapshot(): CalcState = s

    // ------------------------------------------------------------------ plumbing

    private fun publish() {
        s = s.copy(memoryUsed = ctx.vars["M"]?.isZeroValue() == false, hasStat = ctx.stat != null)
        flow.value = s
    }

    private fun syncCtx() {
        ctx.angle = s.angle
        ctx.complex = s.mode == CalcMode.CMPLX
        ctx.baseMode = if (s.mode == CalcMode.BASE) s.baseNum else 0
    }

    private fun saveAll() {
        Persistence.saveSettings(storage, s)
        Persistence.saveMemory(storage, ctx)
        Persistence.saveHistory(storage, s.history)
    }

    private fun dispatch(key: Key, sh: Boolean, al: Boolean) {
        if (key == Key.MODE && s.screen !is Screen.Menu) {
            openMenu(if (sh) Menus.setup1() else Menus.mode(), back = s.screen)
            return
        }
        when (val scr = s.screen) {
            is Screen.Main -> if (s.error != null) errorKey(key) else mainKey(key, sh, al)
            is Screen.Menu -> menuKey(scr, key)
            is Screen.Grid -> gridKey(scr.g, key, sh, al)
            is Screen.Results -> resultsKey(scr, key)
            is Screen.Table -> tableKey(scr, key)
            is Screen.Confirm -> confirmKey(scr, key)
        }
    }

    private fun openMenu(def: MenuDef, back: Screen = Screen.Main, sel: Int = 0) {
        s = s.copy(screen = Screen.Menu(def, sel, "", back))
    }

    // ------------------------------------------------------------------ errors

    private fun setError(e: CalcError) {
        val idx = if (e.pos < 0) s.tokens.size else Toks.tokenAt(s.tokens, e.pos)
        s = s.copy(error = ErrorView(e.kind, idx.coerceIn(0, s.tokens.size)), result = null)
    }

    private inline fun guarded(onError: (CalcError) -> Unit = ::setError, block: () -> Unit) {
        try { block() }
        catch (e: CalcError) { onError(e) }
        catch (e: StackOverflowError) { onError(CalcError(ErrorKind.STACK)) }
        catch (e: ArithmeticException) { onError(CalcError(ErrorKind.MATH)) }
        catch (e: NumberFormatException) { onError(CalcError(ErrorKind.SYNTAX)) }
    }

    private fun noticeError(e: CalcError) { s = s.copy(notice = e.kind.title) }

    private fun errorKey(key: Key) {
        val err = s.error ?: return
        when (key) {
            Key.AC -> clearInput()
            Key.LEFT, Key.RIGHT -> s = s.copy(error = null, cursor = err.tokenIndex.coerceIn(0, s.tokens.size))
            else -> {}
        }
    }

    private fun clearInput() {
        s = s.copy(
            tokens = emptyList(), cursor = 0, result = null, error = null, calcSession = false, pending = null,
            historyPos = -1, replayText = null
        )
    }

    // ------------------------------------------------------------------ main screen

    private fun varKey(key: Key): String? = when (key) {
        Key.NEG -> "A"; Key.DMS -> "B"; Key.HYP -> "C"; Key.SIN -> "D"; Key.COS -> "E"; Key.TAN -> "F"
        Key.RPAR -> "X"; Key.LPAR -> "Y"; Key.MPLUS -> "M"
        else -> null
    }

    private fun mainKey(key: Key, sh: Boolean, al: Boolean) {
        s.pending?.let { p ->
            s = s.copy(pending = null)
            val v = varKey(key)
            if (v != null) {
                if (p == PendingMem.RCL) insertToks(listOf(InTok(v)))
                else storeTo(v)
            }
            if (v != null || key == Key.AC) return
        }
        when (key) {
            Key.AC -> clearInput()
            Key.EQ -> if (sh) insertToks(listOf(InTok("%"))) else evaluate()
            Key.DEL -> if (sh) s = s.copy(insertMode = !s.insertMode) else deleteBack()
            Key.LEFT -> moveCursor(-1)
            Key.RIGHT -> moveCursor(1)
            Key.UP -> historyStep(-1)
            Key.DOWN -> historyStep(1)
            Key.OPTN -> openMenu(Menus.optn())
            Key.CALC -> when { al -> insertToks(listOf(InTok("="))); sh -> startSolve(); else -> startCalc() }
            Key.RCL -> s = s.copy(pending = if (sh) PendingMem.STO else PendingMem.RCL)
            Key.ENG -> engKey(sh)
            Key.SD -> sdKey(sh)
            Key.MPLUS -> if (al) insertToks(listOf(InTok("M"))) else memoryAdd(if (sh) -1 else 1)
            Key.HYP -> if (al) insertToks(listOf(InTok("C"))) else if (s.mode == CalcMode.BASE) insertToks(listOf(InTok("C"))) else openMenu(Menus.hyp())
            Key.DMS -> dmsKey(sh, al)
            Key.ANS -> if (sh) openMenu(Menus.drg()) else insertToks(listOf(if (al) InTok("PreAns") else InTok("Ans")))
            Key.D1 -> if (sh && s.mode == CalcMode.STAT) openMenu(Menus.statMenu(s.statType)) else typeDigit(key)
            Key.D2 -> if (sh) openMenu(Menus.cmplx()) else typeDigit(key)
            Key.D3 -> if (sh && s.mode == CalcMode.BASE) openMenu(Menus.base()) else typeDigit(key)
            Key.D4 -> if (sh) openMenu(Menus.matrixMenu()) else typeDigit(key)
            Key.D5 -> if (sh) openMenu(Menus.vectorMenu()) else typeDigit(key)
            Key.D6 -> if (sh) s = s.copy(helpRequested = true) else typeDigit(key)
            Key.D7 -> if (sh) openMenu(Menus.constMenu()) else typeDigit(key)
            Key.D8 -> if (sh) openMenu(Menus.convMenu()) else typeDigit(key)
            Key.D9 -> if (sh) openMenu(Menus.clr()) else typeDigit(key)
            Key.D0 -> if (sh) insertToks(listOf(InTok("Ran#"))) else typeDigit(key)
            Key.SHIFT, Key.ALPHA, Key.MODE, Key.ON -> {}
            else -> {
                if (s.mode == CalcMode.BASE && baseKey(key, sh)) return
                keyInsertion(key, sh, al)?.let { insertToks(it.first, it.second) }
            }
        }
    }

    private fun typeDigit(key: Key) {
        val d = DIGITS.getValue(key)
        if (s.mode == CalcMode.BASE && d.digitToInt() >= s.baseNum) return
        insertToks(listOf(InTok(d.toString())))
    }

    /** BASE-N specific keys: base selectors and hex digits. Returns true when handled. */
    private fun baseKey(key: Key, sh: Boolean): Boolean {
        if (sh) return false
        val newBase = when (key) { Key.SQUARE -> 10; Key.POW -> 16; Key.LOG -> 2; Key.LN -> 8; else -> null }
        if (newBase != null) {
            s = s.copy(baseNum = newBase, result = s.result?.copy(outs = emptyList()))
            syncCtx(); saveAll()
            return true
        }
        val letter = varKey(key)?.takeIf { it in "ABCDEF" }
        if (letter != null) { if (s.baseNum == 16) insertToks(listOf(InTok(letter))); return true }
        return false
    }

    private fun engKey(sh: Boolean) {
        if (s.mode == CalcMode.CMPLX && !sh) { insertToks(listOf(InTok("i"))); return }
        val rv = s.result ?: return
        s = s.copy(result = rv.copy(eng = if (sh) (rv.eng ?: 1) - 1 else (rv.eng?.plus(1) ?: 0)))
    }

    private fun sdKey(sh: Boolean) {
        val rv = s.result ?: return
        s = s.copy(
            result = when {
                sh && rv.value is Cplx -> rv.copy(flipPolar = !rv.flipPolar, outs = rv.outs.filter { !it.startsWith("▶r") && !it.startsWith("▶a") })
                sh -> rv.copy(flipMixed = !rv.flipMixed)
                else -> rv.copy(flipExact = !rv.flipExact, eng = null)
            }
        )
    }

    private fun dmsKey(sh: Boolean, al: Boolean) {
        when {
            al -> insertToks(listOf(InTok("B")))
            s.mode == CalcMode.BASE -> insertToks(listOf(InTok("B")))
            sh -> insertToks(listOf(InTok("▶DMS")))
            else -> {
                prepareInput(null)
                var i = s.cursor - 1
                var last: String? = null
                while (i >= 0) {
                    val t = s.tokens[i]
                    if (t.text in DMS_SYMBOLS) { last = t.text; break }
                    if (t.role == Role.NORMAL && (t.text.length == 1 && (t.text[0].isDigit() || t.text[0] == '.'))) i-- else break
                }
                val next = when (last) { null -> "°"; "°" -> "′"; "′" -> "″"; else -> "°" }
                insertToks(listOf(InTok(next)))
            }
        }
    }

    /** Returns the tokens inserted by a plain key press and the cursor offset after insertion. */
    private fun keyInsertion(key: Key, sh: Boolean, al: Boolean): Pair<List<InTok>, Int>? {
        fun one(text: String, display: String = text) = listOf(InTok(text, display)) to 1
        return when (key) {
            Key.DOT -> if (sh) one("RanInt#(") else one(".")
            Key.EXP10 -> when { sh -> one("π"); al -> one("e"); else -> one("ᴇ") }
            Key.ADD -> if (sh) one("Pol(") else one("+")
            Key.SUB -> if (sh) one("Rec(") else one("−")
            Key.MUL -> if (sh) one("nPr") else one("×")
            Key.DIV -> if (sh) one("nCr") else one("÷")
            Key.LPAR -> when { sh -> one("Abs("); al -> one("Y"); else -> one("(") }
            Key.RPAR -> when { sh -> one(","); al -> one("X"); else -> one(")") }
            Key.NEG -> when { al -> one("A"); sh -> one("∠"); else -> one("-") }
            Key.SIN -> when { al -> one("D"); sh -> one("asin(", "sin⁻¹("); else -> one("sin(") }
            Key.COS -> when { al -> one("E"); sh -> one("acos(", "cos⁻¹("); else -> one("cos(") }
            Key.TAN -> when { al -> one("F"); sh -> one("atan(", "tan⁻¹("); else -> one("tan(") }
            Key.LOG -> if (sh) (listOf(InTok("1"), InTok("0")) + Toks.SUP) to 3 else one("log(")
            Key.LN -> if (sh) (listOf(InTok("e")) + Toks.SUP) to 2 else one("ln(")
            Key.LOGA -> if (sh) one("Σ(") else Toks.LOGB to 1
            Key.INV -> if (sh) one("!") else one("⁻¹")
            Key.SQUARE -> if (sh) one("³") else one("²")
            Key.POW -> if (sh) Toks.ROOT to 1 else Toks.SUP to 1
            Key.SQRT -> if (sh) one("∛(") else Toks.SQRT to 1
            Key.FRAC -> if (sh) Toks.MIXED to 1 else Toks.FRAC to 1
            Key.INTEGRAL -> if (sh) one("d/dx(") else one("∫(")
            else -> null
        }
    }

    private val postfixStarts = setOf("+", "−", "×", "÷", "²", "³", "⁻¹", "!", "%", "nPr", "nCr", "∠", "°", "ʳ", "ᵍ", " and ", " or ", " xor ", " xnor ")

    /** Prepares the input line for typing: after a result, operators continue from Ans, anything else starts afresh. */
    private fun prepareInput(first: InTok?) {
        if (s.result != null || s.replayText != null) {
            val cont = s.result != null && first != null && (first.text in postfixStarts || first.role == Role.SUP_OPEN)
            val base = if (cont) listOf(InTok("Ans")) else emptyList()
            s = s.copy(tokens = base, cursor = base.size, result = null, replayText = null, historyPos = -1, calcSession = false)
        } else if (s.historyPos != -1) s = s.copy(historyPos = -1)
    }

    private fun insertToks(ins: List<InTok>, cursorOffset: Int = ins.size) {
        prepareInput(ins.firstOrNull())
        val cur = s.cursor.coerceIn(0, s.tokens.size)
        val replace = !s.insertMode && ins.size == 1 && cur < s.tokens.size && s.tokens[cur].role == Role.NORMAL
        val base = if (replace) s.tokens.subList(0, cur) + s.tokens.subList(cur + 1, s.tokens.size) else s.tokens
        s = s.copy(tokens = Toks.insert(base, cur, ins), cursor = cur + cursorOffset)
    }

    private fun deleteBack() {
        if (s.result != null || s.replayText != null) {
            s = s.copy(result = null, replayText = null, cursor = s.tokens.size, calcSession = false)
        }
        val c = s.cursor
        if (c <= 0 || c > s.tokens.size) return
        val t = s.tokens[c - 1]
        val toks = s.tokens.toMutableList()
        when {
            t.role == Role.CLOSE || t.isSeparator -> s = s.copy(cursor = c - 1)
            t.isOpener -> {
                val idx = (Toks.matching(toks, c - 1) + (c - 1)).sortedDescending()
                idx.forEach { toks.removeAt(it) }
                s = s.copy(tokens = toks, cursor = c - 1, historyPos = -1)
            }
            else -> { toks.removeAt(c - 1); s = s.copy(tokens = toks, cursor = c - 1, historyPos = -1) }
        }
    }

    private fun moveCursor(d: Int) {
        if (s.result != null || s.replayText != null) {
            s = s.copy(result = null, replayText = null, cursor = if (d < 0) s.tokens.size else 0, calcSession = false)
            return
        }
        s = s.copy(cursor = (s.cursor + d).coerceIn(0, s.tokens.size))
    }

    private fun historyStep(d: Int) {
        val h = s.history
        if (h.isEmpty()) return
        val pos = s.historyPos
        val np = when {
            d < 0 -> if (pos == -1) h.lastIndex else maxOf(0, pos - 1)
            pos == -1 -> return
            pos >= h.lastIndex -> -1
            else -> pos + 1
        }
        s = if (np == -1) s.copy(tokens = emptyList(), cursor = 0, historyPos = -1, result = null, replayText = null, calcSession = false)
        else s.copy(tokens = h[np].tokens, cursor = h[np].tokens.size, historyPos = np, result = null, replayText = h[np].result, calcSession = false)
    }

    // ------------------------------------------------------------------ evaluation

    private fun evaluate() {
        if (s.tokens.isEmpty()) return
        val (src, _) = Toks.serialize(s.tokens)
        guarded {
            val res = Engine.evaluate(src, ctx)
            finishResult(ResultView(res.value, res.extras, res.outs))
        }
    }

    private fun finishResult(rv: ResultView, calcSession: Boolean = false) {
        s = s.copy(result = rv, error = null, cursor = s.tokens.size, calcSession = calcSession, historyPos = -1, replayText = null)
        val text = Render.resultText(s)
        s = s.copy(history = (s.history + HistoryEntry(s.tokens, text)).takeLast(Persistence.MAX_HISTORY))
        saveAll()
    }

    private fun storeTo(v: String) {
        val tokens = if (s.tokens.isEmpty() || s.result != null) listOf(InTok("Ans")) else s.tokens
        s = s.copy(tokens = tokens + InTok("→") + InTok(v), cursor = tokens.size + 2, result = null, replayText = null)
        evaluate()
    }

    private fun memoryAdd(sign: Int) {
        if (s.tokens.isNotEmpty() && s.result == null) evaluate()
        if (s.error != null) return
        val cur = if (s.result != null) ctx.ans else if (s.tokens.isEmpty()) ctx.ans else return
        val m = ctx.vars["M"] ?: Real.ZERO
        guarded {
            ctx.vars["M"] = if (sign > 0) Ops.add(m, cur) else Ops.sub(m, cur)
            if (s.result == null) {
                s = s.copy(tokens = listOf(InTok("Ans")), cursor = 1)
                finishResult(ResultView(cur))
            }
            saveAll()
        }
    }

    // ------------------------------------------------------------------ CALC / SOLVE

    private fun usedVariables(): List<String> =
        s.tokens.map { it.text }.filter { it in VARS }.distinct()

    private fun startCalc() {
        if (s.tokens.isEmpty()) return
        val vars = usedVariables()
        if (s.tokens.any { it.text == "=" }) { setError(CalcError(ErrorKind.SYNTAX)); return }
        if (vars.isEmpty()) { evaluate(); return }
        val cells = vars.map { varSrc(it) }
        s = s.copy(
            screen = Screen.Grid(
                GridState(GridKind.CALC_VARS, "CALC", vars.size, 1, cells, rowLabels = vars.map { "$it=" })
            )
        )
    }

    private fun startSolve() {
        if (s.tokens.none { it.text == "=" }) { setError(CalcError(ErrorKind.SYNTAX)); return }
        s = s.copy(
            screen = Screen.Grid(
                GridState(GridKind.SOLVE_GUESS, "SOLVE", 1, 1, listOf(varSrc("X")), rowLabels = listOf("Solve for X  X=")
                )
            )
        )
    }

    private fun varSrc(name: String) = valueSrc(ctx.vars[name] ?: Real.ZERO)

    // ------------------------------------------------------------------ menus

    private fun menuKey(scr: Screen.Menu, key: Key) {
        val def = scr.def
        val n = def.items.size
        fun sel(i: Int) = s.copy(screen = scr.copy(sel = i.coerceIn(0, maxOf(0, n - 1)), digits = ""))
        when (key) {
            Key.AC, Key.MODE -> s = s.copy(screen = scr.back)
            Key.D0, Key.D1, Key.D2, Key.D3, Key.D4, Key.D5, Key.D6, Key.D7, Key.D8, Key.D9 -> {
                val d = DIGITS.getValue(key)
                if (def.digitEntry) {
                    val acc = scr.digits + d
                    if (acc.length >= 2) {
                        val idx = acc.toInt() - 1
                        if (idx in 0 until n) chooseItem(scr, idx) else s = s.copy(screen = scr.copy(digits = ""))
                    } else s = s.copy(screen = scr.copy(digits = acc, sel = (acc.toInt() - 1).coerceIn(0, n - 1)))
                } else {
                    val idx = if (d == '0') 9 else d - '1'
                    if (idx < n) chooseItem(scr, idx)
                }
            }
            Key.UP -> {
                val t = scr.sel - def.columns
                if (t < 0 && def.prev != null) openMenu(menuById(def.prev), scr.back)
                else s = sel(t)
            }
            Key.DOWN -> {
                val t = scr.sel + def.columns
                if (t >= n && def.next != null) openMenu(menuById(def.next), scr.back)
                else s = sel(t)
            }
            Key.LEFT -> s = sel(scr.sel - 1)
            Key.RIGHT -> s = sel(scr.sel + 1)
            Key.EQ -> chooseItem(scr, scr.sel)
            else -> {}
        }
    }

    private fun menuById(id: String): MenuDef = if (id == "setup2") Menus.setup2() else Menus.setup1()

    private fun chooseItem(scr: Screen.Menu, idx: Int) {
        val item = scr.def.items.getOrNull(idx) ?: return
        val tokens = item.tokens
        if (tokens != null) {
            s = s.copy(screen = scr.back.takeIf { it is Screen.Main } ?: Screen.Main)
            insertToks(tokens)
            return
        }
        s = s.copy(screen = scr.back)
        menuAction(item.id, scr.back)
    }

    private fun applySetup(f: (CalcState) -> CalcState) { s = f(s).copy(screen = Screen.Main); syncCtx(); saveAll() }

    private fun menuAction(id: String, back: Screen) {
        val statType = s.statType
        when {
            id.startsWith("mode:") -> setMode(CalcMode.valueOf(id.removePrefix("mode:")))
            id == "sub:fixdig" -> openMenu(Menus.fixDigits())
            id == "sub:scidig" -> openMenu(Menus.sciDigits())
            id == "sub:norm" -> openMenu(Menus.norm())
            id == "sub:cmplxfmt" -> openMenu(Menus.cmplxFmt())
            id == "sub:statfmt" -> openMenu(Menus.statFmt())
            id == "sub:disp" -> openMenu(Menus.disp())
            id == "sub:engsym" -> openMenu(Menus.engSym())
            id == "sub:tablefmt" -> openMenu(Menus.tableFmt())
            id.startsWith("setup:fix:") -> applySetup { it.copy(display = it.display.copy(format = NumFormat.FIX, digits = id.last().digitToInt())) }
            id.startsWith("setup:sci:") -> applySetup { it.copy(display = it.display.copy(format = NumFormat.SCI, digits = id.last().digitToInt())) }
            id == "setup:norm:1" -> applySetup { it.copy(display = it.display.copy(format = NumFormat.NORM1)) }
            id == "setup:norm:2" -> applySetup { it.copy(display = it.display.copy(format = NumFormat.NORM2)) }
            id == "setup:mathio" -> applySetup { it.copy(display = it.display.copy(mathIO = true)) }
            id == "setup:lineio" -> applySetup { it.copy(display = it.display.copy(mathIO = false)) }
            id == "setup:deg" -> applySetup { it.copy(angle = AngleUnit.DEG) }
            id == "setup:rad" -> applySetup { it.copy(angle = AngleUnit.RAD) }
            id == "setup:gra" -> applySetup { it.copy(angle = AngleUnit.GRA) }
            id == "setup:abc" -> applySetup { it.copy(display = it.display.copy(mixedFractions = true)) }
            id == "setup:dc" -> applySetup { it.copy(display = it.display.copy(mixedFractions = false)) }
            id == "setup:rect" -> applySetup { it.copy(display = it.display.copy(polarComplex = false)) }
            id == "setup:polar" -> applySetup { it.copy(display = it.display.copy(polarComplex = true)) }
            id == "setup:freqon" -> applySetup { it.copy(statFreq = true) }
            id == "setup:freqoff" -> applySetup { it.copy(statFreq = false) }
            id == "setup:dot" -> applySetup { it.copy(display = it.display.copy(decimalComma = false)) }
            id == "setup:comma" -> applySetup { it.copy(display = it.display.copy(decimalComma = true)) }
            id == "setup:engon" -> applySetup { it.copy(display = it.display.copy(engSymbols = true)) }
            id == "setup:engoff" -> applySetup { it.copy(display = it.display.copy(engSymbols = false)) }
            id == "setup:tablef" -> applySetup { it.copy(tableG = false) }
            id == "setup:tableg" -> applySetup { it.copy(tableG = true) }
            id == "clr:setup" -> s = s.copy(screen = Screen.Confirm("Reset setup?", "clr:setup"))
            id == "clr:mem" -> s = s.copy(screen = Screen.Confirm("Clear memory?", "clr:mem"))
            id == "clr:all" -> s = s.copy(screen = Screen.Confirm("Reset all?", "clr:all"))
            id.startsWith("stat:type:") -> {
                val t = StatType.valueOf(id.removePrefix("stat:type:"))
                s = s.copy(statType = t, statCells = emptyList())
                ctx.stat = null
                openStatEditor()
            }
            id == "statm:type" -> openMenu(Menus.statType())
            id == "statm:data" -> openStatEditor()
            id.startsWith("statm:") -> openMenu(Menus.statSub(id.removePrefix("statm:"), statType))
            id.startsWith("eqn:") -> openEqnGrid(id.removePrefix("eqn:"))
            id == "matm:dim" -> openMenu(Menus.matrixPick("dim"))
            id == "matm:data" -> openMenu(Menus.matrixPick("data"))
            id.startsWith("matpick:") -> {
                val (_, purpose, name) = id.split(":")
                if (purpose == "data" && ctx.mats["Mat$name"] != null) openMatGrid(name) else openDimGrid(GridKind.DIM_MAT, name)
            }
            id == "vctm:dim" -> openMenu(Menus.vectorPick("dim"))
            id == "vctm:data" -> openMenu(Menus.vectorPick("data"))
            id.startsWith("vctpick:") -> {
                val (_, purpose, name) = id.split(":")
                if (purpose == "data" && ctx.vecs["Vct$name"] != null) openVecGrid(name) else openDimGrid(GridKind.DIM_VEC, name)
            }
        }
    }

    private fun setMode(m: CalcMode) {
        s = s.copy(
            mode = m, tokens = emptyList(), cursor = 0, result = null, error = null, screen = Screen.Main,
            calcSession = false, historyPos = -1, replayText = null, pending = null,
            baseNum = if (m == CalcMode.BASE) 10 else s.baseNum
        )
        syncCtx()
        when (m) {
            CalcMode.STAT -> openMenu(Menus.statType())
            CalcMode.EQN -> openMenu(Menus.eqnType())
            CalcMode.MATRIX -> openMenu(Menus.matrixPick("mode"))
            CalcMode.VECTOR -> openMenu(Menus.vectorPick("mode"))
            CalcMode.TABLE -> openTableGrid()
            else -> {}
        }
        saveAll()
    }

    private fun confirmKey(scr: Screen.Confirm, key: Key) {
        when (key) {
            Key.EQ -> {
                when (scr.action) {
                    "clr:setup" -> s = s.copy(angle = AngleUnit.DEG, display = DisplaySettings(), statFreq = false, tableG = false)
                    "clr:mem" -> clearMemory()
                    "clr:all" -> {
                        clearMemory()
                        s = s.copy(angle = AngleUnit.DEG, display = DisplaySettings(), statFreq = false, tableG = false, history = emptyList())
                    }
                }
                s = s.copy(screen = Screen.Main, notice = "Reset complete")
                syncCtx(); clearInput(); saveAll()
            }
            Key.AC -> s = s.copy(screen = Screen.Main)
            else -> {}
        }
    }

    private fun clearMemory() {
        ctx.vars.clear(); ctx.mats.clear(); ctx.vecs.clear(); ctx.stat = null
        ctx.ans = Real.ZERO; ctx.preAns = Real.ZERO
        s = s.copy(statCells = emptyList())
    }

    // ------------------------------------------------------------------ grid editor

    private fun cellInsertion(key: Key, sh: Boolean, al: Boolean): String? {
        val digit = DIGITS[key]
        if (digit != null && !sh) return digit.toString()
        val toks: List<InTok> = when (key) {
            Key.D0 -> listOf(InTok("Ran#"))
            Key.ANS -> listOf(InTok(if (al) "PreAns" else "Ans"))
            Key.RPAR, Key.LPAR, Key.NEG, Key.SIN, Key.COS, Key.TAN -> {
                if (al) listOfNotNull(varKey(key)?.let { InTok(it) }) else keyInsertion(key, sh, al)?.first.orEmpty()
            }
            Key.HYP -> if (al) listOf(InTok("C")) else emptyList()
            Key.MPLUS -> if (al) listOf(InTok("M")) else emptyList()
            else -> keyInsertion(key, sh, al)?.first.orEmpty()
        }
        if (toks.isEmpty()) return null
        val sb = StringBuilder()
        for (t in toks) {
            when (t.role) {
                Role.CLOSE -> break
                Role.FRAC_OPEN -> {}
                Role.FRAC_SEP -> sb.append("÷")
                else -> sb.append(t.text)
            }
        }
        return sb.toString()
    }

    private fun setGrid(g: GridState) { s = s.copy(screen = Screen.Grid(g)) }

    private fun gridKey(g: GridState, key: Key, sh: Boolean, al: Boolean) {
        when (key) {
            Key.AC -> leaveGrid(g)
            Key.UP -> if (g.r > 0) setGrid(g.copy(r = g.r - 1, fresh = true)) else Unit
            Key.DOWN -> gridDown(g)
            Key.LEFT -> if (g.c > 0) setGrid(g.copy(c = g.c - 1, fresh = true)) else if (g.cols > 1 && g.r > 0) setGrid(g.copy(r = g.r - 1, c = g.cols - 1, fresh = true))
            Key.RIGHT -> if (g.c < g.cols - 1) setGrid(g.copy(c = g.c + 1, fresh = true)) else if (g.r < g.rows - 1) setGrid(g.copy(r = g.r + 1, c = 0, fresh = true))
            Key.DEL -> {
                val t = g.current
                setGrid(g.with(g.r, g.c, if (g.fresh && sh) "" else t.dropLast(1)).copy(fresh = false))
            }
            Key.EQ -> gridEnter(g)
            else -> cellInsertion(key, sh, al)?.let { ins ->
                val base = if (g.fresh) "" else g.current
                setGrid(g.with(g.r, g.c, base + ins).copy(fresh = false))
            }
        }
    }

    private fun gridDown(g: GridState) {
        if (g.r < g.rows - 1) setGrid(g.copy(r = g.r + 1, fresh = true))
        else if (g.kind == GridKind.STAT) {
            val limit = statLimit()
            if (g.rows < limit && g.cells.drop((g.rows - 1) * g.cols).any { it.isNotBlank() }) {
                setGrid(g.copy(rows = g.rows + 1, cells = g.cells + List(g.cols) { "" }, r = g.r + 1, fresh = true))
            }
        }
    }

    private fun gridEnter(g: GridState) {
        guarded(onError = ::noticeError) {
            val blankOk = g.kind == GridKind.STAT || (g.kind == GridKind.TABLE_PARAMS && s.tableG && g.r == 1)
            if (g.current.isNotBlank()) evalCell(g.current) else if (!blankOk) throw CalcError(ErrorKind.SYNTAX)
            val last = g.r == g.rows - 1 && g.c == g.cols - 1
            when {
                g.kind == GridKind.STAT -> gridDown(g)
                last && g.kind != GridKind.MAT && g.kind != GridKind.VEC -> submitGrid(g)
                g.c < g.cols - 1 -> setGrid(g.copy(c = g.c + 1, fresh = true))
                g.r < g.rows - 1 -> setGrid(g.copy(r = g.r + 1, c = 0, fresh = true))
                else -> {}
            }
        }
    }

    private fun tableFixedRows() = if (s.tableG) 2 else 1

    private fun evalCell(text: String): Value {
        syncCtx()
        return Evaluator(ctx).run(text).value
    }

    private fun cellReal(text: String): Real = evalCell(text) as? Real ?: throw CalcError(ErrorKind.MATH)

    private fun leaveGrid(g: GridState) {
        when (g.kind) {
            GridKind.MAT -> {
                val vals = g.cells.map { runCatching { evalCell(it) }.getOrDefault(Real.ZERO) }
                ctx.mats["Mat${g.tag}"] = MatV(g.rows, g.cols, vals)
                s = s.copy(screen = Screen.Main); saveAll()
            }
            GridKind.VEC -> {
                val vals = g.cells.map { runCatching { evalCell(it) }.getOrDefault(Real.ZERO) }
                ctx.vecs["Vct${g.tag}"] = VecV(vals)
                s = s.copy(screen = Screen.Main)
            }
            GridKind.STAT -> {
                storeStatCells(g)
                computeStat()
                s = s.copy(screen = Screen.Main); clearInput()
            }
            GridKind.EQN_LINEAR, GridKind.EQN_POLY -> openMenu(Menus.eqnType())
            GridKind.TABLE_PARAMS -> { tableCells = g.cells; setGrid(g.copy(r = 0, c = 0, fresh = true)) }
            else -> s = s.copy(screen = Screen.Main)
        }
    }

    private fun submitGrid(g: GridState) {
        when (g.kind) {
            GridKind.EQN_LINEAR -> {
                val rows = (0 until g.rows).map { r -> (0 until g.cols).map { c -> cellReal(g.cell(r, c)) } }
                val sol = Poly.linear(rows)
                val names = listOf("X", "Y", "Z", "T")
                s = s.copy(screen = Screen.Results(g.title, sol.mapIndexed { i, v -> ResultPage(names[i], v) }, 0, false, g))
            }
            GridKind.EQN_POLY -> {
                val coefs = g.cells.map { cellReal(it) }
                val res = Poly.solve(coefs)
                val pages = ArrayList<ResultPage>()
                res.roots.forEachIndexed { i, v -> pages += ResultPage("x${i + 1}", v) }
                res.discriminant?.let { d ->
                    val note = when { d.d > 0 -> "Δ>0: two real roots"; d.d < 0 -> "Δ<0: two complex roots"; else -> "Δ=0: double root" }
                    pages += ResultPage("Δ", d, note)
                }
                s = s.copy(screen = Screen.Results(g.title, pages, 0, false, g))
            }
            GridKind.CALC_VARS -> {
                val assign = g.rowLabels.indices.associate { i -> g.rowLabels[i].dropLast(1) to evalCell(g.cell(i, 0)) }
                ctx.vars.putAll(assign)
                val (src, _) = Toks.serialize(s.tokens)
                s = s.copy(screen = Screen.Main)
                guarded {
                    val res = Engine.evaluate(src, ctx)
                    finishResult(ResultView(res.value, res.extras, res.outs), calcSession = true)
                }
            }
            GridKind.SOLVE_GUESS -> {
                val guess = cellReal(g.cell(0, 0)).d
                val (src, _) = Toks.serialize(s.tokens)
                s = s.copy(screen = Screen.Main)
                guarded {
                    val r = Engine.solve(src, ctx, guess)
                    ctx.vars["X"] = Real.of(r.x)
                    ctx.preAns = ctx.ans; ctx.ans = Real.of(r.x)
                    finishResult(ResultView(Real.of(r.x), listOf("L-R" to Real.of(r.diff)), emptyList(), label = "X="))
                }
            }
            GridKind.TABLE_PARAMS -> {
                tableCells = g.cells
                val hasG = s.tableG
                val off = if (hasG) 2 else 1
                val f = g.cell(0, 0)
                val gx = if (hasG) g.cell(1, 0) else null
                if (f.isBlank()) throw CalcError(ErrorKind.SYNTAX)
                val start = cellReal(g.cell(off, 0)).d
                val end = cellReal(g.cell(off + 1, 0)).d
                val step = cellReal(g.cell(off + 2, 0)).d
                val rows = TableGen.generate(f, gx?.takeIf { it.isNotBlank() }, start, end, step, ctx)
                s = s.copy(screen = Screen.Table(rows, hasG, 0, g))
            }
            GridKind.DIM_MAT -> {
                val m = cellReal(g.cell(0, 0)); val n = cellReal(g.cell(1, 0))
                val rr = intIn(m, 1, 4); val cc = intIn(n, 1, 4)
                val old = ctx.mats["Mat${g.tag}"]
                ctx.mats["Mat${g.tag}"] = MatV.build(rr, cc) { r, c -> if (old != null && r < old.rows && c < old.cols) old[r, c] else Real.ZERO }
                openMatGrid(g.tag)
            }
            GridKind.DIM_VEC -> {
                val n = intIn(cellReal(g.cell(0, 0)), 2, 3)
                val old = ctx.vecs["Vct${g.tag}"]
                ctx.vecs["Vct${g.tag}"] = VecV((0 until n).map { i -> old?.items?.getOrNull(i) ?: Real.ZERO })
                openVecGrid(g.tag)
            }
            else -> {}
        }
    }

    private fun intIn(v: Real, lo: Int, hi: Int): Int {
        if (!v.isInteger || v.d < lo || v.d > hi) throw CalcError(ErrorKind.ARGUMENT)
        return v.d.toInt()
    }

    private fun openDimGrid(kind: GridKind, name: String) {
        if (kind == GridKind.DIM_MAT) {
            val m = ctx.mats["Mat$name"]
            setGrid(GridState(kind, "Mat$name Dim", 2, 1, listOf((m?.rows ?: 2).toString(), (m?.cols ?: 2).toString()),
                rowLabels = listOf("m (rows, 1-4) =", "n (cols, 1-4) ="), tag = name))
        } else {
            val v = ctx.vecs["Vct$name"]
            setGrid(GridState(kind, "Vct$name Dim", 1, 1, listOf((v?.size ?: 3).toString()), rowLabels = listOf("Dim (2-3) ="), tag = name))
        }
    }

    private fun openMatGrid(name: String) {
        val m = ctx.mats["Mat$name"] ?: return openDimGrid(GridKind.DIM_MAT, name)
        setGrid(GridState(GridKind.MAT, "Mat$name", m.rows, m.cols, m.data.map { valueSrc(it) }, tag = name))
    }

    private fun openVecGrid(name: String) {
        val v = ctx.vecs["Vct$name"] ?: return openDimGrid(GridKind.DIM_VEC, name)
        setGrid(GridState(GridKind.VEC, "Vct$name", 1, v.size, v.items.map { valueSrc(it) }, tag = name))
    }

    private fun openEqnGrid(kind: String) {
        val labels = listOf("a", "b", "c", "d", "e")
        when (kind) {
            "lin2", "lin3" -> {
                val n = if (kind == "lin2") 2 else 3
                val cols = n + 1
                val colLabels = listOf("a", "b", "c", "d").take(cols)
                setGrid(GridState(GridKind.EQN_LINEAR, if (n == 2) "anX+bnY=cn" else "anX+bnY+cnZ=dn", n, cols,
                    List(n * cols) { "0" }, rowLabels = (1..n).map { "$it" }, colLabels = colLabels))
            }
            else -> {
                val n = when (kind) { "p2" -> 3; "p3" -> 4; else -> 5 }
                val title = when (kind) { "p2" -> "aX²+bX+c=0"; "p3" -> "aX³+bX²+cX+d=0"; else -> "aX⁴+bX³+cX²+dX+e=0" }
                setGrid(GridState(GridKind.EQN_POLY, title, n, 1, List(n) { "0" }, rowLabels = labels.take(n).map { "$it=" }))
            }
        }
    }

    private fun openTableGrid() {
        val hasG = s.tableG
        val labels = (if (hasG) listOf("f(X)=", "g(X)=") else listOf("f(X)=")) + listOf("Start=", "End=", "Step=")
        val rows = labels.size
        val cells = (0 until rows).map { i ->
            val src = when {
                i < tableFixedRows() -> tableCells.getOrElse(i) { "" }
                else -> tableCells.getOrElse(i - tableFixedRows() + 2) { "" }
            }
            src
        }
        setGrid(GridState(GridKind.TABLE_PARAMS, "TABLE", rows, 1, cells, rowLabels = labels))
    }

    // ------------------------------------------------------------------ statistics

    private fun statCols() = if (s.statType.twoVar) (if (s.statFreq) 3 else 2) else (if (s.statFreq) 2 else 1)
    private fun statLimit() = if (s.statType.twoVar || s.statFreq) 40 else 80

    private fun openStatEditor() {
        val cols = statCols()
        val labels = buildList {
            add("X"); if (s.statType.twoVar) add("Y"); if (s.statFreq) add("FREQ")
        }
        val old = s.statCells
        val filled = old.indexOfLast { r -> r.take(cols).any { it.isNotBlank() } } + 1
        val rows = minOf(statLimit(), maxOf(filled + 1, 2))
        val cells = (0 until rows).flatMap { r -> (0 until cols).map { c -> old.getOrNull(r)?.getOrNull(c) ?: "" } }
        setGrid(GridState(GridKind.STAT, s.statType.label, rows, cols, cells, colLabels = labels))
    }

    private fun storeStatCells(g: GridState) {
        s = s.copy(statCells = (0 until g.rows).map { r -> (0 until g.cols).map { c -> g.cell(r, c) } })
    }

    private fun computeStat() {
        ctx.stat = null
        val cols = statCols()
        val x = ArrayList<Double>(); val y = ArrayList<Double>(); val f = ArrayList<Double>()
        try {
            for (row in s.statCells) {
                if (row.take(cols).all { it.isBlank() }) continue
                x += cellReal(row[0]).d
                if (s.statType.twoVar) y += cellReal(row.getOrElse(1) { "" }.ifBlank { throw CalcError(ErrorKind.ARGUMENT) }).d
                if (s.statFreq) f += cellReal(row.getOrElse(cols - 1) { "" }.ifBlank { "1" }).d
            }
            if (x.isNotEmpty()) ctx.stat = Stat.compute(s.statType, x, if (s.statType.twoVar) y else null, if (s.statFreq) f else null)
        } catch (e: CalcError) { s = s.copy(notice = e.kind.title) }
    }

    // ------------------------------------------------------------------ results / table screens

    private fun resultsKey(scr: Screen.Results, key: Key) {
        when (key) {
            Key.UP -> s = s.copy(screen = scr.copy(idx = maxOf(0, scr.idx - 1)))
            Key.DOWN -> s = s.copy(screen = scr.copy(idx = minOf(scr.pages.lastIndex, scr.idx + 1)))
            Key.SD -> s = s.copy(screen = scr.copy(decimal = !scr.decimal))
            Key.AC, Key.EQ -> s = s.copy(screen = scr.back?.let { Screen.Grid(it) } ?: Screen.Main)
            else -> {}
        }
    }

    private fun tableKey(scr: Screen.Table, key: Key) {
        when (key) {
            Key.UP -> s = s.copy(screen = scr.copy(sel = maxOf(0, scr.sel - 1)))
            Key.DOWN -> s = s.copy(screen = scr.copy(sel = minOf(scr.rows.lastIndex, scr.sel + 1)))
            Key.AC, Key.EQ -> s = s.copy(screen = scr.back?.let { Screen.Grid(it) } ?: Screen.Main)
            else -> {}
        }
    }

    // ------------------------------------------------------------------ misc

    companion object {
        val DIGITS = mapOf(
            Key.D0 to '0', Key.D1 to '1', Key.D2 to '2', Key.D3 to '3', Key.D4 to '4',
            Key.D5 to '5', Key.D6 to '6', Key.D7 to '7', Key.D8 to '8', Key.D9 to '9'
        )
        private val VARS = setOf("A", "B", "C", "D", "E", "F", "X", "Y", "M")
        private val DMS_SYMBOLS = setOf("°", "′", "″")

        /** Engine-parsable source text for a value (used to fill editor cells). */
        fun valueSrc(v: Value): String = when (v) {
            is Real -> {
                val q = v.q
                when {
                    q != null && q.isInteger -> if (v.d < 0) "(${q})" else q.toString()
                    q != null -> "(${q.num}÷${q.den})"
                    else -> BigDecimal(v.d).round(MathContext(15)).stripTrailingZeros().toPlainString().let { if (v.d < 0) "($it)" else it }
                }
            }
            is Cplx -> "${valueSrc(v.re)}+${valueSrc(v.im)}i"
            else -> "0"
        }
    }
}
