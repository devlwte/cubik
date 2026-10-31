package com.kyrnforge.cubik

// Formato: bultos,filas,ancho÷producto1,producto2,…  (filas, ancho y productos son opcionales).

const val MAX_PRODUCTS = 4
const val MAX_BAGS = 3000

val TRUCK_CYCLE: List<Truck?> = listOf(null) + Truck.entries

fun truckKey(t: Truck?) = t?.name?.lowercase() ?: "auto"
fun truckOf(key: String?): Truck? = Truck.entries.firstOrNull { it.name.equals(key, ignoreCase = true) }

data class Product(val n: Int, val rest: Boolean = false)

sealed interface CalcResult {
    data class Ok(val load: Load, val products: List<Product>) : CalcResult
    data class Error(val text: String, val hint: String) : CalcResult
}

data class Expr(val head: List<String>, val products: List<String>?)

fun splitExpr(input: String): Expr {
    val i = input.indexOf('÷')
    val head = (if (i < 0) input else input.substring(0, i)).split(',')
    val prods = if (i < 0) null else input.substring(i + 1).split(',')
    return Expr(head, prods)
}

private fun sumOf(part: String): Int? {
    val nums = part.split('+').filter { it.isNotEmpty() }
    if (nums.isEmpty()) return null
    return nums.sumOf { it.toIntOrNull() ?: return null }
}

fun calculate(input: String, kg: Int, truck: Truck?): CalcResult {
    val e = splitExpr(input)
    val n = sumOf(e.head[0]) ?: return CalcResult.Error("Escriba los bultos", "Ejemplo: 300,8,5")
    if (n < 1) return CalcResult.Error("Los bultos deben ser más de 0", "Ejemplo: 300,8,5")
    if (n > MAX_BAGS) return CalcResult.Error("Máximo $MAX_BAGS bultos", "Revise el total de bultos.")
    val filas = e.head.getOrNull(1)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
    if (filas != null && filas !in 3..16) return CalcResult.Error("Las filas van de 3 a 16", "Usted escribió $filas filas.")
    val ancho = e.head.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
    if (ancho != null && ancho !in 3..5) return CalcResult.Error("El ancho va de 3 a 5 bultos", "Usted escribió $ancho de ancho.")

    var base = Load(n = n, kg = kg, w = ancho ?: DEFAULT_WIDTH, wManual = ancho != null, autoTruck = truck == null,
        truck = truck ?: Truck.DOBLE, l = (truck ?: Truck.DOBLE).rows).withAutoTruck()
    base = base.copy(l = filas ?: base.truck.rows).distributed()

    val written = e.products?.filter { it.isNotEmpty() }?.map { sumOf(it) ?: 0 } ?: return CalcResult.Ok(base, emptyList())
    if (written.isEmpty()) return CalcResult.Ok(base, emptyList())
    if (written.any { it < 1 }) return CalcResult.Error("Cada producto debe tener bultos", "Quite el producto en 0.")
    if (written.size > MAX_PRODUCTS) return CalcResult.Error("Máximo $MAX_PRODUCTS productos", "Junte algunos productos.")
    val s = written.sum()
    val sumTxt = written.joinToString(" + ")
    if (written.size == 1 && written[0] == n) return CalcResult.Error("Para dividir se necesitan al menos 2 productos", "Escriba menos de $n o quite el ÷.")
    if (s > n) return if (written.size == 1) CalcResult.Error("$s bultos pasa del total ($n)", "El producto debe ser menor que el total.")
        else CalcResult.Error("$sumTxt = $s bultos, pasa del total ($n)", "Revise los productos: no pueden pasar de $n.")
    if (s == n) return CalcResult.Ok(base, written.map { Product(it) })
    // Lo que sobra siempre pasa a ser otro producto (RESTO).
    if (written.size == MAX_PRODUCTS) return CalcResult.Error("Sobran ${n - s} bultos y ya hay $MAX_PRODUCTS productos", "Junte algunos productos.")
    return CalcResult.Ok(base, written.map { Product(it) } + Product(n - s, rest = true))
}

fun numberSpans(input: String): List<IntRange> = Regex("[0-9]+").findAll(input).map { it.range }.toList()

fun numberAt(input: String, offset: Int): Int? =
    numberSpans(input).indexOfFirst { offset in it.first..it.last + 1 }.takeIf { it >= 0 }

fun numberName(input: String, k: Int): String {
    val r = numberSpans(input).getOrNull(k) ?: return ""
    val before = input.substring(0, r.first)
    val div = before.indexOf('÷')
    if (div >= 0) return "el producto ${before.substring(div).count { it == ',' } + 1}"
    return when (before.count { it == ',' }) { 0 -> "los bultos"; 1 -> "las filas"; else -> "el ancho" }
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
