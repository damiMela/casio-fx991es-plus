package com.damimela.fx991.model

import com.damimela.fx991.engine.AngleUnit
import com.damimela.fx991.engine.Context
import com.damimela.fx991.engine.DisplaySettings
import com.damimela.fx991.engine.MatV
import com.damimela.fx991.engine.NumFormat
import com.damimela.fx991.engine.Real
import com.damimela.fx991.engine.StatType
import com.damimela.fx991.engine.Value

/** Minimal key-value store (backed by SharedPreferences on Android, in memory for tests). */
interface Storage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class MemoryStorage : Storage {
    private val map = HashMap<String, String>()
    override fun getString(key: String) = map[key]
    override fun putString(key: String, value: String) { map[key] = value }
}

/** Serialization of everything that survives app restarts. Plain text, no external libraries. */
object Persistence {
    private const val FS = '\u001f'
    private const val RS = '\u001e'
    private const val TS = '\u001c'
    const val MAX_HISTORY = 30

    fun encodeTokens(tokens: List<InTok>) =
        tokens.joinToString(FS.toString()) { "${it.role.ordinal}$TS${it.text}$TS${it.display}" }

    fun decodeTokens(s: String): List<InTok> =
        if (s.isEmpty()) emptyList() else s.split(FS).mapNotNull { f ->
            val p = f.split(TS)
            if (p.size < 3) null else InTok(p[1], p[2], Role.values().getOrElse(p[0].toIntOrNull() ?: 0) { Role.NORMAL })
        }

    fun saveHistory(storage: Storage, h: List<HistoryEntry>) =
        storage.putString("history", h.takeLast(MAX_HISTORY).joinToString(RS.toString()) { encodeTokens(it.tokens) + "\u001d" + it.result })

    fun loadHistory(storage: Storage): List<HistoryEntry> =
        storage.getString("history")?.takeIf { it.isNotEmpty() }?.split(RS)?.mapNotNull { e ->
            val i = e.indexOf('\u001d')
            if (i < 0) null else HistoryEntry(decodeTokens(e.substring(0, i)), e.substring(i + 1))
        } ?: emptyList()

    fun saveSettings(storage: Storage, s: CalcState) {
        val d = s.display
        storage.putString(
            "settings",
            listOf(
                s.angle.name, d.format.name, d.digits, d.mathIO, d.mixedFractions, d.decimalComma, d.engSymbols,
                d.polarComplex, s.statFreq, s.tableG, s.mode.name, s.statType.name, s.baseNum
            ).joinToString(",")
        )
        val p = s.prefs
        storage.putString("prefs", "${p.haptics},${p.keepScreenOn},${p.theme.name}")
    }

    fun loadSettings(storage: Storage, base: CalcState): CalcState {
        var st = base
        storage.getString("settings")?.split(",")?.let { f ->
            runCatching {
                st = st.copy(
                    angle = AngleUnit.valueOf(f[0]),
                    display = DisplaySettings(
                        NumFormat.valueOf(f[1]), f[2].toInt(), f[3].toBoolean(), f[4].toBoolean(), f[5].toBoolean(),
                        f[6].toBoolean(), f[7].toBoolean()
                    ),
                    statFreq = f[8].toBoolean(), tableG = f[9].toBoolean(), mode = CalcMode.valueOf(f[10]),
                    statType = StatType.valueOf(f[11]), baseNum = f[12].toInt()
                )
            }
        }
        storage.getString("prefs")?.split(",")?.let { f ->
            runCatching { st = st.copy(prefs = AppPrefs(f[0].toBoolean(), f[1].toBoolean(), ThemeChoice.valueOf(f[2]))) }
        }
        return st
    }

    private fun num(v: Value?): String? = (v as? Real)?.d?.toString()

    fun saveMemory(storage: Storage, ctx: Context) {
        val vars = ctx.vars.entries.mapNotNull { (k, v) -> num(v)?.let { "$k=$it" } }
        storage.putString("vars", (vars + listOfNotNull(num(ctx.ans)?.let { "Ans=$it" }, num(ctx.preAns)?.let { "PreAns=$it" })).joinToString(";"))
        storage.putString(
            "mats",
            ctx.mats.entries.filter { it.key != "MatAns" }.joinToString("|") { (k, m) ->
                "$k;${m.rows};${m.cols};" + m.data.joinToString(";") { num(it) ?: "0.0" }
            }
        )
    }

    fun loadMemory(storage: Storage, ctx: Context) {
        storage.getString("vars")?.split(";")?.forEach { kv ->
            val i = kv.indexOf('=')
            val d = if (i > 0) kv.substring(i + 1).toDoubleOrNull() else null
            if (d != null) when (val k = kv.substring(0, i)) {
                "Ans" -> ctx.ans = Real.of(d)
                "PreAns" -> ctx.preAns = Real.of(d)
                else -> ctx.vars[k] = Real.of(d)
            }
        }
        storage.getString("mats")?.takeIf { it.isNotEmpty() }?.split("|")?.forEach { e ->
            val p = e.split(";")
            runCatching {
                val r = p[1].toInt(); val c = p[2].toInt()
                ctx.mats[p[0]] = MatV(r, c, (0 until r * c).map { Real.of(p[3 + it].toDouble()) })
            }
        }
    }
}
