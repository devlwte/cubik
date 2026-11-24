package com.kyrnforge.cubik

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

object C {
    val bg = Color(0xFFF4F5F7)
    val card = Color(0xFFFFFFFF)
    val ink = Color(0xFF1E2229)
    val muted = Color(0xFF4F5864)
    val line = Color(0xFFC3C9D1)
    val kraft = Color(0xFFD9A05B)
    val kraftDk = Color(0xFFC28B46)
    val kraftEdge = Color(0xFF7A4E1C)
    val traba = Color(0xFFB85D19)
    val trabaDk = Color(0xFF8A420E)
    val steel = Color(0xFF2C3338)
    val xray = Color(0xFFEEF1F5)
    val sel = Color(0xFFFFE6B8)
    val red = Color(0xFFD92D20)
    val redBg = Color(0xFFFEF3F2)
    val green = Color(0xFF079455)
    val greenBg = Color(0xFFECFDF3)
    val win = Color(0xFFDCE2E8)
    val hub = Color(0xFF9AA3AD)
    val wallB = Color(0xFFD5DAE0)
    val door = Color(0xFFE3E7EC)
    val cabLine = Color(0xFF59636B)
}

/** Calculadora: oscura con operadores naranjas. */
object K {
    val bg = Color(0xFF111214)
    val card = Color(0xFF1C1D20)
    val num = Color(0xFF2D2F33)
    val fn = Color(0xFF505359)
    val op = Color(0xFFFF9500)
    val ink = Color(0xFFF5F5F7)
    val muted = Color(0xFF9A9DA3)
    val line = Color(0xFF34363B)
    val red = Color(0xFFFF6B5E)
    val green = Color(0xFF4CD97B)
    val sel = Color(0xFF4A3A1C)
}

/** Colores del resultado: claros en el historial, oscuros en la calculadora. */
data class Pal(val card: Color, val line: Color, val ink: Color, val muted: Color, val accent: Color, val red: Color, val green: Color, val pick: Color)

val LIGHT = Pal(C.card, C.line, C.ink, C.muted, C.traba, C.red, C.green, C.sel)
val DARK = Pal(K.card, K.line, K.ink, K.muted, K.op, K.red, K.green, K.sel)

val Body = FontFamily(
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold)
)

val Cond = FontFamily(
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold)
)

fun cond(size: TextUnit, color: Color = C.ink, weight: FontWeight = FontWeight.ExtraBold, spacing: TextUnit = 0.em) =
    TextStyle(fontFamily = Cond, fontWeight = weight, fontSize = size, color = color, letterSpacing = spacing,
        fontFeatureSettings = "tnum", lineHeight = size * 1.12f)

fun body(size: TextUnit = 17.sp, color: Color = C.ink, weight: FontWeight = FontWeight.SemiBold) =
    TextStyle(fontFamily = Body, fontWeight = weight, fontSize = size, color = color, lineHeight = size * 1.3f)
