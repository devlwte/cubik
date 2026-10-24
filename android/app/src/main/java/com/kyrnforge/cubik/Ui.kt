package com.kyrnforge.cubik

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Componentes base

private val R8 = RoundedCornerShape(8.dp)

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().background(C.card, RoundedCornerShape(10.dp))
        .border(2.dp, C.line, RoundedCornerShape(10.dp)).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    content = content
)

@Composable
private fun StepHead(n: String, title: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(34.dp).background(C.ink, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
        BasicText(n, style = cond(21.sp, Color.White))
    }
    Spacer(Modifier.width(10.dp))
    BasicText(title.uppercase(), style = cond(23.sp, spacing = 0.03.em))
}

@Composable
private fun Sub(text: String) = BasicText(text.uppercase(), style = cond(17.sp, C.muted, FontWeight.Bold, 0.05.em))

@Composable
private fun Hint(text: String) = BasicText(text, style = body(15.sp, C.muted, FontWeight.Medium))

@Composable
fun Btn(
    text: String,
    modifier: Modifier = Modifier,
    pressed: Boolean = false,
    height: Dp = 56.dp,
    size: TextUnit = 20.sp,
    sub: String? = null,
    bg: Color? = null,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val fill = bg ?: if (pressed) C.ink else Color.White
    val fg = if (bg != null || pressed) Color.White else C.ink
    Box(
        modifier.heightIn(min = height).clip(R8).background(fill)
            .border(2.dp, bg ?: C.ink, R8)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            icon?.invoke()
            BasicText(text, style = cond(size, fg).copy(textAlign = TextAlign.Center))
            if (sub != null) BasicText(sub, style = body(13.sp, fg.copy(alpha = 0.8f)))
        }
    }
}

/** Mantener presionado repite; después de un rato salta de a 10. */
@Composable
fun RepeatBtn(text: String, desc: String, modifier: Modifier = Modifier, height: Dp = 72.dp, size: TextUnit = 38.sp, onStep: (Int) -> Unit) {
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    var down by remember { mutableStateOf(false) }
    Box(
        modifier.height(height).clip(R8).background(if (down) C.sel else Color.White).border(2.dp, C.ink, R8)
            .semantics { role = Role.Button; contentDescription = desc }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    down = true
                    step(1)
                    val job = scope.launch {
                        delay(450)
                        var c = 0
                        while (isActive) { c++; step(if (c > 20) 10 else 1); delay(80) }
                    }
                    tryAwaitRelease()
                    job.cancel()
                    down = false
                })
            },
        contentAlignment = Alignment.Center
    ) { BasicText(text, style = cond(size)) }
}

@Composable
private fun Icon(d: String, size: Dp = 28.dp, color: Color = C.ink, view: Float = 28f, filled: Boolean = false) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(Modifier.size(size)) {
        val s = this.size.width / view
        withTransform({ scale(s, s, Offset.Zero) }) {
            if (filled) drawPath(path, color)
            else drawPath(path, color, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun Grid(cols: Int, content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)

private fun bold(s: String, size: TextUnit = 23.sp): AnnotatedString = buildAnnotatedString {
    s.split("**").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontFamily = Cond, fontWeight = FontWeight.ExtraBold, fontSize = size)) { append(part) }
        else append(part)
    }
}

// Pantalla principal

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CubikScreen(store: Store) {
    val s = store.load
    val scope = rememberCoroutineScope()
    val viewReq = remember { BringIntoViewRequester() }
    fun up(f: (Load) -> Load) = store.update(f)

    Box(Modifier.fillMaxSize().background(C.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Header()
                BagsCard(s, ::up)
                TruckCard(s, ::up)
                ModeCard(s, ::up) { scope.launch { delay(120); viewReq.bringIntoView() } }
                WeightAlert(s)
                val maxTop = s.layout().maxOf { it.height }
                if (maxTop > s.truck.techo) Box(
                    Modifier.fillMaxWidth().background(C.redBg, R8).border(2.dp, C.red, R8).padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicText(
                        "Ojo: $maxTop pisos sobrepasan la baranda ${s.truck.deName} (${s.truck.techo} pisos). Agregue filas o use un camión más grande.",
                        style = body(16.sp, C.red, FontWeight.Bold)
                    )
                }
                Box(Modifier.bringIntoViewRequester(viewReq)) { ViewsCard(s, ::up) }
                RouteCard(s)
            }
        }
    }
}

@Composable
private fun Header() = Row(Modifier.padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Canvas(Modifier.size(44.dp)) {
        val k = size.width / 44f
        fun r(x: Float, y: Float, w: Float, h: Float, c: Color, rx: Float = 2f) =
            drawRoundRect(c, Offset(x * k, y * k), Size(w * k, h * k), CornerRadius(rx * k))
        r(1f, 1f, 42f, 42f, C.ink, 8f)
        r(7f, 25f, 14f, 8f, C.kraft); r(23f, 25f, 14f, 8f, C.kraftDk)
        r(7f, 16f, 14f, 8f, C.kraftDk); r(23f, 16f, 14f, 8f, C.kraft)
        r(14f, 8f, 16f, 7f, C.traba)
    }
    Spacer(Modifier.width(12.dp))
    Column {
        BasicText("CUBIK", style = cond(30.sp, spacing = 0.02.em))
        BasicText("Cubicaje y estiba de bultos · KyrnForge", style = body(14.sp, C.muted))
    }
}

@Composable
private fun BagsCard(s: Load, up: ((Load) -> Load) -> Unit) = Card {
    StepHead("1", "Bultos a cargar")
    fun setN(n: Int) = up { it.copy(n = n.coerceIn(1, 3000)).recalc() }
    var tf by remember { mutableStateOf(TextFieldValue(s.n.toString())) }
    LaunchedEffect(s.n) { if (tf.text.toIntOrNull() != s.n) tf = TextFieldValue(s.n.toString(), TextRange(s.n.toString().length)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RepeatBtn("−", "Quitar un bulto", Modifier.width(68.dp)) { k -> up { it.copy(n = (it.n - k).coerceIn(1, 3000)).recalc() } }
        BasicTextField(
            value = tf,
            onValueChange = { v ->
                val digits = v.text.filter(Char::isDigit).take(4)
                tf = TextFieldValue(digits, TextRange(digits.length))
                digits.toIntOrNull()?.takeIf { it > 0 }?.let { setN(min(3000, it)) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = cond(46.sp).copy(textAlign = TextAlign.Center),
            cursorBrush = SolidColor(C.ink),
            modifier = Modifier.weight(1f).height(72.dp)
                .onFocusChanged { if (!it.isFocused && tf.text.toIntOrNull() == null) tf = TextFieldValue(s.n.toString()) }
                .semantics { contentDescription = "Número de bultos" },
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize().background(C.bg, R8).border(2.dp, C.line, R8), contentAlignment = Alignment.Center) { inner() }
            }
        )
        RepeatBtn("+", "Agregar un bulto", Modifier.width(68.dp)) { k -> up { it.copy(n = (it.n + k).coerceIn(1, 3000)).recalc() } }
    }
    BasicText(
        buildAnnotatedString {
            append("${s.n} × ${s.kg} kg = ")
            withStyle(SpanStyle(fontSize = 30.sp)) { append(fmtT(s.targetTons)) }
            append(" toneladas")
        },
        style = cond(22.sp, weight = FontWeight.Bold).copy(textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth()
    )
    Grid(5) { PRESETS.forEach { n -> Btn("$n", Modifier.weight(1f), s.n == n, size = 22.sp) { setN(n) } } }
    Sub("Peso de cada bulto")
    Grid(3) { BAG_KG.forEach { k -> Btn("$k kg", Modifier.weight(1f), s.kg == k, size = 22.sp) { up { it.copy(kg = k).recalc() } } } }
    Sub("Qué se carga (opcional)")
    var prod by remember { mutableStateOf(TextFieldValue(s.prod)) }
    BasicTextField(
        value = prod,
        onValueChange = { v ->
            val t = v.text.take(40)
            prod = v.copy(text = t)
            up { it.copy(prod = t.trim().ifEmpty { DEFAULT_PROD }) }
        },
        singleLine = true,
        textStyle = body(19.sp),
        cursorBrush = SolidColor(C.ink),
        modifier = Modifier.fillMaxWidth().height(56.dp)
            .onFocusChanged { if (!it.isFocused && prod.text.isBlank()) prod = TextFieldValue(s.prod) },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().background(C.bg, R8).border(2.dp, C.line, R8).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                if (prod.text.isEmpty()) BasicText(DEFAULT_PROD, style = body(19.sp, C.muted))
                inner()
            }
        }
    )
}

@Composable
private fun TruckCard(s: Load, up: ((Load) -> Load) -> Unit) = Card {
    StepHead("2", "Espacio del camión")
    Btn(if (s.autoTruck) "✓ Camión escogido según el peso" else "Escoger camión solo según el peso", Modifier.fillMaxWidth(), s.autoTruck) {
        up { it.copy(autoTruck = true).recalc() }
    }
    Hint(
        if (s.autoTruck) "${s.n} bultos de ${s.kg} kg = ${fmtT(s.targetTons)} T: va en ${s.truck.label.lowercase()}. Toque otro camión para cambiarlo a mano."
        else "Camión escogido a mano: ${s.truck.label.lowercase()}."
    )
    Truck.entries.chunked(2).forEach { pair ->
        Grid(2) {
            pair.forEach { t ->
                Btn(t.label, Modifier.weight(1f), s.truck == t, height = 64.dp, sub = "Hasta ${fmtT(t.tons)} T") {
                    up { it.copy(autoTruck = false, truck = t, l = t.rows).distributed() }
                }
            }
        }
    }
    Sub("Largo: filas de fondo a compuerta")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RepeatBtn("−", "Una fila menos", Modifier.width(68.dp)) { up { it.copy(l = (it.l - 1).coerceIn(3, 16)).distributed() } }
        Column(
            Modifier.weight(1f).height(72.dp).background(C.bg, R8).border(2.dp, C.line, R8),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            BasicText("${s.l}", style = cond(46.sp).copy(lineHeight = 46.sp))
            BasicText("FILAS", style = cond(15.sp, C.muted, FontWeight.Bold, 0.06.em))
        }
        RepeatBtn("+", "Una fila más", Modifier.width(68.dp)) { up { it.copy(l = (it.l + 1).coerceIn(3, 16)).distributed() } }
    }
    Grid(5) { LENGTH_CHIPS.forEach { l -> Btn("$l", Modifier.weight(1f), s.l == l, size = 22.sp) { up { it.copy(l = l).distributed() } } } }
    Sub("Ancho: bultos lado a lado")
    Grid(3) { WIDTH_CHIPS.forEach { w -> Btn("$w", Modifier.weight(1f), s.w == w, size = 22.sp) { up { it.copy(w = w, wManual = true).distributed() } } } }
}

@Composable
private fun ModeCard(s: Load, up: ((Load) -> Load) -> Unit, onManual: () -> Unit) = Card {
    StepHead("3", "Modo de ajuste")
    Grid(2) {
        Btn("Repartir parejo", Modifier.weight(1f), s.mode == Mode.AUTO, height = 64.dp, size = 21.sp) { up { it.copy(mode = Mode.AUTO).distributed() } }
        Btn("▲▼ Ajustar a mano", Modifier.weight(1f), s.mode == Mode.MANUAL, height = 64.dp, size = 21.sp) { up { it.copy(mode = Mode.MANUAL) }; onManual() }
    }
    Hint(
        if (s.mode == Mode.AUTO) "El sistema divide el total entre las filas: todas llevan lo mismo y el bulto que sobra va de a uno desde la cabina. Lo que no completa piso va encima de remate; si así el remate queda igual en todas, una fila se deja en pisos completos."
        else "Use ▲ y ▼ debajo del dibujo para subir o bajar bultos en una fila. \"Repartir parejo\" vuelve a empezar."
    )
    if (s.mode == Mode.MANUAL) {
        val d = s.total - s.n
        val c = if (d == 0) C.green else C.red
        Box(Modifier.fillMaxWidth().background(if (d == 0) C.greenBg else C.redBg, R8).border(2.dp, c, R8).padding(horizontal = 12.dp, vertical = 10.dp)) {
            BasicText(
                if (d == 0) "✓ Cuadra: ${s.total} de ${s.n} bultos"
                else "Dibujados ${s.total} de ${s.n}: ${if (d < 0) "faltan ${-d}" else "sobran $d"} ${plural(abs(d), "bulto", "bultos")}",
                style = body(17.sp, c, FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun WeightAlert(s: Load) {
    val t = s.truck
    Column(
        Modifier.fillMaxWidth().background(if (s.over) C.red else C.green, RoundedCornerShape(10.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BasicText(
            if (s.over) "⚠️ PELIGRO: Carga de ${fmtT(s.tons)} Toneladas. Este camión supera el peso permitido en báscula"
            else "✓ Peso aprobado: ${fmtT(s.tons)} de ${fmtT(t.tons)} toneladas",
            style = cond(25.sp, Color.White)
        )
        val frac = if (s.over) 1f else min(1f, (s.tons / t.tons).toFloat())
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.3f))) {
            Box(Modifier.fillMaxWidth(frac).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color.White))
        }
        BasicText(
            if (s.over) "${t.label}: máximo ${fmtT(t.tons)} T (${s.maxBags} bultos). Sobran ${fmtT(s.tons - t.tons)} T."
            else "${t.label}: caben hasta ${s.maxBags} bultos de ${s.kg} kg.",
            style = body(15.sp, Color.White.copy(alpha = 0.95f))
        )
    }
}

private val TABS = listOf(
    Triple(ViewTab.SIDE, "Lateral", "M2 20v-7l3-4h5v11M10 6h16v14H10M4.5 21.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0M18.5 21.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0"),
    Triple(ViewTab.TOP, "Arriba", "M9 6h17v16H9zM15 6v16M20.5 6v16M9 14h17M2 9h5v10H2z"),
    Triple(ViewTab.BACK, "Atrás", "M8 4h12v15H8zM8 4L3 6v13h5M20 4l5 2v13h-5M11 19v4M17 19v4M10 15h8M10 11h8"),
    Triple(ViewTab.AXLE, "Ejes", "M14 4v17M5 22h18M5 8h18M5 8l-3 7h6zM23 8l-3 7h6z")
)

@Composable
private fun Legend() = Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
    @Composable
    fun item(fill: Color, edge: Color, text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp, 14.dp).background(fill, RoundedCornerShape(3.dp)).border(1.5.dp, edge, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(6.dp))
        BasicText(text, style = body(15.sp))
    }
    item(C.kraft, C.kraftEdge, "Bulto acostado")
    item(C.traba, C.trabaDk, "Atravesado de amarre")
}

@Composable
private fun ViewsCard(s: Load, up: ((Load) -> Load) -> Unit) = Card {
    Grid(4) {
        TABS.forEach { (v, label, d) ->
            val on = s.view == v
            Btn(label, Modifier.weight(1f), on, height = 68.dp, size = 16.sp,
                icon = { Icon(d, color = if (on) Color.White else C.ink) }) { up { it.copy(view = v) } }
        }
    }
    val pick: (Int?) -> Unit = { i -> if (i != null) up { it.copy(sel = i) } }
    when (s.view) {
        ViewTab.SIDE -> {
            BasicText("Vista lateral · toca una fila para verla desde atrás", style = body(17.sp, weight = FontWeight.Bold))
            val v = SideView(s)
            Drawing(v.vw, v.vh, { x, y -> pick(v.hit(x, y)) }) { v.draw(this) }
            Legend()
        }
        ViewTab.TOP -> {
            BasicText(bold("**${s.l}** filas de largo × **${s.w}** bultos de ancho", 22.sp), style = body(17.sp, weight = FontWeight.Bold))
            val v = TopView(s)
            Drawing(v.vw, v.vh, { x, y -> pick(v.hit(x, y)) }) { v.draw(this) }
            Legend()
        }
        ViewTab.BACK -> {
            Sub("Escoja la fila")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until s.l) Btn("${i + 1}", Modifier.width(56.dp), i == s.sel, height = 52.dp) { pick(i) }
            }
            val r = s.row(s.rows[s.sel])
            val desc = when {
                r.p > 0 -> "${r.p} ${plural(r.p, "piso", "pisos")} de ${s.w}" + if (r.t > 0) " + " + topShort(s.w, r.t) else ""
                r.t > 0 -> "${r.t} en el piso"
                else -> "vacía"
            }
            BasicText(bold("Fila **${s.sel + 1}** de ${s.l}: $desc · **${r.n}** bultos", 22.sp), style = body(17.sp, weight = FontWeight.Bold))
            val v = BackView(s)
            Drawing(v.vw, v.vh) { v.draw(this) }
            Legend()
        }
        ViewTab.AXLE -> {
            val b = s.balance()
            Row(Modifier.fillMaxWidth().height(64.dp).clip(R8).border(2.dp, C.ink, R8)) {
                Column(
                    Modifier.weight(max(b.front, 22).toFloat()).fillMaxSize().background(C.steel).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    BasicText("${b.front}%", style = cond(30.sp, Color.White))
                    BasicText("ADELANTE · CABINA", style = body(12.sp, Color.White, FontWeight.Bold), softWrap = false)
                }
                Column(
                    Modifier.weight(max(100 - max(b.front, 22), 1).toFloat()).fillMaxSize().background(C.kraft).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.End
                ) {
                    BasicText("${b.back}%", style = cond(30.sp))
                    BasicText("ATRÁS · COMPUERTA", style = body(12.sp, weight = FontWeight.Bold), softWrap = false)
                }
            }
            BasicText(
                if (b.ok) "✓ Camión parejo, no está recostado" else "✕ Recostado hacia " + if (b.front > 50) "la cabina" else "la compuerta",
                style = cond(23.sp, if (b.ok) C.green else C.red)
            )
            val v = AxleView(s, b)
            Drawing(v.vw, v.vh) { v.draw(this) }
            Hint("Barras: bultos en cada fila. El triángulo es el centro de la carga; debe caer en la franja verde.")
        }
    }
    if (s.mode == Mode.MANUAL) {
        Sub("Subir o bajar bultos por fila")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until s.l) {
                val on = i == s.sel
                Column(Modifier.width(64.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RepeatBtn("▲", "Subir un bulto en fila ${i + 1}", Modifier.fillMaxWidth(), 52.dp, 22.sp) { k ->
                        up { it.copy(rows = it.rows.toMutableList().also { r -> r[i] += k }, sel = i) }
                    }
                    Column(
                        Modifier.fillMaxWidth().clip(R8).background(if (on) C.sel else Color.White)
                            .border(2.dp, if (on) C.ink else C.line, R8).clickable { pick(i) }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BasicText("${s.rows[i]}", style = cond(26.sp))
                        BasicText("FILA ${i + 1}", style = body(12.sp, C.muted, FontWeight.Bold))
                    }
                    RepeatBtn("▼", "Bajar un bulto en fila ${i + 1}", Modifier.fillMaxWidth(), 52.dp, 22.sp) { k ->
                        up { it.copy(rows = it.rows.toMutableList().also { r -> r[i] = max(0, r[i] - k) }, sel = i) }
                    }
                }
            }
        }
    }
}

private const val WA_ICON = "M12 2a10 10 0 0 0-8.6 15.1L2 22l5-1.3A10 10 0 1 0 12 2zm0 18.2c-1.5 0-3-.4-4.3-1.2l-.3-.2-3 .8.8-2.9-.2-.3A8.2 8.2 0 1 1 12 20.2zm4.5-6.1c-.2-.1-1.5-.7-1.7-.8s-.4-.1-.6.1-.6.8-.8 1-.3.2-.5.1a6.7 6.7 0 0 1-3.3-2.9c-.3-.4.2-.4.7-1.4.1-.2 0-.3 0-.4l-.8-1.8c-.2-.5-.4-.4-.6-.4h-.5a1 1 0 0 0-.7.3 3 3 0 0 0-.9 2.2 5.2 5.2 0 0 0 1.1 2.7 11.8 11.8 0 0 0 4.5 4c1.7.7 2.3.8 3.2.6a2.7 2.7 0 0 0 1.8-1.2 2.2 2.2 0 0 0 .1-1.3c0-.1-.2-.2-.5-.3z"

@Composable
private fun RouteCard(s: Load) = Card {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    var copied by remember { mutableStateOf("") }
    StepHead("✓", "Hoja de cargue")
    Column(Modifier.fillMaxWidth().background(C.ink, R8).padding(horizontal = 14.dp, vertical = 12.dp)) {
        BasicText("RESUMEN", style = body(13.sp, C.kraft, FontWeight.Bold).copy(letterSpacing = 0.08.em))
        BasicText(s.summary(), style = cond(27.sp, Color.White))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        s.routeSteps().forEachIndexed { i, step ->
            val (fill, edge, fg) = when (step.kind) {
                StepKind.TRABA -> Triple(C.traba, C.trabaDk, Color.White)
                StepKind.OK -> Triple(C.green, C.green, Color.White)
                StepKind.BAD -> Triple(C.red, C.red, Color.White)
                StepKind.PLAIN -> Triple(C.kraft, C.kraftEdge, C.ink)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).background(fill, CircleShape).border(2.dp, edge, CircleShape), contentAlignment = Alignment.Center) {
                    BasicText("${i + 1}", style = cond(20.sp, fg))
                }
                BasicText(bold(step.text), style = body(19.sp), modifier = Modifier.weight(1f).padding(top = 4.dp))
            }
        }
    }
    Btn("Compartir carga por WhatsApp", Modifier.fillMaxWidth(), height = 64.dp, size = 23.sp, bg = C.green,
        icon = { Icon(WA_ICON, 26.dp, Color.White, 24f, filled = true) }) { shareWhatsApp(ctx, s.shareText()) }
    Btn("Copiar texto", Modifier.fillMaxWidth()) {
        clip.setText(AnnotatedString(s.shareText()))
        copied = "Texto copiado. Péguelo en el chat."
    }
    if (copied.isNotEmpty()) BasicText(copied, style = body(17.sp, C.green, FontWeight.Bold).copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
}

/** Abre WhatsApp (normal o Business) con el texto listo; si no está instalado, el menú de compartir de Android. */
private fun shareWhatsApp(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    val wa = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { ctx.packageManager.getLaunchIntentForPackage(it) != null }
    try {
        if (wa != null) ctx.startActivity(Intent(send).setPackage(wa))
        else ctx.startActivity(Intent.createChooser(send, "Compartir carga"))
    } catch (e: ActivityNotFoundException) {
        ctx.startActivity(Intent.createChooser(send, "Compartir carga"))
    }
}
