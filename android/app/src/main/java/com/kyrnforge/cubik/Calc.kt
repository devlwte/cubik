package com.kyrnforge.cubik

// Formato: bultos÷filas×ancho,producto1,producto2,…  (filas, ancho y productos son opcionales).

const val MAX_PRODUCTS = 4
const val MAX_BAGS = 3000
const val EXAMPLE = "Ejemplo: 350÷12×5"

val TRUCK_CYCLE: List<Truck?> = listOf(null) + Truck.entries

fun truckKey(t: Truck?) = t?.name?.lowercase() ?: "auto"
fun truckOf(key: String?): Truck? = Truck.entries.firstOrNull { it.name.equals(key, ignoreCase = true) }

data class Product(val n: Int, val rest: Boolean = false)

sealed interface CalcResult {
    data class Ok(val load: Load, val products: List<Product>) : CalcResult
    data class Error(val text: String, val hint: String) : CalcResult
}

data class Expr(val bultos: String, val filas: String?, val ancho: String?, val products: List<String>?)

private val EXPR = Regex("^([^÷×,]*)(?:÷([^×,]*))?(?:×([^,]*))?(?:,(.*))?$")

fun splitExpr(input: String): Expr {
    val g = EXPR.find(input)?.groups ?: return Expr(input, null, null, null)
    return Expr(g[1]?.value.orEmpty(), g[2]?.value, g[3]?.value, g[4]?.value?.split(','))
}

/** Historial de la 1.1 ("bultos,filas,ancho÷productos") al formato nuevo ("bultos÷filas×ancho,productos"). */
fun fromV1(s: String): String {
    val i = s.indexOf('÷')
    val head = (if (i < 0) s else s.substring(0, i)).split(',')
    val f = head.getOrNull(1).orEmpty()
    val a = head.getOrNull(2).orEmpty()
    return head[0] + (if (f.isNotEmpty()) "÷$f" else "") + (if (a.isNotEmpty()) "×$a" else "") + (if (i >= 0) "," + s.substring(i + 1) else "")
}

private fun sumOf(part: String): Int? {
    val nums = part.split('+').filter { it.isNotEmpty() }
    if (nums.isEmpty()) return null
    return nums.sumOf { it.toIntOrNull() ?: return null }
}

fun calculate(input: String, kg: Int, truck: Truck?): CalcResult {
    val e = splitExpr(input)
    val n = sumOf(e.bultos) ?: return CalcResult.Error("Escriba los bultos", EXAMPLE)
    if (n < 1) return CalcResult.Error("Los bultos deben ser más de 0", EXAMPLE)
    if (n > MAX_BAGS) return CalcResult.Error("Máximo $MAX_BAGS bultos", "Revise el total de bultos.")
    val filas = e.filas?.takeIf { it.isNotEmpty() }?.toIntOrNull()
    if (filas != null && filas !in 3..16) return CalcResult.Error("Las filas van de 3 a 16", "Usted escribió $filas filas.")
    val ancho = e.ancho?.takeIf { it.isNotEmpty() }?.toIntOrNull()
    if (ancho != null && ancho !in 3..5) return CalcResult.Error("El ancho va de 3 a 5 bultos", "Usted escribió $ancho de ancho.")

    var base = Load(n = n, kg = kg, w = ancho ?: DEFAULT_WIDTH, wManual = ancho != null, autoTruck = truck == null,
        truck = truck ?: Truck.DOBLE, l = (truck ?: Truck.DOBLE).rows).withAutoTruck()
    base = base.copy(l = filas ?: base.truck.rows).distributed()

    splitCount(e)?.let { k ->
        if (n < k) return CalcResult.Error("No alcanzan los bultos para $k productos", "Escriba más bultos o menos productos.")
        return CalcResult.Ok(base, List(k) { Product(n / k + if (it < n % k) 1 else 0) })
    }
    val written = e.products?.filter { it.isNotEmpty() }?.map { sumOf(it) ?: 0 } ?: return CalcResult.Ok(base, emptyList())
    if (written.isEmpty()) return CalcResult.Ok(base, emptyList())
    if (written.any { it < 1 }) return CalcResult.Error("Cada producto debe tener bultos", "Quite el producto en 0.")
    if (written.size > MAX_PRODUCTS) return CalcResult.Error("Máximo $MAX_PRODUCTS productos", "Junte algunos productos.")
    val s = written.sum()
    val sumTxt = written.joinToString(" + ")
    if (written.size == 1 && written[0] == n) return CalcResult.Error("Para dividir se necesitan al menos 2 productos", "Escriba menos de $n o quite la coma.")
    if (s > n) return if (written.size == 1) CalcResult.Error("$s bultos pasa del total ($n)", "El producto debe ser menor que el total.")
        else CalcResult.Error("$sumTxt = $s bultos, pasa del total ($n)", "Revise los productos: no pueden pasar de $n.")
    if (s == n) return CalcResult.Ok(base, written.map { Product(it) })
    // Lo que sobra siempre pasa a ser otro producto (RESTO).
    if (written.size == MAX_PRODUCTS) return CalcResult.Error("Sobran ${n - s} bultos y ya hay $MAX_PRODUCTS productos", "Junte algunos productos.")
    return CalcResult.Ok(base, written.map { Product(it) } + Product(n - s, rest = true))
}

/** ",2" a ",4" (un solo número) = dividir la carga en esa cantidad de productos iguales. */
fun splitCount(e: Expr): Int? = e.products?.singleOrNull()?.toIntOrNull()?.takeIf { it in 2..MAX_PRODUCTS }

fun numberSpans(input: String): List<IntRange> = Regex("[0-9]+").findAll(input).map { it.range }.toList()

fun numberAt(input: String, offset: Int): Int? =
    numberSpans(input).indexOfFirst { offset in it.first..it.last + 1 }.takeIf { it >= 0 }

fun numberName(input: String, k: Int): String {
    val r = numberSpans(input).getOrNull(k) ?: return ""
    val before = input.substring(0, r.first)
    return when {
        ',' in before -> if (splitCount(splitExpr(input)) != null) "cuántos productos" else "el producto ${before.count { it == ',' }}"
        '×' in before -> "el ancho"
        '÷' in before -> "las filas"
        else -> "los bultos"
    }
}

/** Tecla sobre el número tocado: la primera cifra lo reemplaza y ⌫ borra de a una (si se vacía queda 0). */
fun editNumber(input: String, k: Int, key: String, fresh: Boolean): String {
    val r = numberSpans(input).getOrNull(k) ?: return input
    val cur = input.substring(r)
    val next = when {
        key == "⌫" -> cur.dropLast(1).ifEmpty { "0" }
        fresh || cur == "0" -> key.trimStart('0').ifEmpty { "0" }
        else -> cur + key
    }
    return if (next.length > 4) input else input.replaceRange(r, next)
}

data class RowLine(val count: Int, val n: Int, val main: String, val top: String?)

fun Load.rowLines(): List<RowLine> {
    val g = linkedMapOf<Int, Int>()
    rows.forEach { g[it] = (g[it] ?: 0) + 1 }
    return g.map { (n, c) ->
        val r = row(n)
        when {
            n == 0 -> RowLine(c, n, "vacía", null)
            r.p == 0 -> RowLine(c, n, "$n en el piso", null)
            else -> RowLine(
                c, n, "$n = ${r.p} ${plural(r.p, "piso", "pisos")} de $w",
                if (r.t == 0) null else if (topLayer(w, r.t).crossed) "${r.t} atrav." else "${r.t} encima"
            )
        }
    }
}

fun RowLine.text() = "$count ${plural(count, "fila", "filas")} → $main" + (top?.let { " + $it" } ?: "")

/** Qué lleva cada producto, cargando desde el fondo: "3 filas de 38 + 16 en fila 4". */
fun Load.productText(products: List<Product>): List<String> {
    var i = 0
    var used = 0
    return products.map { p ->
        var left = p.n
        val parts = mutableListOf<String>()
        var runN = 0
        var runC = 0
        fun flush() {
            if (runC == 0) return
            parts += "$runC ${plural(runC, "fila", "filas")} de $runN"
            runC = 0
        }
        while (left > 0 && i < rows.size) {
            val avail = rows[i] - used
            if (avail <= 0) { i++; used = 0; continue }
            val take = minOf(left, avail)
            if (used == 0 && take == rows[i]) {
                if (runC > 0 && runN != rows[i]) flush()
                runN = rows[i]
                runC++
            } else {
                flush()
                parts += "$take en fila ${i + 1}"
            }
            left -= take
            used += take
            if (used >= rows[i]) { i++; used = 0 }
        }
        flush()
        parts.joinToString(" + ")
    }
}

fun productLabel(i: Int, p: Product) = "P${i + 1} · ${p.n}"

fun Load.quickShareText(products: List<Product>): String {
    if (products.isEmpty()) return shareText()
    val where = productText(products)
    val lines = mutableListOf(shareText(), "", "*Productos (fondo → compuerta):*")
    products.forEachIndexed { i, p -> lines += "${productLabel(i, p)} → ${where[i]}" + if (p.rest) " (resto)" else "" }
    return lines.joinToString("\n")
}

/** Formas de repartir los mismos bultos en las mismas filas, sin repetir:
 *  parejo (260÷9 → 29 y 28), con un solo tipo de remate (30 y 28) y en pisos completos (30 y 25). */
data class Option(val tag: String, val load: Load)

fun Load.options(): List<Option> {
    val out = mutableListOf<Load>()
    fun add(o: Load) { if (out.none { it.rows.sorted() == o.rows.sorted() }) out += o }
    add(copy(rows = List(l) { n / l + if (it < n % l) 1 else 0 }))
    add(this)
    val pisos = n / w
    val base = pisos / l
    val ex = pisos % l
    if (base > 0) add(copy(rows = MutableList(l) { (base + if (it < ex) 1 else 0) * w }.also { it[ex] += n % w }))
    return out.mapIndexed { i, o -> Option("ABC"[i].toString(), o) }
}

/** "5 pisos + 4 encima" para una fila de n bultos. */
fun Load.pisosText(n: Int): String {
    val r = row(n)
    val top = if (r.t == 0) "" else if (topLayer(w, r.t).crossed) "${r.t} atrav." else "${r.t} encima"
    return when {
        r.p == 0 -> "en el piso"
        top.isEmpty() -> "${r.p} ${plural(r.p, "piso", "pisos")}"
        else -> "${r.p} ${plural(r.p, "piso", "pisos")} + $top"
    }
}

// Calculadora normal

const val OPS = "+−×÷"

/** + − × ÷ con prioridad, decimales y % (200+10% = 220). Null si no se puede (por ejemplo, ÷ 0). */
fun evalMath(s: String): Double? {
    val toks = Regex("[0-9.]+%?|[+−×÷]").findAll(s.trimEnd(*OPS.toCharArray())).map { it.value }.toList()
    if (toks.none { it[0] !in OPS }) return null
    var total = 0.0
    var term = 0.0
    var op = '+'
    var seen = false
    for (t in toks) {
        if (t[0] in OPS) { op = t[0]; continue }
        val pct = t.endsWith('%')
        val x = t.trimEnd('%').toDoubleOrNull() ?: return null
        when (op) {
            '+', '−' -> { total += term; val v = if (pct && seen) total * x / 100 else if (pct) x / 100 else x; term = if (op == '+') v else -v }
            '×' -> term *= if (pct) x / 100 else x
            else -> { val v = if (pct) x / 100 else x; if (v == 0.0) return null; term /= v }
        }
        seen = true
    }
    return (total + term).takeIf { it.isFinite() }
}

fun fmtNum(v: Double): String = java.math.BigDecimal(v).round(java.math.MathContext(12)).stripTrailingZeros().toPlainString()
    .let { if (it == "-0") "0" else it }.replace('-', '−')

fun pressMath(s: String, key: String): String {
    val last = s.lastOrNull()
    val token = s.takeLastWhile { it.isDigit() || it == '.' }
    if (s.length >= 40 && key != "⌫") return s
    return when {
        key == "⌫" -> s.dropLast(1)
        key == "." -> if ('.' in token || last == '%') s else if (token.isEmpty()) s + "0." else "$s."
        key == "%" -> if (last?.isDigit() == true) "$s%" else s
        key in OPS -> when {
            s.isEmpty() || s == "−" -> if (key == "−") "−" else s
            last!! in OPS -> s.dropLast(1) + key
            last == '.' -> s.dropLast(1) + key
            else -> s + key
        }
        last == '%' || token.length >= 12 -> s
        key == "00" -> if (token.isNotEmpty() && token != "0") s + "00" else s
        token == "0" -> s.dropLast(1) + key
        else -> s + key
    }
}
