package com.kyrnforge.cubik

import kotlin.math.abs
import kotlin.math.roundToInt

enum class Axles { SINGLE, TANDEM, MULA }

/** Capacidad en báscula, filas de largo y altura de baranda en pisos. */
enum class Truck(val label: String, val tons: Double, val rows: Int, val techo: Int, val axles: Axles) {
    TURBO("Turbo", 4.5, 6, 10, Axles.SINGLE),
    SENCILLO("Sencillo", 9.0, 8, 12, Axles.SINGLE),
    DOBLE("Doble troque", 17.0, 9, 12, Axles.TANDEM),
    MULA("Mula", 32.0, 12, 13, Axles.MULA)
}

val Truck.deName: String get() = if (this == Truck.MULA) "de la mula" else "del ${label.lowercase()}"

enum class Mode { AUTO, MANUAL }
enum class ViewTab { SIDE, TOP, BACK, AXLE }

const val DEFAULT_PROD = "Carga general"
const val DEFAULT_WIDTH = 5
val PRESETS = listOf(100, 150, 200, 400, 600)
val BAG_KG = listOf(25, 40, 50)
val LENGTH_CHIPS = listOf(6, 7, 8, 9, 10)
val WIDTH_CHIPS = listOf(3, 4, 5)

data class Load(
    val n: Int = 300,
    val truck: Truck = Truck.DOBLE,
    val l: Int = Truck.DOBLE.rows,
    val w: Int = DEFAULT_WIDTH,
    val wManual: Boolean = false,
    val mode: Mode = Mode.AUTO,
    val rows: List<Int> = emptyList(),
    val sel: Int = 0,
    val view: ViewTab = ViewTab.SIDE,
    val prod: String = DEFAULT_PROD,
    val kg: Int = 50,
    val autoTruck: Boolean = true
) {
    val total: Int get() = rows.sum()
    val tons: Double get() = total * kg / 1000.0
    val targetTons: Double get() = n * kg / 1000.0
    val maxBags: Int get() = (truck.tons * 1000 / kg).toInt()
    val over: Boolean get() = tons > truck.tons
}

/** Una fila: [p] pisos completos de w bultos y [t] bultos atravesados encima (o en el piso si p = 0). */
data class Row(val n: Int, val p: Int, val t: Int) {
    val height: Int get() = p + if (t > 0) 1 else 0
}

fun Load.row(n: Int) = Row(n, n / w, n % w)

/** Posición y orientación de los bultos del remate superior según el ancho de fila. */
data class TopLayer(val crossed: Boolean, val slots: List<Pair<Float, Float>>)

fun topLayer(w: Int, t: Int): TopLayer {
    if (t <= 0) return TopLayer(true, emptyList())
    if (t <= 3) {
        val size = minOf(1.8f, w.toFloat() / t)
        if (t == 1) return TopLayer(true, listOf((w - size) / 2 to size))
        val gap = (w - t * size) / (t - 1)
        return TopLayer(true, List(t) { it * (size + gap) to size })
    }
    val order = buildList { var a = 0; var b = w - 1; while (a <= b) { add(a); if (a != b) add(b); a++; b-- } }
    return TopLayer(false, order.take(t).sorted().map { it.toFloat() to 1f })
}

fun topText(w: Int, t: Int): String {
    val l = topLayer(w, t)
    if (l.crossed) return when (t) {
        1 -> "+ **1 atravesado** encima, en la mitad de la fila."
        2 -> "+ **2 atravesados** encima, uno a cada lado" + if (w - 2 * 1.8f > 0.3f) " y el centro libre." else "."
        else -> "+ **$t atravesados** encima, repartidos parejo a lo ancho."
    }
    val a = (t + 1) / 2
    val b = t / 2
    return "+ **$t bultos encima a lo largo**, " + (if (a == b) "$a a cada lado" else "$a a un lado y $b al otro") +
        (if (w == t) "." else if (a == b) " y el centro libre." else " y un espacio libre.")
}

fun topShort(w: Int, t: Int) =
    if (topLayer(w, t).crossed) "$t ${plural(t, "atravesado", "atravesados")}" else "$t encima a lo largo"
fun Load.layout(): List<Row> = rows.map { row(it) }

/** Reparto de bultos por fila desde la cabina hacia la compuerta. */
fun Load.distributed(): Load {
    val base = n / l
    val extra = n % l
    val even = List(l) { base + if (it < extra) 1 else 0 }
    val r = cleanerSplit(even)?.takeIf { copy(rows = it).balance().ok } ?: even
    val t = r.indexOfFirst { it >= w && it % w != 0 }
    return copy(rows = r, sel = if (t >= 0) t else 0)
}

/** Busca un reparto alternativo que unifique el remate superior dejando el resto en pisos completos. */
private fun Load.cleanerSplit(even: List<Int>): List<Int>? {
    val q = n / l
    fun kinds(r: List<Int>) = r.map { it % w }.filter { it != 0 }.toSet().size
    var bestK = kinds(even)
    if (q < w || bestK <= 1) return null
    var bestS = Int.MAX_VALUE
    var best: List<Int>? = null
    for (k in 1 until l) for (b in maxOf(0, q - w)..q + w) {
        val rest = n - b * k
        if (rest < 0 || rest % (l - k) != 0) continue
        val a = rest / (l - k)
        if (a == b || abs(a - b) > minOf(2, w - 1)) continue
        val r = (List(l - k) { a } + List(k) { b }).sortedDescending()
        val kk = kinds(r)
        val ss = r.first() - r.last()
        if (kk < bestK || (best != null && kk == bestK && ss < bestS)) { best = r; bestK = kk; bestS = ss }
    }
    return best
}

/** Selecciona el vehículo más pequeño que soporte el tonelaje total. */
fun Load.withAutoTruck(): Load {
    if (!autoTruck) return this
    val k = Truck.entries.firstOrNull { targetTons <= it.tons } ?: Truck.MULA
    return if (k != truck) copy(truck = k, l = k.rows) else this
}

fun Load.recalc(): Load = withAutoTruck().distributed()

data class Balance(val x: Double, val front: Int, val back: Int, val ok: Boolean)

fun Load.balance(): Balance {
    var tot = 0
    var m = 0.0
    rows.forEachIndexed { i, n -> tot += n; m += n * (i + 0.5) / rows.size }
    val x = if (tot > 0) m / tot else 0.5
    val front = ((1 - x) * 100).roundToInt()
    return Balance(x, front, 100 - front, abs(front - 50) <= 6)
}

// Textos y hoja de cargue

fun fmtT(t: Double): String {
    val r = (t * 10).roundToInt() / 10.0
    return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString().replace('.', ',')
}

fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

fun Load.rowsTxt(idx: List<Int>): String {
    val nums = idx.map { it + 1 }.sorted()
    if (nums.size == l && l > 1) return "todas las filas"
    if (nums.size == 1) return "la fila ${nums[0]}"
    val consecutive = nums.zipWithNext().all { (a, b) -> b == a + 1 }
    if (consecutive && nums.size >= 4) return "las filas ${nums.first()} a ${nums.last()}"
    return "las filas " + nums.dropLast(1).joinToString(", ") + " y " + nums.last()
}

fun Load.prodTxt(): String =
    if (Regex("^[A-ZÁÉÍÓÚÑ][a-záéíóúñ]").containsMatchIn(prod)) prod[0].lowercase() + prod.substring(1) else prod

enum class StepKind { PLAIN, TRABA, OK, BAD }

data class Step(val text: String, val kind: StepKind = StepKind.PLAIN)

fun Load.routeSteps(): List<Step> {
    val lay = layout()
    val b = balance()
    val steps = mutableListOf(
        Step("Poner **$l filas** de fondo a compuerta."),
        Step("Cada fila lleva **$w bultos** a lo ancho.")
    )
    val groups = linkedMapOf<Int, MutableList<Int>>()
    lay.forEachIndexed { i, r -> groups.getOrPut(r.n) { mutableListOf() }.add(i) }
    groups.forEach { (n, idx) ->
        val r = row(n)
        val who = cap(rowsTxt(idx))
        val each = if (idx.size > 1) " cada una" else ""
        steps += when {
            n == 0 -> Step("$who: ${plural(idx.size, "queda vacía", "quedan vacías")}.")
            r.p == 0 -> Step("$who: **$n ${plural(n, "bulto", "bultos")}**$each, solo en el piso.")
            else -> Step(
                "$who: **$n bultos**$each = ${r.p} ${plural(r.p, "piso", "pisos")} de $w" +
                    if (r.t > 0) " " + topText(w, r.t) else ".",
                if (r.t > 0) StepKind.TRABA else StepKind.PLAIN
            )
        }
    }
    steps += if (b.ok) Step(
        "Reparto: ${b.front}% adelante y ${b.back}% atrás. **Camión parejo**, no está recostado.", StepKind.OK
    ) else Step(
        "Reparto: ${b.front}% adelante y ${b.back}% atrás. **Camión recostado hacia " +
            (if (b.front > 50) "la cabina" else "la compuerta") + "**: pasar bultos hacia el centro.", StepKind.BAD
    )
    steps += Step(
        "Total: **$total bultos** de ${prodTxt()} = **${fmtT(tons)} toneladas**. " +
            if (over) "Supera las ${fmtT(truck.tons)} T ${truck.deName}." else "Dentro de las ${fmtT(truck.tons)} T ${truck.deName}.",
        if (over) StepKind.BAD else StepKind.OK
    )
    return steps
}

fun Load.summary(): String {
    val g = linkedMapOf<Int, Int>()
    rows.forEach { g[it] = (g[it] ?: 0) + 1 }
    val parts = g.map { (n, c) ->
        if (n > 0) "$c ${plural(c, "fila", "filas")} de $n ${plural(n, "bulto", "bultos")}"
        else "$c ${plural(c, "fila vacía", "filas vacías")}"
    }
    return if (parts.size > 1) parts.dropLast(1).joinToString(", ") + " y " + parts.last() else parts.firstOrNull().orEmpty()
}

fun Load.shareText(): String {
    val lines = mutableListOf(
        "*CARGUE · ${prod.uppercase()}*",
        "Camión: ${truck.label} (máx. ${fmtT(truck.tons)} T)",
        "Carga: $total bultos de $kg kg = ${fmtT(tons)} T ${if (over) "⚠️ SOBREPESO" else "✅"}",
        "*Resumen: ${summary()}*",
        ""
    )
    routeSteps().forEachIndexed { i, s -> lines += "${i + 1}. ${s.text.replace(Regex("\\*\\*(.+?)\\*\\*"), "*$1*")}" }
    lines += ""
    lines += "Bultos por fila (cabina → compuerta):"
    lines += rows.mapIndexed { i, n -> "F${i + 1}: $n" }.joinToString(" | ")
    return lines.joinToString("\n")
}
