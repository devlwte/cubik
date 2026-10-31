package com.kyrnforge.cubik

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

data class Entry(val id: String, val ts: Long, val input: String, val kg: Int, val truck: Truck?) {
    fun result() = calculate(input, kg, truck)
}

data class Day(val key: String, val label: String, val entries: List<Entry>)

private val ES = Locale("es", "CO")
private val MONTHS = listOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC")

fun dayKey(ts: Long): String = SimpleDateFormat("yyyy-MM-dd", ES).format(ts)
fun hourTxt(ts: Long): String = SimpleDateFormat("h:mm", ES).format(ts) + if (Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.HOUR_OF_DAY) < 12) " am" else " pm"

fun dayLabel(key: String, now: Long = System.currentTimeMillis()): String {
    val (y, m, d) = key.split('-').map { it.toInt() }
    val short = "$d ${MONTHS[m - 1]}"
    val yesterday = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
    return when (key) {
        dayKey(now) -> "HOY · $short"
        dayKey(yesterday) -> "AYER · $short"
        else -> "$short $y"
    }
}

fun List<Entry>.days(): List<Day> =
    sortedByDescending { it.ts }.groupBy { dayKey(it.ts) }.map { (k, v) -> Day(k, dayLabel(k), v) }

// Archivo de respaldo: el mismo formato en la web y en Android.

fun exportJson(entries: List<Entry>): String = JSONObject()
    .put("app", "cubik").put("type", "historial").put("version", 1)
    .put("entries", JSONArray().apply {
        entries.sortedBy { it.ts }.forEach {
            put(JSONObject().put("id", it.id).put("ts", it.ts).put("input", it.input).put("kg", it.kg).put("truck", truckKey(it.truck)))
        }
    }).toString(2)

fun parseBackup(text: String): List<Entry>? = try {
    val o = JSONObject(text)
    if (o.optString("app") != "cubik") null else {
        val a = o.getJSONArray("entries")
        (0 until a.length()).mapNotNull { i ->
            val e = a.optJSONObject(i) ?: return@mapNotNull null
            val input = e.optString("input")
            val kg = e.optInt("kg", 50)
            if (input.isEmpty() || kg !in BAG_KG) null
            else Entry(e.optString("id").ifEmpty { UUID.randomUUID().toString() }, e.optLong("ts", System.currentTimeMillis()), input, kg, truckOf(e.optString("truck")))
        }
    }
} catch (e: Exception) { null }

class QuickStore(context: Context) {
    private val prefs = context.getSharedPreferences("cubik_calc", Context.MODE_PRIVATE)

    var quick by mutableStateOf(prefs.getBoolean("quick", false))
        private set
    var input by mutableStateOf(prefs.getString("input", "").orEmpty())
        private set
    var kg by mutableStateOf(prefs.getInt("kg", 50).takeIf { it in BAG_KG } ?: 50)
        private set
    var truck by mutableStateOf(truckOf(prefs.getString("truck", null)))
        private set
    var shown by mutableStateOf(prefs.getBoolean("shown", false))
        private set
    var history by mutableStateOf(readHistory())
        private set
    private var lastId: String? = prefs.getString("lastId", null)

    fun switchQuick(on: Boolean) { quick = on; save() }

    fun type(next: String) { input = next; shown = false; save() }

    fun cycleKg() {
        kg = BAG_KG[(BAG_KG.indexOf(kg) + 1) % BAG_KG.size]
        refreshShown()
    }

    fun cycleTruck() {
        truck = TRUCK_CYCLE[(TRUCK_CYCLE.indexOf(truck) + 1) % TRUCK_CYCLE.size]
        refreshShown()
    }

    private fun refreshShown() {
        if (shown && calculate(input, kg, truck) is CalcResult.Ok) {
            val id = lastId
            history = history.map { if (it.id == id) it.copy(kg = kg, truck = truck) else it }
            writeHistory()
        }
        save()
    }

    fun equals() {
        shown = true
        if (calculate(input, kg, truck) is CalcResult.Ok) {
            val last = history.maxByOrNull { it.ts }
            val now = System.currentTimeMillis()
            if (last != null && last.input == input && last.kg == kg && last.truck == truck && dayKey(last.ts) == dayKey(now)) {
                lastId = last.id
            } else {
                val e = Entry(UUID.randomUUID().toString(), now, input, kg, truck)
                history = history + e
                lastId = e.id
                writeHistory()
            }
        }
        save()
    }

    fun clear() { input = ""; shown = false; save() }

    fun open(e: Entry) { input = e.input; kg = e.kg; truck = e.truck; shown = true; lastId = e.id; quick = true; save() }

    fun delete(ids: Set<String>) { history = history.filterNot { it.id in ids }; writeHistory() }

    fun deleteAll() { history = emptyList(); writeHistory() }

    /** Suma un respaldo al historial; los cálculos que ya están (mismo id) no se repiten. Devuelve cuántos entraron. */
    fun import(list: List<Entry>): Int {
        val have = history.map { it.id }.toSet()
        val fresh = list.filter { it.id !in have }.distinctBy { it.id }
        history = history + fresh
        writeHistory()
        return fresh.size
    }

    private fun save() {
        prefs.edit().putBoolean("quick", quick).putString("input", input).putInt("kg", kg)
            .putString("truck", truckKey(truck)).putBoolean("shown", shown).putString("lastId", lastId).apply()
    }

    private fun readHistory(): List<Entry> = prefs.getString("history", null)?.let(::parseBackup).orEmpty()

    private fun writeHistory() { prefs.edit().putString("history", exportJson(history)).apply() }
}
