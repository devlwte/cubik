package com.kyrnforge.cubik

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/* Los dibujos trabajan en unidades fijas (como un viewBox) y se escalan al ancho de la pantalla. */

enum class Anchor { START, MIDDLE, END }

class Pen(val d: DrawScope, private val tm: TextMeasurer) {
    fun rect(x: Float, y: Float, w: Float, h: Float, fill: Color?, rx: Float = 0f,
             stroke: Color? = null, sw: Float = 1f, dash: FloatArray? = null) {
        val tl = Offset(x, y)
        val sz = Size(w, h)
        val cr = CornerRadius(rx, rx)
        if (fill != null) d.drawRoundRect(fill, tl, sz, cr)
        if (stroke != null) d.drawRoundRect(stroke, tl, sz, cr,
            style = Stroke(sw, pathEffect = dash?.let { PathEffect.dashPathEffect(it) }))
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, sw: Float = 1f, dash: FloatArray? = null, alpha: Float = 1f) =
        d.drawLine(color, Offset(x1, y1), Offset(x2, y2), sw, pathEffect = dash?.let { PathEffect.dashPathEffect(it) }, alpha = alpha)

    fun poly(fill: Color?, stroke: Color? = null, sw: Float = 1f, vararg pts: Float) {
        val p = Path().apply {
            moveTo(pts[0], pts[1])
            var i = 2
            while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }
            close()
        }
        if (fill != null) d.drawPath(p, fill)
        if (stroke != null) d.drawPath(p, stroke, style = Stroke(sw))
    }

    fun text(s: String, x: Float, y: Float, size: Float, color: Color, anchor: Anchor = Anchor.START,
             weight: FontWeight = FontWeight.Bold, family: FontFamily = Cond, spacing: Float = 0f) {
        // Tamaño en unidades del dibujo, sin importar el tamaño de letra del sistema.
        val style = TextStyle(color = color, fontSize = (size / (d.density * d.fontScale)).sp,
            fontFamily = family, fontWeight = weight, letterSpacing = spacing.em)
        val r = tm.measure(s, style)
        val dx = when (anchor) { Anchor.START -> 0f; Anchor.MIDDLE -> r.size.width / 2f; Anchor.END -> r.size.width.toFloat() }
        d.drawText(r, topLeft = Offset(x - dx, y - r.firstBaseline))
    }

    fun label(s: String, x: Float, y: Float, anchor: Anchor = Anchor.START) =
        text(s, x, y, 11f, C.muted, anchor, spacing = 0.08f)

    fun bag(x: Float, y: Float, w: Float, h: Float, alt: Boolean, rx: Float = 3f, partial: Boolean = false) =
        rect(x, y, w, h, if (alt) C.kraftDk else C.kraft, rx, C.kraftEdge, 1f, if (partial) floatArrayOf(3f, 2f) else null)

    /** Bulto atravesado de amarre: óxido con rayas diagonales. */
    fun traba(x: Float, y: Float, w: Float, h: Float, rx: Float = 3f) {
        rect(x, y, w, h, C.traba, rx)
        val clip = Path().apply { addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(rx, rx))) }
        d.clipPath(clip) {
            var k = -h
            while (k < w + h) {
                drawLine(C.trabaDk, Offset(x + k, y + h), Offset(x + k + h, y), 1.6f)
                k += 6f
            }
        }
        rect(x, y, w, h, null, rx, C.trabaDk, 1.2f)
    }

    fun wheel(cx: Float, cy: Float, r: Float = 15f) {
        d.drawCircle(C.steel, r, Offset(cx, cy))
        d.drawCircle(C.hub, r * 0.4f, Offset(cx, cy))
    }

    fun rotated(deg: Float, px: Float, py: Float, block: Pen.() -> Unit) = d.rotate(deg, Offset(px, py)) { this@Pen.block() }
}

@Composable
fun Drawing(vw: Float, vh: Float, onTap: ((Float, Float) -> Unit)? = null, draw: Pen.() -> Unit) {
    val tm = rememberTextMeasurer(cacheSize = 64)
    val tap by rememberUpdatedState(onTap)
    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .border(2.dp, C.line, RoundedCornerShape(8.dp))
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(6.dp)
    ) {
        val minW = (vw * 0.55f).dp
        val w = if (maxWidth > minW) maxWidth else minW
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Canvas(
                Modifier.width(w).height(w * (vh / vw)).pointerInput(vw, vh) {
                    detectTapGestures { o -> val s = size.width / vw; tap?.invoke(o.x / s, o.y / s) }
                }
            ) {
                val s = size.width / vw
                withTransform({ scale(s, s, pivot = Offset.Zero) }) { Pen(this, tm).draw() }
            }
        }
    }
}

// Vista lateral

class SideView(private val s: Load) {
    private val t = s.truck
    private val cw = 36f
    private val ph = 15f
    private val pad = 10f
    private val lab = 34f
    private val lay = s.layout()
    private val top = max(1, lay.maxOf { it.height })
    private val extra = if (top > t.techo) (top - t.techo) * ph + 6f else 0f
    private val mula = t == Truck.MULA
    private val cabW = if (mula) 64f else 72f
    private val bedX = pad + cabW + if (mula) 6f else 10f
    private val bedW = s.l * cw
    private val bedTop = lab + extra
    private val wallH = t.techo * ph
    private val floorY = bedTop + wallH
    val vw = bedX + bedW + 50f
    val vh = floorY + 46f

    fun hit(x: Float, y: Float): Int? {
        val i = floor((x - bedX) / cw).toInt()
        return if (i in 0 until s.l && y in (lab - 20f)..floorY) i else null
    }

    fun draw(p: Pen): Unit = with(p) {
        label("◂ CABINA", pad, 13f)
        label("COMPUERTA ▸", bedX + bedW, 13f, Anchor.END)
        rect(bedX + s.sel * cw, lab - 18f, cw, floorY - lab + 18f, C.sel)
        val cabH = min(wallH * 0.8f, 100f)
        val cabTop = floorY - cabH
        poly(C.steel, null, 1f, pad, floorY + 9, pad, cabTop + cabH * 0.38f, pad + cabW * 0.32f, cabTop, pad + cabW, cabTop, pad + cabW, floorY + 9)
        poly(C.win, null, 1f, pad + 5, cabTop + cabH * 0.36f, pad + cabW * 0.32f + 3, cabTop + 6, pad + cabW - 7, cabTop + 6, pad + cabW - 7, cabTop + cabH * 0.36f)
        line(pad + cabW * 0.5f, cabTop + cabH * 0.44f, pad + cabW * 0.5f, floorY - 4, C.cabLine, 1.5f)
        rect(pad, floorY, bedX + bedW - pad + 4, 9f, C.steel)
        rect(bedX, bedTop, bedW, wallH, C.xray)
        lay.forEachIndexed { i, r ->
            val x = bedX + i * cw
            for (k in 0 until r.p) {
                val y = floorY - (k + 1) * ph + 1
                bag(x + 2, y, cw - 4, ph - 2, k % 2 == 1)
                line(x + 7, y + 4, x + cw - 7, y + 4, C.kraftEdge, 0.8f, alpha = 0.55f)
            }
            if (r.t > 0) {
                val y = floorY - (r.p + 1) * ph + 1
                if (r.p > 0 && topLayer(s.w, r.t).crossed) {
                    traba(x + 2, y, cw - 4, ph - 2)
                    text("${r.t}", x + cw / 2, y + ph / 2 + 3, 12f, Color.White, Anchor.MIDDLE, FontWeight.ExtraBold)
                } else if (r.p > 0) {
                    rect(x + 2, y, cw - 4, ph - 2, C.kraft, 3f, C.trabaDk, 2f)
                    text("${r.t}", x + cw / 2, y + ph / 2 + 3, 12f, C.ink, Anchor.MIDDLE, FontWeight.ExtraBold)
                } else {
                    bag(x + 2, y, cw - 4, ph - 2, false, partial = true)
                    text("${r.t}", x + cw / 2, y + ph / 2 + 3, 12f, C.ink, Anchor.MIDDLE)
                }
            }
            val on = i == s.sel
            text("${i + 1}", x + cw / 2, lab - 5, if (on) 15f else 13f, if (on) C.ink else C.muted, Anchor.MIDDLE)
        }
        var i = 2
        while (i < s.l) { line(bedX + i * cw, bedTop, bedX + i * cw, floorY, C.steel, 1f, floatArrayOf(4f, 4f), 0.5f); i += 2 }
        rect(bedX, bedTop, bedW, wallH, null, 0f, C.steel, 2.5f)
        line(bedX + bedW, bedTop, bedX + bedW, floorY + 9, C.steel, 5f)
        if (top > t.techo) {
            line(bedX - 4, bedTop, bedX + bedW + 4, bedTop, C.red, 2.5f, floatArrayOf(6f, 4f))
            text("MUY ALTO", pad, bedTop + 4, 12f, C.red, weight = FontWeight.ExtraBold)
        }
        val cy = floorY + 24
        wheel(pad + cabW * 0.48f, cy)
        when (t.axles) {
            Axles.SINGLE -> wheel(bedX + bedW * 0.7f, cy)
            Axles.TANDEM -> { wheel(bedX + bedW * 0.62f, cy); wheel(bedX + bedW * 0.62f + 33, cy) }
            Axles.MULA -> listOf(bedX + 18, bedX + 51, bedX + bedW - 100, bedX + bedW - 67, bedX + bedW - 34).forEach { wheel(it, cy) }
        }
        val pm = lay.maxOf { it.p }
        if (pm > 0) {
            val x = bedX + bedW + 12
            val y2 = floorY - pm * ph
            line(x - 4, floorY, x + 4, floorY, C.ink, 1.5f)
            line(x, floorY, x, y2, C.ink, 1.5f)
            line(x - 4, y2, x + 4, y2, C.ink, 1.5f)
            text("$pm", x + 8, (floorY + y2) / 2 + 4, 20f, C.ink, weight = FontWeight.ExtraBold)
            text("pisos", x + 8, (floorY + y2) / 2 + 16, 11f, C.muted)
        }
    }
}

// Vista superior

class TopView(private val s: Load) {
    private val cw = 40f
    private val bw = 20f
    private val pad = 10f
    private val lab = 30f
    private val cabW = 54f
    private val lay = s.layout()
    private val bedX = pad + cabW + 8
    private val bedY = lab
    private val bedW = s.l * cw
    private val bedH = s.w * bw
    val vw = bedX + bedW + 40f
    val vh = bedY + bedH + 54f

    fun hit(x: Float, y: Float): Int? {
        val i = floor((x - bedX) / cw).toInt()
        return if (i in 0 until s.l && y <= bedY + bedH + 18) i else null
    }

    fun draw(p: Pen): Unit = with(p) {
        label("◂ CABINA", pad, 13f)
        label("COMPUERTA ▸", bedX + bedW, 13f, Anchor.END)
        rect(pad, bedY + bedH * 0.08f, cabW, bedH * 0.84f, C.steel, 10f)
        rect(pad + 6, bedY + bedH * 0.16f, 11f, bedH * 0.68f, C.win, 3f)
        rect(bedX, bedY, bedW, bedH, C.xray)
        rect(bedX + s.sel * cw, bedY, cw, bedH, C.sel)
        lay.forEachIndexed { i, r ->
            val x = bedX + i * cw
            val cells = if (r.p > 0) s.w else r.t
            for (j in 0 until cells) {
                val y = bedY + j * bw + 2
                bag(x + 2, y, cw - 4, bw - 4, i % 2 == 1, partial = r.p == 0)
                line(x + 7, y + (bw - 4) / 2, x + cw - 7, y + (bw - 4) / 2, C.kraftEdge, 0.8f, alpha = 0.55f)
            }
            if (r.p > 0 && r.t > 0) {
                // Lo de abajo se aclara para que resalte lo que va encima.
                rect(x + 1, bedY + 1, cw - 2, bedH - 2, Color.White.copy(alpha = 0.6f))
                val top = topLayer(s.w, r.t)
                top.slots.forEach { (start, size) ->
                    val ty = bedY + start * bw + 2
                    val th = size * bw - 4
                    if (top.crossed) {
                        // Atravesado: bulto girado a lo ancho, centrado en el fondo de la fila.
                        val tw = cw * 0.6f
                        val tx = x + (cw - tw) / 2
                        rect(tx, ty, tw, th, C.traba, 3f, C.trabaDk, 1.2f)
                        line(tx + tw / 2, ty + 5, tx + tw / 2, ty + th - 5, Color.White, 1f, alpha = 0.45f)
                    } else {
                        rect(x + 2, ty, cw - 4, th, C.kraft, 3f, C.trabaDk, 2f)
                        line(x + 7, ty + th / 2, x + cw - 7, ty + th / 2, C.kraftEdge, 0.8f, alpha = 0.55f)
                    }
                }
            }
            val on = i == s.sel
            text("${i + 1}", x + cw / 2, lab - 6, if (on) 15f else 13f, if (on) C.ink else C.muted, Anchor.MIDDLE)
            text("${r.n}", x + cw / 2, bedY + bedH + 15, 12f, C.ink, Anchor.MIDDLE)
        }
        // Divisiones entre filas para que cada fila se lea aparte.
        for (i in 1 until s.l) line(bedX + i * cw, bedY, bedX + i * cw, bedY + bedH, C.steel, 1.5f)
        rect(bedX, bedY, bedW, bedH, null, 0f, C.steel, 2.5f)
        line(bedX + bedW, bedY, bedX + bedW, bedY + bedH, C.steel, 5f)
        val ay = bedY + bedH + 27
        line(bedX, ay - 4, bedX, ay + 4, C.ink, 1.5f)
        line(bedX, ay, bedX + bedW, ay, C.ink, 1.5f)
        line(bedX + bedW, ay - 4, bedX + bedW, ay + 4, C.ink, 1.5f)
        text("${s.l} filas de largo · número = bultos por fila", bedX + bedW / 2, ay + 18, 13f, C.ink, Anchor.MIDDLE)
        val ax = bedX + bedW + 12
        line(ax - 4, bedY, ax + 4, bedY, C.ink, 1.5f)
        line(ax, bedY, ax, bedY + bedH, C.ink, 1.5f)
        line(ax - 4, bedY + bedH, ax + 4, bedY + bedH, C.ink, 1.5f)
        rotated(90f, ax + 12, bedY + bedH / 2) { text("${s.w} de ancho", ax + 12, bedY + bedH / 2, 13f, C.ink, Anchor.MIDDLE) }
    }
}

// Vista posterior

class BackView(private val s: Load) {
    private val t = s.truck
    private val be = 44f
    private val ph = 17f
    private val pad = 8f
    private val doorW = 40f
    private val wall = 8f
    private val lab = 24f
    private val r = s.row(s.rows[s.sel])
    private val top = r.height
    private val extra = if (top > t.techo) (top - t.techo) * ph + 6f else 0f
    private val iw = s.w * be
    private val x0 = pad + doorW + 4
    private val inX = x0 + wall
    private val xr = inX + iw
    val vw = xr + wall + doorW + 4 + pad
    private val wallTop = lab + extra + 6
    private val wallH = t.techo * ph
    private val floorY = wallTop + wallH
    val vh = floorY + 48f

    fun draw(p: Pen): Unit = with(p) {
        label("PARED IZQ.", pad, 13f)
        label("PARED DER.", vw - pad, 13f, Anchor.END)
        poly(C.door, C.steel, 2f, x0, wallTop, x0 - doorW, wallTop + 14, x0 - doorW, floorY - 2, x0, floorY)
        poly(C.door, C.steel, 2f, xr + wall, wallTop, xr + wall + doorW, wallTop + 14, xr + wall + doorW, floorY - 2, xr + wall, floorY)
        line(x0 - doorW / 2, wallTop + 10, x0 - doorW / 2, floorY - 4, C.cabLine, 1.5f)
        line(xr + wall + doorW / 2, wallTop + 10, xr + wall + doorW / 2, floorY - 4, C.cabLine, 1.5f)
        rect(inX, wallTop, iw, wallH, C.xray)
        for (k in 0 until r.p) for (j in 0 until s.w) {
            bag(inX + j * be + 2, floorY - (k + 1) * ph + 1, be - 4, ph - 2, (k + j) % 2 == 1, rx = 7f)
        }
        if (r.t > 0) {
            val y = floorY - (r.p + 1) * ph + 1
            if (r.p > 0) {
                val top = topLayer(s.w, r.t)
                top.slots.forEach { (start, size) ->
                    val tx = inX + start * be + 2
                    val tw = size * be - 4
                    if (top.crossed) traba(tx, y, tw, ph - 2, 4f)
                    else rect(tx, y, tw, ph - 2, C.kraft, 7f, C.trabaDk, 2f)
                }
                text(topShort(s.w, r.t).uppercase(), inX + iw / 2, y - 5, 13f, C.traba, Anchor.MIDDLE, FontWeight.ExtraBold)
            } else {
                val start = inX + (iw - r.t * be) / 2
                for (j in 0 until r.t) bag(start + j * be + 2, y, be - 4, ph - 2, false, rx = 7f, partial = true)
            }
        }
        rect(x0, wallTop, wall, wallH, C.wallB, 0f, C.steel, 2f)
        rect(xr, wallTop, wall, wallH, C.wallB, 0f, C.steel, 2f)
        line(inX, wallTop, xr, wallTop, C.steel, 2.5f, floatArrayOf(5f, 5f))
        if (top > t.techo) {
            line(x0, wallTop, xr + wall, wallTop, C.red, 2.5f, floatArrayOf(6f, 4f))
            text("MUY ALTO", inX + iw / 2, lab - 4, 12f, C.red, Anchor.MIDDLE, FontWeight.ExtraBold)
        }
        rect(x0, floorY, iw + 2 * wall, 8f, C.steel)
        rect(x0 + 10, floorY + 8, iw + 2 * wall - 20, 6f, C.steel.copy(alpha = 0.7f))
        listOf(x0 + 2, x0 + 21, xr + wall - 18, xr + wall - 37).forEach { rect(it, floorY + 14, 16f, 28f, C.steel, 4f) }
    }
}

// Reparto de ejes

class AxleView(private val s: Load, private val b: Balance) {
    private val pad = 12f
    val vw = 360f
    private val base = 112f
    val vh = base + 48f

    fun draw(p: Pen): Unit = with(p) {
        val wd = vw - 2 * pad
        val bw = wd / s.l
        val hmax = 76f
        val maxN = max(1, s.rows.maxOrNull() ?: 1)
        rect(pad + 0.44f * wd, 10f, 0.12f * wd, base - 10, C.green.copy(alpha = 0.22f))
        s.rows.forEachIndexed { i, n ->
            val h = n.toFloat() / maxN * hmax
            val x = pad + i * bw
            rect(x + 2, base - h, bw - 4, h, C.kraft, 2f, C.kraftEdge)
            if (bw >= 22) text("$n", x + bw / 2, base - h - 4, 12f, C.ink, Anchor.MIDDLE)
        }
        line(pad, base, vw - pad, base, C.steel, 3f)
        val cx = pad + b.x.toFloat() * wd
        poly(if (b.ok) C.green else C.red, null, 1f, cx, base + 4, cx - 9, base + 20, cx + 9, base + 20)
        label("◂ CABINA", pad, base + 40)
        label("COMPUERTA ▸", vw - pad, base + 40, Anchor.END)
        text("centro", cx, base + 34, 13f, C.ink, Anchor.MIDDLE)
    }
}
