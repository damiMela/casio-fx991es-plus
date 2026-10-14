package com.damimela.fx991

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.damimela.fx991.model.AppPrefs
import com.damimela.fx991.model.CalcState
import com.damimela.fx991.model.CalculatorModel
import com.damimela.fx991.model.Key
import com.damimela.fx991.model.Storage
import kotlinx.coroutines.flow.StateFlow

/** [Storage] backed by SharedPreferences (settings, variables, history). */
class PrefsStorage(context: Context) : Storage {
    private val prefs = context.getSharedPreferences("fx991_state", Context.MODE_PRIVATE)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

class CalcViewModel(app: Application) : AndroidViewModel(app) {
    private val model = CalculatorModel(PrefsStorage(app))
    val state: StateFlow<CalcState> get() = model.state

    fun press(key: Key) = model.press(key)
    fun setPrefs(p: AppPrefs) = model.setPrefs(p)
    fun consumeHelp() = model.consumeHelpRequest()
}
