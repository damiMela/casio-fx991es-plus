package com.damimela.fx991.model

import com.damimela.fx991.engine.AngleUnit
import com.damimela.fx991.engine.DisplaySettings
import com.damimela.fx991.engine.ErrorKind
import com.damimela.fx991.engine.StatType
import com.damimela.fx991.engine.TableGen
import com.damimela.fx991.engine.Value

enum class CalcMode(val label: String) {
    COMP("COMP"), CMPLX("CMPLX"), STAT("STAT"), BASE("BASE-N"), EQN("EQN"), MATRIX("MATRIX"), TABLE("TABLE"), VECTOR("VECTOR")
}

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** App-level (non calculator) preferences. */
data class AppPrefs(
    val haptics: Boolean = true,
    val keepScreenOn: Boolean = false,
    val theme: ThemeChoice = ThemeChoice.SYSTEM
)

class MenuItem(val label: String, val id: String = "", val tokens: List<InTok>? = null)

/** A numbered menu. Number keys 1-9 (0 = tenth item) select, arrows move, = confirms. */
class MenuDef(
    val id: String,
    val title: String,
    val items: List<MenuItem>,
    val columns: Int = 1,
    val digitEntry: Boolean = false,
    val prev: String? = null,
    val next: String? = null
)

enum class GridKind { MAT, VEC, STAT, EQN_LINEAR, EQN_POLY, CALC_VARS, SOLVE_GUESS, TABLE_PARAMS, DIM_MAT, DIM_VEC }

/** Generic cell editor used for matrices, vectors, statistics data, equation coefficients and prompts. */
data class GridState(
    val kind: GridKind,
    val title: String,
    val rows: Int,
    val cols: Int,
    val cells: List<String>,
    val rowLabels: List<String> = emptyList(),
    val colLabels: List<String> = emptyList(),
    val r: Int = 0,
    val c: Int = 0,
    val tag: String = "",
    /** Typed text replaces the cell on first keystroke (Casio-like overwrite of the highlighted cell). */
    val fresh: Boolean = true
) {
    fun cell(r: Int, c: Int) = cells[r * cols + c]
    val current get() = cell(r, c)
    fun with(r: Int, c: Int, text: String): GridState = copy(cells = cells.toMutableList().also { it[r * cols + c] = text })
}

class ResultPage(val label: String, val value: Value?, val note: String? = null)

sealed interface Screen {
    data object Main : Screen
    data class Menu(val def: MenuDef, val sel: Int = 0, val digits: String = "", val back: Screen = Main) : Screen
    data class Grid(val g: GridState) : Screen
    data class Results(
        val title: String, val pages: List<ResultPage>, val idx: Int = 0, val decimal: Boolean = false,
        val back: GridState? = null
    ) : Screen
    data class Table(val rows: List<TableGen.Row>, val hasG: Boolean, val sel: Int = 0, val back: GridState? = null) : Screen
    data class Confirm(val message: String, val action: String) : Screen
}

/** A displayed result. Flags let S⇔D / ENG / polar toggles re-render the same value. */
data class ResultView(
    val value: Value,
    val extras: List<Pair<String, Value>> = emptyList(),
    val outs: List<String> = emptyList(),
    val flipExact: Boolean = false,
    val eng: Int? = null,
    val flipPolar: Boolean = false,
    val flipMixed: Boolean = false,
    val label: String? = null
)

data class ErrorView(val kind: ErrorKind, val tokenIndex: Int)

data class HistoryEntry(val tokens: List<InTok>, val result: String)

enum class PendingMem { STO, RCL }

data class CalcState(
    val mode: CalcMode = CalcMode.COMP,
    val angle: AngleUnit = AngleUnit.DEG,
    val display: DisplaySettings = DisplaySettings(),
    val statFreq: Boolean = false,
    val tableG: Boolean = false,
    val statType: StatType = StatType.ONE_VAR,
    val baseNum: Int = 10,
    val tokens: List<InTok> = emptyList(),
    val cursor: Int = 0,
    val insertMode: Boolean = true,
    val shift: Boolean = false,
    val alpha: Boolean = false,
    val alphaLock: Boolean = false,
    val pending: PendingMem? = null,
    val screen: Screen = Screen.Main,
    val result: ResultView? = null,
    val error: ErrorView? = null,
    val calcSession: Boolean = false,
    val history: List<HistoryEntry> = emptyList(),
    val historyPos: Int = -1,
    val memoryUsed: Boolean = false,
    val hasStat: Boolean = false,
    val statCells: List<List<String>> = emptyList(),
    val prefs: AppPrefs = AppPrefs(),
    val helpRequested: Boolean = false,
    val notice: String? = null,
    val replayText: String? = null
) {
    val resultShown get() = result != null
    val alphaActive get() = alpha || alphaLock
}
