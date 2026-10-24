package com.kyrnforge.cubik

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Estado de la pantalla; cada cambio se guarda en el teléfono para abrir la app donde se dejó. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("cubik", Context.MODE_PRIVATE)

    var load by mutableStateOf(read())
        private set

    fun update(f: (Load) -> Load) {
        load = f(load)
        write(load)
    }

    private fun read(): Load {
        val d = Load()
        val truck = Truck.entries.firstOrNull { it.name == prefs.getString("truck", null) } ?: d.truck
        val wManual = prefs.getBoolean("wManual", false)
        var s = d.copy(
            n = prefs.getInt("n", d.n),
            truck = truck,
            l = prefs.getInt("l", truck.rows),
            w = if (wManual) prefs.getInt("w", DEFAULT_WIDTH) else DEFAULT_WIDTH,
            wManual = wManual,
            mode = Mode.entries.firstOrNull { it.name == prefs.getString("mode", null) } ?: d.mode,
            view = ViewTab.entries.firstOrNull { it.name == prefs.getString("view", null) } ?: d.view,
            prod = prefs.getString("prod", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_PROD,
            kg = prefs.getInt("kg", d.kg),
            autoTruck = prefs.getBoolean("autoTruck", true)
        ).withAutoTruck()
        val saved = prefs.getString("rows", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }
        s = if (s.mode == Mode.MANUAL && saved.size == s.l) {
            s.copy(rows = saved, sel = prefs.getInt("sel", 0).coerceIn(0, s.l - 1))
        } else s.distributed()
        return s
    }

    private fun write(s: Load) {
        prefs.edit()
            .putInt("n", s.n).putString("truck", s.truck.name).putInt("l", s.l).putInt("w", s.w)
            .putBoolean("wManual", s.wManual).putString("mode", s.mode.name)
            .putString("rows", s.rows.joinToString(",")).putInt("sel", s.sel)
            .putString("view", s.view.name).putString("prod", s.prod).putInt("kg", s.kg)
            .putBoolean("autoTruck", s.autoTruck)
            .apply()
    }
}
