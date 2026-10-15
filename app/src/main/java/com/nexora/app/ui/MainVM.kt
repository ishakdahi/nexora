package com.nexora.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.app.data.AppDb
import com.nexora.app.data.Prefs
import com.nexora.app.data.Scenario
import com.nexora.app.engine.Engine
import com.nexora.app.engine.EventService
import com.nexora.app.engine.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainVM(private val app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)
    private val prefs = Prefs(app)

    val scenarios = db.scenarios().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val logs = db.logs().observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var kill by mutableStateOf(prefs.kill); private set
    var lang by mutableStateOf(prefs.lang); private set
    var theme by mutableStateOf(prefs.theme); private set

    var draft by mutableStateOf(blank()); private set
    var editorKey by mutableIntStateOf(0); private set

    init {
        viewModelScope.launch(Dispatchers.IO) { Scheduler.rescheduleAll(app) }
    }

    private fun blank() = Scenario(name = "", triggerType = "MANUAL", title = "", message = "")

    fun loadDraft(s: Scenario) { draft = s; editorKey++ }
    fun newDraft() = loadDraft(blank())

    fun save(s: Scenario) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = db.scenarios().upsert(s)
            Scheduler.schedule(app, s.copy(id = id))
            EventService.sync(app)
        }
    }

    fun toggle(s: Scenario, enabled: Boolean) = save(s.copy(enabled = enabled))

    fun delete(s: Scenario) {
        viewModelScope.launch(Dispatchers.IO) {
            db.scenarios().deleteById(s.id)
            Scheduler.cancel(app, s.id)
            EventService.sync(app)
        }
    }

    fun runNow(s: Scenario, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { Engine.run(app, s, true) }
            onResult(r)
        }
    }

    fun updateKill(v: Boolean) {
        kill = v
        prefs.kill = v
        viewModelScope.launch(Dispatchers.IO) { Scheduler.rescheduleAll(app) }
    }

    fun updateLang(v: String) { lang = v; prefs.lang = v }
    fun updateTheme(v: String) { theme = v; prefs.theme = v }
    fun clearLogs() { viewModelScope.launch(Dispatchers.IO) { db.logs().clear() } }
}
