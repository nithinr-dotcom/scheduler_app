package com.wac.worktrack.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wac.worktrack.core.Module
import com.wac.worktrack.core.Prefs

/** Observable copy of the switched-on features, so menus and tabs update as soon as one changes. */
object ModuleState {
    var on by mutableStateOf(Module.entries.toSet())
        private set

    fun load(context: Context) {
        val keys = Prefs(context).modules
        on = Module.entries.filter { it.key in keys }.toSet()
    }

    fun set(context: Context, modules: Set<Module>) {
        Prefs(context).modules = modules.map { it.key }.toSet()
        on = modules
    }

    fun has(m: Module) = m in on
}
