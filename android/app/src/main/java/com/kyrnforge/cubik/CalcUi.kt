package com.kyrnforge.cubik

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Modo Rápido: calculadora, historial y detalle.

private val R12 = RoundedCornerShape(12.dp)
private val R14 = RoundedCornerShape(14.dp)
private val R16 = RoundedCornerShape(16.dp)

/** Colores de P1…P4: se distinguen al sol y no juntan rojo con verde. */
val PRODUCT_COLORS = listOf(Color(0xFFC28B46), Color(0xFF2C3338), Color(0xFF2F7D8C), Color(0xFF6B7F2A))

const val HISTORY_ICON = "M3 12a9 9 0 1 0 3-6.7M3 4v4h4M12 7v5l3 2"
const val TRUCK_ICON = "M2 9h12v8H2zM14 11h4l3 3v3h-7zM4 9V6h4v3M8 9V6h4v3M4 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M15 18a2 2 0 1 0 4 0a2 2 0 1 0 -4 0"
const val KEYPAD_ICON = "M5 2h14a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1zM7.5 5h9v4h-9zM8 13h.5M12 13h.5M16 13h.5M8 17h.5M12 17h.5M16 17h.5"
private const val BACK_ICON = "M15 5l-7 7 7 7"

private sealed interface Screen {
    data object Calc : Screen
    data object History : Screen
    data class Detail(val id: String) : Screen
}

private data class Ask(val text: String, val ok: String, val action: () -> Unit)

@Composable
fun TopIcon(d: String, desc: String, onClick: () -> Unit) = Box(
    Modifier.size(52.dp).clip(R14).background(C.card).border(2.dp, C.line, R14)
        .semantics { contentDescription = desc; role = Role.Button }.clickable(onClick = onClick),
    contentAlignment = Alignment.Center
) { Icon(d, 28.dp, view = 24f) }

@Composable
private fun TopBar(title: String, sub: String, left: @Composable () -> Unit, right: @Composable () -> Unit) =
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        left()
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(title, style = cond(24.sp, spacing = 0.08.em))
            BasicText(sub, style = body(11.sp, C.traba, FontWeight.Bold).copy(letterSpacing = 0.12.em))
        }
        right()
    }

@Composable
fun QuickApp(q: QuickStore) {
    var screen by remember { mutableStateOf<Screen>(Screen.Calc) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    var toast by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf("") }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) toast = if (writeText(ctx, uri, pending)) "Respaldo guardado." else "No se pudo guardar el archivo."
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val list = readText(ctx, uri)?.let(::parseBackup)
        toast = when {
            list == null -> "Ese archivo no es un historial de Cubik."
            else -> q.import(list).let { if (it == 0) "Esos cálculos ya estaban en el historial." else "Se agregaron $it ${plural(it, "cálculo", "cálculos")}." }
        }
    }
    fun export(entries: List<Entry>, name: String) {
        pending = exportJson(entries)
        saver.launch("cubik-historial-$name.json")
    }
    LaunchedEffect(toast) { if (toast.isNotEmpty()) { delay(3500); toast = "" } }

    BackHandler(screen != Screen.Calc || ask != null) {
        when {
            ask != null -> ask = null
            screen is Screen.Detail -> screen = Screen.History
            else -> screen = Screen.Calc
        }
    }

    Box(Modifier.fillMaxSize().background(C.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize().widthIn(max = 560.dp).align(Alignment.TopCenter)) {
            when (val s = screen) {
                Screen.Calc -> CalcScreen(q, onHistory = { screen = Screen.History })
                Screen.History -> HistoryScreen(
                    q,
                    onBack = { screen = Screen.Calc },
                    onOpen = { screen = Screen.Detail(it.id) },
                    onExport = ::export,
                    onImport = { opener.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    onAsk = { ask = it }
                )
                is Screen.Detail -> {
                    val e = q.history.firstOrNull { it.id == s.id }
                    if (e == null) LaunchedEffect(Unit) { screen = Screen.History }
                    else DetailScreen(
                        e,
                        onBack = { screen = Screen.History },
                        onUse = { q.open(e); screen = Screen.Calc },
                        onExport = { export(listOf(e), dayKey(e.ts)) },
                        onDelete = { ask = Ask("¿Borrar este cálculo?", "Borrar") { q.delete(setOf(e.id)); screen = Screen.History } }
                    )
                }
            }
        }
        if (toast.isNotEmpty()) Box(
            Modifier.align(Alignment.BottomCenter).padding(16.dp).padding(bottom = 72.dp).background(C.ink, R12).padding(horizontal = 16.dp, vertical = 12.dp)
        ) { BasicText(toast, style = body(16.sp, Color.White, FontWeight.Bold)) }
        ask?.let { a -> ConfirmDialog(a.text, a.ok, onOk = { a.action(); ask = null }, onCancel = { ask = null }) }
    }
}

private fun writeText(ctx: Context, uri: Uri, text: String): Boolean = try {
    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null
} catch (e: Exception) { false }

private fun readText(ctx: Context, uri: Uri): String? = try {
    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
} catch (e: Exception) { null }

@Composable
private fun ConfirmDialog(text: String, ok: String, onOk: () -> Unit, onCancel: () -> Unit) = Box(
    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
    contentAlignment = Alignment.Center
) {
    Column(
        Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth().background(C.card, R16).border(2.dp, C.line, R16)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BasicText(text, style = cond(24.sp))
        BasicText("Esto no se puede deshacer.", style = body(15.sp, C.muted))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Cancelar", Modifier.weight(1f), onClick = onCancel)
            Btn(ok, Modifier.weight(1f), bg = C.red, onClick = onOk)
        }
    }
}

fun press(s: String, key: String): String {
    val last = s.lastOrNull()
    val digitLast = last?.isDigit() == true
    val token = s.takeLastWhile { it.isDigit() }
    val e = splitExpr(s)
    val inProducts = e.products != null
    if (s.length >= 40 && key != "⌫") return s
    return when (key) {
        "⌫" -> s.dropLast(1)
        "00" -> if (token.isNotEmpty() && token != "0" && token.length <= 2) s + "00" else s
        "," -> when {
            !digitLast -> s
            !inProducts && e.head.size >= 3 -> s
            inProducts && e.products!!.size >= MAX_PRODUCTS -> s
            else -> "$s,"
        }
        "÷" -> if (digitLast && !inProducts) "$s÷" else s
        "+" -> if (digitLast && (inProducts || e.head.size == 1)) "$s+" else s
        else -> when {
            token.length >= 4 -> s
            token == "0" -> s.dropLast(1) + key
            else -> s + key
        }
    }
}

@Composable
private fun ColumnScope.CalcScreen(q: QuickStore, onHistory: () -> Unit) {
    TopBar("CUBIK", "RÁPIDO",
        left = { TopIcon(HISTORY_ICON, "Historial", onHistory) },
        right = { TopIcon(TRUCK_ICON, "Pasar al modo completo") { q.switchQuick(false) } })
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val padH = maxHeight
        val result = if (q.shown) calculate(q.input, q.kg, q.truck) else null
        var sel by remember { mutableStateOf<Int?>(null) }
        var fresh by remember { mutableStateOf(false) }
        if (sel != null && sel!! >= numberSpans(q.input).size) sel = null
        // Teclado: casi la mitad de la pantalla y siempre del mismo alto para que las teclas no se muevan.
        val keyH = ((padH * 0.47f - 64.dp) / 5).coerceIn(42.dp, 76.dp)
        Column(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val dispH = maxHeight
                Column(
                    Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(rememberScrollState(), reverseScrolling = true)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
                    horizontalAlignment = Alignment.End
                ) {
                    Display(q.input, compact = result != null && dispH < 320.dp, sel = sel) { k ->
                        sel = if (k == sel) null else k
                        fresh = true
                    }
                    when (result) {
                        is CalcResult.Ok -> ResultCard(result)
                        is CalcResult.Error -> ErrorCard(result)
                        null -> {}
                    }
                }
            }
            Keypad(q, keyH, sel, fresh) { k, f -> sel = k; fresh = f }
        }
    }
}

@Composable
private fun Display(input: String, compact: Boolean, sel: Int?, onTap: (Int?) -> Unit) {
    val e = splitExpr(input)
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val labels = listOf("BULTOS", "FILAS", "ANCHO").take(e.head.size.coerceAtLeast(1))
        labels.forEach { BasicText(it, style = body(12.sp, C.muted, FontWeight.Bold).copy(letterSpacing = 0.08.em)) }
        if (e.products != null) BasicText("PRODUCTOS", style = body(12.sp, C.traba, FontWeight.Bold).copy(letterSpacing = 0.08.em))
    }
    val size = when {
        input.length <= 8 -> 66.sp
        input.length <= 11 -> 56.sp
        input.length <= 14 -> 46.sp
        input.length <= 18 -> 38.sp
        else -> 32.sp
    }.let { if (compact && it.value > 34f) 34.sp else it } * textScale()
    if (input.isEmpty()) {
        BasicText("0", style = cond(66.sp, C.line).copy(textAlign = TextAlign.End))
        BasicText("Escriba bultos,filas,ancho y toque =", style = body(14.sp, C.muted))
    } else {
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        BasicText(
            colored(input, sel?.let { numberSpans(input).getOrNull(it) }),
            style = cond(size).copy(textAlign = TextAlign.End, lineHeight = size * 1.05f),
            onTextLayout = { layout = it },
            modifier = Modifier.semantics { contentDescription = "Escrito: $input. Toque un número para cambiarlo." }
                .pointerInput(input) {
                    detectTapGestures { pos -> layout?.let { onTap(numberAt(input, it.getOffsetForPosition(pos))) } }
                }
        )
        if (sel != null) BasicText("Cambiando ${numberName(input, sel)} · toque = para calcular", style = body(13.sp, C.traba, FontWeight.Bold))
        else if (!compact) BasicText("Toque un número para cambiarlo", style = body(13.sp, C.muted, FontWeight.Bold))
    }
}

private fun colored(input: String, sel: IntRange? = null) = buildAnnotatedString {
    input.forEachIndexed { i, ch ->
        when {
            sel != null && i in sel -> withStyle(SpanStyle(background = C.sel, color = C.trabaDk, textDecoration = TextDecoration.Underline)) { append(ch) }
            else -> when (ch) {
            ',' -> withStyle(SpanStyle(color = C.kraftDk)) { append(ch) }
            '÷', '+' -> withStyle(SpanStyle(color = C.traba)) { append(ch) }
            else -> append(ch)
            }
        }
    }
}

@Composable
private fun ResultBox(border: Color = C.line, bg: Color = C.card, content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().background(bg, R14).border(2.dp, border, R14).padding(horizontal = 12.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
    content = content
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultCard(r: CalcResult.Ok) = ResultBox {
    val s = r.load
    val b = s.balance()
    FlowRow(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        BasicText("${s.truck.label} · ${fmtT(s.tons)} t · ${s.kg} kg", style = body(14.sp, C.muted, FontWeight.Bold), softWrap = false, modifier = Modifier.padding(end = 8.dp))
        BasicText(
            if (b.ok) "Parejo ${b.front}/${b.back} ✓" else "Recostado ${b.front}/${b.back}",
            style = body(14.sp, if (b.ok) C.green else C.red, FontWeight.Bold), softWrap = false
        )
    }
    if (s.over) Warn("Pasa de ${fmtT(s.truck.tons)} t ${s.truck.deName} (máx. ${s.maxBags} bultos)")
    val maxTop = s.layout().maxOf { it.height }
    if (maxTop > s.truck.techo) Warn("$maxTop pisos pasan la baranda (${s.truck.techo})")
    val ts = textScale()
    val lines = s.rowLines()
    if (r.products.isEmpty()) lines.forEach { RowText(it, legend = false, size = 20.sp * ts) }
    else {
        Divider()
        val what = s.productText(r.products)
        r.products.forEachIndexed { i, p ->
            Column(Modifier.padding(vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(PRODUCT_COLORS[i % PRODUCT_COLORS.size], RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(8.dp))
                    BasicText(productLabel(i, p), style = cond(17.sp * ts, C.muted, FontWeight.Bold))
                    if (p.rest) {
                        Spacer(Modifier.width(6.dp))
                        BasicText("RESTO", style = body(11.sp, C.traba, FontWeight.Bold),
                            modifier = Modifier.border(1.5.dp, C.traba, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                }
                // Solo se corta la línea en los "+", nunca dentro de "en fila 5".
                BasicText(what[i].split(" + ").joinToString(" + ") { it.replace(' ', ' ') }, style = cond(19.sp * ts, weight = FontWeight.Bold), modifier = Modifier.padding(start = 22.dp))
            }
        }
        Divider()
        lines.filter { it.n > 0 }.forEach { RowText(it, legend = true, size = 17.sp * ts) }
    }
}

@Composable
private fun RowText(line: RowLine, legend: Boolean, size: TextUnit) = BasicText(
    buildAnnotatedString {
        append("${line.count} ${plural(line.count, "fila", "filas")}" + if (legend) " de " else " → ")
        append(line.main)
        line.top?.let { append(" + "); withStyle(SpanStyle(color = C.traba)) { append(it) } }
    },
    style = cond(size, weight = FontWeight.Bold)
)

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(2.dp).background(C.line))

@Composable
private fun textScale() = (LocalConfiguration.current.screenWidthDp / 380f).coerceIn(0.85f, 1f)

@Composable
private fun Warn(text: String) = BasicText("⚠ $text", style = body(14.sp, C.red, FontWeight.Bold))

@Composable
private fun ErrorCard(r: CalcResult.Error) = ResultBox(C.red, C.redBg) {
    BasicText(r.text, style = cond(20.sp, C.red, FontWeight.Bold))
    BasicText(r.hint, style = body(14.sp, C.muted))
}

private enum class KeyKind { NUM, OP, FN, CLEAR, EQ }

@Composable
private fun RowScope.Key(label: String, kind: KeyKind, h: Dp, sub: String? = null, desc: String = label, span: Float = 1f, size: TextUnit? = null, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val (bg, edge, fg) = when (kind) {
        KeyKind.NUM -> Triple(C.card, C.line, C.ink)
        KeyKind.CLEAR -> Triple(C.card, C.line, C.red)
        KeyKind.OP -> Triple(C.steel, C.ink, Color.White)
        KeyKind.FN -> Triple(C.sel, C.kraftDk, C.ink)
        KeyKind.EQ -> Triple(C.traba, C.trabaDk, Color.White)
    }
    Box(
        Modifier.weight(span).height(h).offset(y = if (down) 2.dp else 0.dp)
            .background(edge, R16).padding(bottom = if (down) 1.dp else 3.dp)
            .clip(R16).background(bg).border(2.dp, edge, R16)
            .semantics { contentDescription = desc; role = Role.Button }
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (label in SYMBOLS) Symbol(label, fg, if (h < 56.dp) 24.dp else 30.dp)
            else {
                val tight = sub != null && h < 56.dp
                val sz = (size ?: if (h < 56.dp) 28.sp else 34.sp) * (if (tight) 0.82f else 1f)
                BasicText(label, style = cond(sz, fg).copy(lineHeight = sz * (if (tight) 1f else 1.15f)))
            }
            if (sub != null) BasicText(sub, style = body(if (h < 56.dp) 9.sp else 10.sp, C.kraftEdge, FontWeight.Bold)
                .copy(letterSpacing = 0.08.em, lineHeight = if (h < 56.dp) 10.sp else 13.sp))
        }
    }
}

private val SYMBOLS = setOf("÷", "+", ",", "=")

/** Los signos se dibujan para que se vean grandes y centrados con cualquier fuente. */
@Composable
private fun Symbol(k: String, color: Color, size: Dp) = Canvas(Modifier.size(size)) {
    val u = this.size.width
    val w = u * 0.13f
    val c = u / 2
    fun bar(y: Float) = drawLine(color, Offset(u * 0.12f, y), Offset(u * 0.88f, y), w, StrokeCap.Round)
    when (k) {
        "+" -> { bar(c); drawLine(color, Offset(c, u * 0.12f), Offset(c, u * 0.88f), w, StrokeCap.Round) }
        "=" -> { bar(u * 0.36f); bar(u * 0.64f) }
        "÷" -> { bar(c); drawCircle(color, w * 0.85f, Offset(c, u * 0.2f)); drawCircle(color, w * 0.85f, Offset(c, u * 0.8f)) }
        "," -> {
            drawCircle(color, u * 0.13f, Offset(c, u * 0.62f))
            drawLine(color, Offset(c + u * 0.1f, u * 0.64f), Offset(c - u * 0.08f, u * 0.92f), w * 0.9f, StrokeCap.Round)
        }
    }
}

@Composable
private fun Keypad(q: QuickStore, h: Dp, sel: Int?, fresh: Boolean, onSel: (Int?, Boolean) -> Unit) {
    fun t(k: String) {
        val n = if (sel != null && (k == "⌫" || k.all { it.isDigit() })) editNumber(q.input, sel, k, fresh).also { onSel(sel, false) }
        else { onSel(null, false); press(q.input, k) }
        if (n != q.input) q.type(n)
    }
    val truckLabel = when (q.truck) { null -> "AUTO"; Truck.DOBLE -> "DOBLE"; else -> q.truck!!.label.uppercase() }
    Column(
        Modifier.fillMaxWidth().background(C.win.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        val gap = Arrangement.spacedBy(9.dp)
        Row(horizontalArrangement = gap) {
            Key("C", KeyKind.CLEAR, h, desc = "Borrar todo") { onSel(null, false); q.clear() }
            Key("⌫", KeyKind.NUM, h, desc = "Borrar uno") { t("⌫") }
            Key("${q.kg}", KeyKind.FN, h, sub = "KG", desc = "Peso del bulto: ${q.kg} kilos", size = 26.sp) { q.cycleKg() }
            Key(truckLabel, KeyKind.FN, h, sub = "CAMIÓN", desc = "Camión: ${q.truck?.label ?: "automático"}",
                size = if (truckLabel.length > 6) 17.sp else 22.sp) { q.cycleTruck() }
        }
        Row(horizontalArrangement = gap) {
            listOf("7", "8", "9").forEach { Key(it, KeyKind.NUM, h) { t(it) } }
            Key("÷", KeyKind.OP, h, desc = "Dividir en productos") { t("÷") }
        }
        Row(horizontalArrangement = gap) {
            listOf("4", "5", "6").forEach { Key(it, KeyKind.NUM, h) { t(it) } }
            Key("+", KeyKind.OP, h, desc = "Sumar") { t("+") }
        }
        Row(horizontalArrangement = gap) {
            listOf("1", "2", "3").forEach { Key(it, KeyKind.NUM, h) { t(it) } }
            Key(",", KeyKind.FN, h, desc = "Coma: separa bultos, filas, ancho y productos") { t(",") }
        }
        Row(horizontalArrangement = gap) {
            Key("0", KeyKind.NUM, h) { t("0") }
            Key("00", KeyKind.NUM, h) { t("00") }
            Key("=", KeyKind.EQ, h, span = 2.09f, desc = "Calcular") { onSel(null, false); q.equals() }
        }
    }
}

@Composable
private fun Folder(open: Boolean) = Canvas(Modifier.size(30.dp, 26.dp)) {
    val k = size.width / 24f
    val p = Path().apply {
        moveTo(1 * k, 3 * k); quadraticBezierTo(1 * k, 1 * k, 3 * k, 1 * k); lineTo(9 * k, 1 * k); lineTo(11 * k, 4 * k)
        lineTo(21 * k, 4 * k); quadraticBezierTo(23 * k, 4 * k, 23 * k, 6 * k); lineTo(23 * k, 17 * k)
        quadraticBezierTo(23 * k, 19 * k, 21 * k, 19 * k); lineTo(3 * k, 19 * k); quadraticBezierTo(1 * k, 19 * k, 1 * k, 17 * k); close()
    }
    drawPath(p, if (open) C.kraftDk else C.kraft)
    drawPath(p, C.kraftEdge, style = Stroke(1.2f * k))
}

@Composable
private fun SmallBtn(text: String, modifier: Modifier = Modifier, fg: Color = C.ink, edge: Color = C.line, bg: Color = C.card, onClick: () -> Unit) = Box(
    modifier.heightIn(min = 48.dp).clip(R12).background(bg).border(2.dp, edge, R12)
        .semantics { role = Role.Button }.clickable(onClick = onClick).padding(horizontal = 8.dp),
    contentAlignment = Alignment.Center
) { BasicText(text.uppercase(), style = cond(16.sp, fg, spacing = 0.03.em).copy(textAlign = TextAlign.Center)) }

private fun Entry.title(): String = when (val r = result()) {
    is CalcResult.Ok -> "${r.load.n} bultos · ${r.load.kg} kg"
    is CalcResult.Error -> input
}

private fun Entry.subtitle(): String = when (val r = result()) {
    is CalcResult.Ok -> "${r.load.truck.label} · ${fmtT(r.load.tons)} t · ${r.load.l} filas" +
        if (r.products.isNotEmpty()) " · ${r.products.size} productos" else ""
    is CalcResult.Error -> r.text
}

@Composable
private fun ColumnScope.HistoryScreen(
    q: QuickStore,
    onBack: () -> Unit,
    onOpen: (Entry) -> Unit,
    onExport: (List<Entry>, String) -> Unit,
    onImport: () -> Unit,
    onAsk: (Ask) -> Unit
) {
    val days = q.history.days()
    var open by remember { mutableStateOf(setOfNotNull(days.firstOrNull()?.key)) }
    TopBar("HISTORIAL", "SOLO MODO RÁPIDO",
        left = { TopIcon(BACK_ICON, "Volver a la calculadora", onBack) },
        right = { Spacer(Modifier.size(52.dp)) })
    Column(
        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (days.isEmpty()) Column(
            Modifier.fillMaxWidth().background(C.card, R14).border(2.dp, C.line, R14).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BasicText("Todavía no hay cálculos", style = cond(22.sp))
            BasicText("Cada vez que toque = en la calculadora se guarda aquí, en la carpeta del día.", style = body(15.sp, C.muted))
        }
        days.forEach { d ->
            val isOpen = d.key in open
            Row(
                Modifier.fillMaxWidth().clip(R14).background(if (isOpen) Color(0xFFFFFAF1) else C.card)
                    .border(2.dp, if (isOpen) C.kraftDk else C.line, R14)
                    .clickable { open = if (isOpen) open - d.key else open + d.key }.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Folder(isOpen)
                Spacer(Modifier.width(12.dp))
                BasicText(d.label, style = cond(21.sp), modifier = Modifier.weight(1f))
                BasicText("${d.entries.size}", style = body(13.sp, C.kraftEdge, FontWeight.Bold),
                    modifier = Modifier.background(C.sel, RoundedCornerShape(9.dp)).padding(horizontal = 9.dp, vertical = 3.dp))
                Spacer(Modifier.width(10.dp))
                BasicText(if (isOpen) "▾" else "▸", style = cond(20.sp))
            }
            if (isOpen) {
                d.entries.forEach { e ->
                    Row(
                        Modifier.padding(start = 22.dp).fillMaxWidth().clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                            .background(C.card).clickable { onOpen(e) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(3.dp).height(58.dp).background(C.kraft))
                        BasicText(hourTxt(e.ts), style = body(13.sp, C.muted, FontWeight.Bold), modifier = Modifier.padding(start = 10.dp).width(62.dp))
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            BasicText(e.title(), style = cond(19.sp))
                            BasicText(e.subtitle(), style = body(13.sp, C.muted))
                        }
                        BasicText("›", style = cond(26.sp, C.muted), modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
                Row(Modifier.padding(start = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallBtn("Exportar día", Modifier.weight(1f)) { onExport(d.entries, d.key) }
                    SmallBtn("Borrar día", Modifier.weight(1f), C.red, Color(0xFFF0B4AE)) {
                        onAsk(Ask("¿Borrar los ${d.entries.size} cálculos de ${d.label.lowercase()}?", "Borrar") { q.delete(d.entries.map { it.id }.toSet()) })
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallBtn("Importar", Modifier.weight(1f), onClick = onImport)
        SmallBtn("Exportar todo", Modifier.weight(1f)) { if (q.history.isNotEmpty()) onExport(q.history, "todo-${dayKey(System.currentTimeMillis())}") }
        SmallBtn("Borrar todo", Modifier.weight(1f), C.red, Color(0xFFF0B4AE)) {
            if (q.history.isNotEmpty()) onAsk(Ask("¿Borrar todo el historial?", "Borrar todo") { q.deleteAll() })
        }
    }
}

@Composable
private fun ColumnScope.DetailScreen(e: Entry, onBack: () -> Unit, onUse: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    val r = e.result()
    var load by remember(e) { mutableStateOf((r as? CalcResult.Ok)?.load) }
    TopBar("CÁLCULO", "${dayLabel(dayKey(e.ts))} · ${hourTxt(e.ts)}".uppercase(),
        left = { TopIcon(BACK_ICON, "Volver al historial", onBack) },
        right = { Spacer(Modifier.size(52.dp)) })
    Column(
        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BasicText(colored(e.input), style = cond(if (e.input.length > 16) 32.sp else 40.sp).copy(textAlign = TextAlign.End), modifier = Modifier.fillMaxWidth())
        when (r) {
            is CalcResult.Ok -> {
                ResultCard(r)
                load?.let { l -> ViewsCard(l) { f -> load = f(l) } }
                Btn("Compartir por WhatsApp", Modifier.fillMaxWidth(), height = 60.dp, size = 22.sp, bg = C.green,
                    icon = { Icon(WA_ICON, 24.dp, Color.White, 24f, filled = true) }) { shareWhatsApp(ctx, r.load.quickShareText(r.products)) }
            }
            is CalcResult.Error -> ErrorCard(r)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallBtn("Usar en calculadora", Modifier.weight(1.4f), onClick = onUse)
            SmallBtn("Exportar", Modifier.weight(1f), onClick = onExport)
            SmallBtn("Borrar", Modifier.weight(1f), C.red, Color(0xFFF0B4AE), onClick = onDelete)
        }
    }
}
