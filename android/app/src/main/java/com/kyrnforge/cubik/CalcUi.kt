package com.kyrnforge.cubik

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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

    Box(Modifier.fillMaxSize().background(if (screen == Screen.Calc) K.bg else C.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
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
    val digitLast = s.lastOrNull()?.isDigit() == true
    val token = s.takeLastWhile { it.isDigit() }
    val e = splitExpr(s)
    val inBultos = e.filas == null && e.ancho == null && e.products == null
    if (s.length >= 40 && key != "⌫") return s
    return when (key) {
        "⌫" -> s.dropLast(1)
        "00" -> if (token.isNotEmpty() && token != "0" && token.length <= 2) s + "00" else s
        "÷" -> if (digitLast && inBultos) "$s÷" else s
        "×" -> if (digitLast && e.ancho == null && e.products == null) "$s×" else s
        "," -> if (digitLast && (e.products?.size ?: 0) < MAX_PRODUCTS) "$s," else s
        "+" -> if (digitLast && (inBultos || e.products != null)) "$s+" else s
        else -> when {
            token.length >= 4 -> s
            token == "0" -> s.dropLast(1) + key
            else -> s + key
        }
    }
}

@Composable
private fun DarkIcon(d: String, desc: String, onClick: () -> Unit) = Box(
    Modifier.size(48.dp).clip(R14).background(K.num)
        .semantics { contentDescription = desc; role = Role.Button }.clickable(onClick = onClick),
    contentAlignment = Alignment.Center
) { Icon(d, 26.dp, K.ink, view = 24f) }

/** Interruptor CALCULADORA | BULTOS. */
@Composable
private fun RowScope.ModeSwitch(plain: Boolean, onChange: (Boolean) -> Unit) = Row(
    Modifier.weight(1f).padding(horizontal = 10.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(K.num).padding(4.dp)
) {
    listOf(true to "CALCULADORA", false to "BULTOS").forEach { (v, label) ->
        val on = plain == v
        Box(
            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(18.dp)).background(if (on) K.op else Color.Transparent)
                .semantics { role = Role.Tab; contentDescription = label.lowercase() }.clickable { onChange(v) },
            contentAlignment = Alignment.Center
        ) { BasicText(label, style = cond(15.sp, if (on) Color.White else K.muted, spacing = 0.06.em)) }
    }
}

@Composable
private fun ColumnScope.CalcScreen(q: QuickStore, onHistory: () -> Unit) {
    val view = LocalView.current
    val window = (LocalContext.current as? Activity)?.window
    // Iconos claros en la barra del sistema mientras se ve la calculadora oscura.
    DisposableEffect(window) {
        val c = window?.let { WindowCompat.getInsetsController(it, view) }
        c?.isAppearanceLightStatusBars = false
        c?.isAppearanceLightNavigationBars = false
        onDispose { c?.isAppearanceLightStatusBars = true; c?.isAppearanceLightNavigationBars = true }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        DarkIcon(HISTORY_ICON, "Historial", onHistory)
        ModeSwitch(q.plain, q::usePlain)
        DarkIcon(TRUCK_ICON, "Pasar al modo completo") { q.switchQuick(false) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val padH = maxHeight
        val result = if (!q.plain && q.shown) calculate(q.input, q.kg, q.truck) else null
        var sel by remember { mutableStateOf<Int?>(null) }
        var fresh by remember { mutableStateOf(false) }
        if (sel != null && sel!! >= numberSpans(q.input).size) sel = null
        // Teclado: casi la mitad de la pantalla y siempre del mismo alto para que las teclas no se muevan.
        val keyH = ((padH * 0.47f - 64.dp) / 5).coerceIn(42.dp, 76.dp)
        Column(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val dispH = maxHeight
                // Lo escrito siempre se ve; si el resultado no cabe, solo el resultado se desplaza.
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
                    horizontalAlignment = Alignment.End
                ) {
                    if (q.plain) MathDisplay(q.math, q.mathPrev)
                    else {
                        Display(q.input, compact = result != null && dispH < 420.dp, sel = sel) { k ->
                            sel = if (k == sel) null else k
                            fresh = true
                        }
                        val scroll = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                        when (result) {
                            is CalcResult.Ok -> Box(scroll) { ResultCard(result, DARK) }
                            is CalcResult.Error -> Box(scroll) { ErrorCard(result, DARK) }
                            null -> {}
                        }
                    }
                }
            }
            if (q.plain) MathKeypad(q, keyH) else Keypad(q, keyH, sel, fresh) { k, f -> sel = k; fresh = f }
        }
    }
}

private fun inputSize(len: Int) = when {
    len <= 8 -> 66.sp
    len <= 11 -> 56.sp
    len <= 14 -> 46.sp
    len <= 18 -> 38.sp
    else -> 32.sp
}

@Composable
private fun MathDisplay(input: String, prev: String) {
    if (prev.isNotEmpty()) BasicText(colored(prev, dark = true), style = cond(26.sp, K.muted, FontWeight.Bold).copy(textAlign = TextAlign.End))
    val size = inputSize(input.length) * textScale()
    BasicText(
        colored(input.ifEmpty { "0" }, dark = true),
        style = cond(size, K.ink, FontWeight.Bold).copy(textAlign = TextAlign.End, lineHeight = size * 1.05f),
        modifier = Modifier.semantics { contentDescription = "Escrito: ${input.ifEmpty { "0" }}" }
    )
    // Resultado en vivo mientras se escribe, como las calculadoras del teléfono.
    if (prev.isEmpty() && input.any { it in OPS || it == '%' }) evalMath(input)?.let {
        BasicText("= ${fmtNum(it)}", style = cond(30.sp, K.muted, FontWeight.Bold).copy(textAlign = TextAlign.End))
    }
}

@Composable
private fun Display(input: String, compact: Boolean, sel: Int?, onTap: (Int?) -> Unit) {
    val e = splitExpr(input)
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val labels = listOfNotNull("BULTOS", e.filas?.let { "FILAS" }, e.ancho?.let { "ANCHO" })
        labels.forEach { BasicText(it, style = body(12.sp, K.muted, FontWeight.Bold).copy(letterSpacing = 0.08.em)) }
        if (e.products != null) BasicText("PRODUCTOS", style = body(12.sp, K.op, FontWeight.Bold).copy(letterSpacing = 0.08.em))
    }
    val size = inputSize(input.length).let { if (compact && it.value > 34f) 34.sp else it } * textScale()
    if (input.isEmpty()) {
        BasicText("0", style = cond(66.sp, K.line).copy(textAlign = TextAlign.End))
        BasicText("Bultos ÷ filas × ancho , productos", style = body(14.sp, K.muted))
    } else {
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        BasicText(
            colored(input, sel?.let { numberSpans(input).getOrNull(it) }, dark = true),
            style = cond(size, K.ink).copy(textAlign = TextAlign.End, lineHeight = size * 1.05f),
            onTextLayout = { layout = it },
            modifier = Modifier.semantics { contentDescription = "Escrito: $input. Toque un número para cambiarlo." }
                .pointerInput(input) {
                    detectTapGestures { pos -> layout?.let { onTap(numberAt(input, it.getOffsetForPosition(pos))) } }
                }
        )
        if (sel != null) BasicText("Cambiando ${numberName(input, sel)} · toque = para calcular", style = body(13.sp, K.op, FontWeight.Bold))
        else if (!compact) BasicText("Toque un número para cambiarlo", style = body(13.sp, K.muted, FontWeight.Bold))
    }
}

private fun colored(input: String, sel: IntRange? = null, dark: Boolean = false) = buildAnnotatedString {
    val sign = if (dark) K.op else C.traba
    input.forEachIndexed { i, ch ->
        when {
            sel != null && i in sel -> withStyle(SpanStyle(background = if (dark) K.sel else C.sel, color = sign, textDecoration = TextDecoration.Underline)) { append(ch) }
            ch in "÷×+−,%" -> withStyle(SpanStyle(color = sign)) { append(ch) }
            else -> append(ch)
        }
    }
}

/** Opciones A, B y C una debajo de otra y, al final, los productos. En el historial se toca una para dibujarla. */
@Composable
fun ResultCard(r: CalcResult.Ok, p: Pal = LIGHT, picked: Load? = null, onPick: ((Load) -> Unit)? = null) = Column(
    Modifier.fillMaxWidth().background(p.card, R14).border(2.dp, p.line, R14).padding(horizontal = 12.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
) {
    val s = r.load
    val b = s.balance()
    val ts = textScale()
    Row(Modifier.fillMaxWidth()) {
        BasicText("${s.truck.label} · ${fmtT(s.tons)} t · ${s.kg} kg", style = body(14.sp, p.muted, FontWeight.Bold), modifier = Modifier.weight(1f))
        BasicText(if (b.ok) "Parejo ✓" else "Recostado", style = body(14.sp, if (b.ok) p.green else p.red, FontWeight.Bold), softWrap = false)
    }
    if (s.over) Warn("Pasa de ${fmtT(s.truck.tons)} t ${s.truck.deName} (máx. ${s.maxBags} bultos)", p.red)
    val maxTop = s.layout().maxOf { it.height }
    if (maxTop > s.truck.techo) Warn("$maxTop pisos pasan la baranda (${s.truck.techo})", p.red)
    val opts = s.options()
    val cur = picked ?: opts[0].load
    opts.forEach { o ->
        val l = o.load
        val on = onPick != null && l.rows == cur.rows
        Row(
            Modifier.fillMaxWidth().clip(R12).background(if (on) p.pick else Color.Transparent)
                .then(if (onPick != null) Modifier.clickable { onPick(l) } else Modifier).padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            BasicText(o.tag, style = cond(26.sp * ts, p.accent), modifier = Modifier.width(30.dp))
            Column(Modifier.weight(1f)) {
                val g = linkedMapOf<Int, Int>()
                l.rows.forEach { g[it] = (g[it] ?: 0) + 1 }
                g.forEach { (n, c) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText("$c ${plural(c, "fila", "filas")} › $n", style = cond(23.sp * ts, p.ink, FontWeight.Bold), modifier = Modifier.padding(end = 10.dp))
                        BasicText(l.pisosText(n), style = body(13.sp * ts, p.muted, FontWeight.Bold))
                    }
                }
            }
        }
    }
    if (r.products.isNotEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(2.dp).background(p.line))
        val what = cur.productText(r.products)
        r.products.forEachIndexed { i, pr ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Box(Modifier.padding(top = 5.dp).size(12.dp).background(PRODUCT_COLORS[i % PRODUCT_COLORS.size], RoundedCornerShape(3.dp)).border(1.dp, p.muted, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(8.dp))
                // Solo se corta la línea en los "+", nunca dentro de "en fila 5".
                BasicText(buildAnnotatedString {
                    withStyle(SpanStyle(color = p.accent)) { append("P${i + 1} ${pr.n}") }
                    if (pr.rest) withStyle(SpanStyle(color = p.muted)) { append(" resto") }
                    append(" › ")
                    append(what[i].split(" + ").joinToString(" + ") { it.replace(' ', ' ') })
                }, style = cond(18.sp * ts, p.ink, FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun textScale() = (LocalConfiguration.current.screenWidthDp / 380f).coerceIn(0.85f, 1f)

@Composable
private fun Warn(text: String, color: Color) = BasicText("⚠ $text", style = body(14.sp, color, FontWeight.Bold))

@Composable
private fun ErrorCard(r: CalcResult.Error, p: Pal = LIGHT) = Column(
    Modifier.fillMaxWidth().background(p.card, R14).border(2.dp, p.red, R14).padding(horizontal = 12.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp)
) {
    BasicText(r.text, style = cond(20.sp, p.red, FontWeight.Bold))
    BasicText(r.hint, style = body(14.sp, p.muted))
}

private enum class KeyKind { NUM, FN, OP }

@Composable
private fun RowScope.Key(label: String, kind: KeyKind, h: Dp, sub: String? = null, desc: String = label, size: TextUnit? = null, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val bg = when (kind) { KeyKind.NUM -> K.num; KeyKind.FN -> K.fn; KeyKind.OP -> K.op }
    Box(
        Modifier.weight(1f).height(h).clip(RoundedCornerShape(h / 2.6f)).background(if (down) bg.copy(alpha = 0.7f) else bg)
            .semantics { contentDescription = desc; role = Role.Button }
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (label in SYMBOLS) Symbol(label, Color.White, if (h < 56.dp) 22.dp else 28.dp)
            else {
                val sz = (size ?: if (h < 56.dp) 28.sp else 34.sp) * (if (sub != null && h < 56.dp) 0.82f else 1f)
                BasicText(label, style = cond(sz, Color.White, FontWeight.Bold).copy(lineHeight = sz * 1.1f))
            }
            if (sub != null) BasicText(sub, style = body(if (h < 56.dp) 9.sp else 10.sp, Color.White.copy(alpha = 0.75f), FontWeight.Bold)
                .copy(letterSpacing = 0.08.em, lineHeight = if (h < 56.dp) 10.sp else 13.sp))
        }
    }
}

private val SYMBOLS = setOf("÷", "×", "+", "−", ",", "=")

/** Los signos se dibujan para que se vean grandes y centrados con cualquier fuente. */
@Composable
private fun Symbol(k: String, color: Color, size: Dp) = Canvas(Modifier.size(size)) {
    val u = this.size.width
    val w = u * 0.12f
    val c = u / 2
    fun bar(y: Float) = drawLine(color, Offset(u * 0.14f, y), Offset(u * 0.86f, y), w, StrokeCap.Round)
    when (k) {
        "+" -> { bar(c); drawLine(color, Offset(c, u * 0.14f), Offset(c, u * 0.86f), w, StrokeCap.Round) }
        "−" -> bar(c)
        "=" -> { bar(u * 0.36f); bar(u * 0.64f) }
        "×" -> {
            drawLine(color, Offset(u * 0.22f, u * 0.22f), Offset(u * 0.78f, u * 0.78f), w, StrokeCap.Round)
            drawLine(color, Offset(u * 0.78f, u * 0.22f), Offset(u * 0.22f, u * 0.78f), w, StrokeCap.Round)
        }
        "÷" -> { bar(c); drawCircle(color, w * 0.85f, Offset(c, u * 0.2f)); drawCircle(color, w * 0.85f, Offset(c, u * 0.8f)) }
        "," -> {
            drawCircle(color, u * 0.13f, Offset(c, u * 0.62f))
            drawLine(color, Offset(c + u * 0.1f, u * 0.64f), Offset(c - u * 0.08f, u * 0.92f), w * 0.9f, StrokeCap.Round)
        }
    }
}

@Composable
private fun Pad(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    content = content
)

private val GAP = Arrangement.spacedBy(10.dp)

@Composable
private fun Keypad(q: QuickStore, h: Dp, sel: Int?, fresh: Boolean, onSel: (Int?, Boolean) -> Unit) {
    fun t(k: String) {
        val n = if (sel != null && (k == "⌫" || k.all { it.isDigit() })) editNumber(q.input, sel, k, fresh).also { onSel(sel, false) }
        else { onSel(null, false); press(q.input, k) }
        if (n != q.input) q.type(n)
    }
    val truckLabel = when (q.truck) { null -> "AUTO"; Truck.DOBLE -> "DOBLE"; else -> q.truck!!.label.uppercase() }
    Pad {
        Row(horizontalArrangement = GAP) {
            Key("C", KeyKind.FN, h, desc = "Borrar todo") { onSel(null, false); q.clear() }
            Key("⌫", KeyKind.FN, h, desc = "Borrar uno") { t("⌫") }
            Key("${q.kg}", KeyKind.FN, h, sub = "KG", desc = "Peso del bulto: ${q.kg} kilos", size = 26.sp) { q.cycleKg() }
            Key(truckLabel, KeyKind.FN, h, sub = "CAMIÓN", desc = "Camión: ${q.truck?.label ?: "automático"}",
                size = if (truckLabel.length > 6) 17.sp else 22.sp) { q.cycleTruck() }
        }
        Row(horizontalArrangement = GAP) {
            listOf("7", "8", "9").forEach { Key(it, KeyKind.NUM, h) { t(it) } }
            Key("÷", KeyKind.OP, h, sub = "FILAS", desc = "Filas de fondo a compuerta") { t("÷") }
        }
        Row(horizontalArrangement = GAP) {
            listOf("4", "5", "6").forEach { Key(it, KeyKind.NUM, h) { t(it) } }
            Key("×", KeyKind.OP, h, sub = "ANCHO", desc = "Bultos a lo ancho") { t("×") }
        }
        Row(horizontalArrangement = GAP) {
            listOf("1", "2", "3").forEach { Key(it, KeyKind.NUM, h) { t(it) } }
            Key("+", KeyKind.OP, h, desc = "Sumar") { t("+") }
        }
        Row(horizontalArrangement = GAP) {
            Key("0", KeyKind.NUM, h) { t("0") }
            Key("00", KeyKind.NUM, h) { t("00") }
            Key(",", KeyKind.FN, h, sub = "PRODUCTO", desc = "Coma: agrega un producto") { t(",") }
            Key("=", KeyKind.OP, h, desc = "Calcular") { onSel(null, false); q.equals() }
        }
    }
}

private val OP_NAMES = mapOf("×" to "Multiplicar", "−" to "Restar", "+" to "Sumar")

@Composable
private fun MathKeypad(q: QuickStore, h: Dp) {
    fun t(k: String) { val n = pressMath(q.math, k); if (n != q.math) q.typeMath(n) }
    Pad {
        Row(horizontalArrangement = GAP) {
            Key("C", KeyKind.FN, h, desc = "Borrar todo") { q.typeMath("") }
            Key("⌫", KeyKind.FN, h, desc = "Borrar uno") { t("⌫") }
            Key("%", KeyKind.FN, h, desc = "Por ciento") { t("%") }
            Key("÷", KeyKind.OP, h, desc = "Dividir") { t("÷") }
        }
        listOf("789" to "×", "456" to "−", "123" to "+").forEach { (nums, op) ->
            Row(horizontalArrangement = GAP) {
                nums.forEach { d -> Key("$d", KeyKind.NUM, h) { t("$d") } }
                Key(op, KeyKind.OP, h, desc = OP_NAMES.getValue(op)) { t(op) }
            }
        }
        Row(horizontalArrangement = GAP) {
            Key("0", KeyKind.NUM, h) { t("0") }
            Key("00", KeyKind.NUM, h) { t("00") }
            Key(".", KeyKind.NUM, h, desc = "Punto decimal") { t(".") }
            Key("=", KeyKind.OP, h, desc = "Resultado") { q.mathEquals() }
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
    var load by remember(e) { mutableStateOf((r as? CalcResult.Ok)?.load?.options()?.first()?.load) }
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
                ResultCard(r, picked = load) { o -> load = o.copy(view = load?.view ?: o.view, sel = minOf(load?.sel ?: 0, o.l - 1)) }
                load?.let { l -> ViewsCard(l) { f -> load = f(l) } }
                Btn("Compartir por WhatsApp", Modifier.fillMaxWidth(), height = 60.dp, size = 22.sp, bg = C.green,
                    icon = { Icon(WA_ICON, 24.dp, Color.White, 24f, filled = true) }) { shareWhatsApp(ctx, (load ?: r.load).quickShareText(r.products)) }
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
