package com.example.rankedlife

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable

class Pal(
    val light: Color,
    val dark: Color,
    val edge: Color,
    val gem: Color,
    val accent: Color
)

val palettes = listOf(
    Pal(Color(0xFF8A8F94), Color(0xFF3E4246), Color(0xFF23262A), Color(0xFF6B7075), Color(0xFF9AA0A6)), // Iron
    Pal(Color(0xFFD08A5B), Color(0xFF6B3A1E), Color(0xFF3A1F0F), Color(0xFFE8A878), Color(0xFFF0B98A)), // Bronze
    Pal(Color(0xFFD5DCE3), Color(0xFF6C7782), Color(0xFF3B434B), Color(0xFFBFD8F0), Color(0xFFEAF2FA)), // Silver
    Pal(Color(0xFFF2CC5C), Color(0xFF9A6B14), Color(0xFF5A3C08), Color(0xFF3FBF6F), Color(0xFFFFE08A)), // Gold
    Pal(Color(0xFF7FB3B0), Color(0xFF2B5558), Color(0xFF15302F), Color(0xFF47E0A0), Color(0xFFA8E6DA)), // Platinum
    Pal(Color(0xFF3FB8A0), Color(0xFF14504C), Color(0xFF08292A), Color(0xFF3CFF8F), Color(0xFF9CF5D8)), // Emerald
    Pal(Color(0xFF8E86F0), Color(0xFF302A78), Color(0xFF16134A), Color(0xFFB9C8FF), Color(0xFFD7DCFF)), // Diamond
    Pal(Color(0xFFC06AD8), Color(0xFF4E1B66), Color(0xFF260B38), Color(0xFFFF5CF0), Color(0xFFF0B2FF)), // Master
    Pal(Color(0xFFB5A6A6), Color(0xFF4A2024), Color(0xFF220C0E), Color(0xFFFF3B3B), Color(0xFFFF8A8A)), // Grandmaster
    Pal(Color(0xFFF5D98A), Color(0xFF8C5E1A), Color(0xFF4A3008), Color(0xFF3DB8FF), Color(0xFFFFEDB0)), // Challenger
    Pal(Color(0xFFFFF4D6), Color(0xFFB8A070), Color(0xFF6A5A3A), Color(0xFF4FC3FF), Color(0xFFFFFFFF))  // Sovereign
)

fun tierColor(t: Int): Color = palettes[t.coerceIn(0, palettes.size - 1)].accent

/** Escudo original dibujado con vectores. Más adornos conforme subes de rango. */
@Composable
fun Emblem(tier: Int, modifier: Modifier = Modifier) {
    val t = tier.coerceIn(0, palettes.size - 1)
    val pal = palettes[t]

    Canvas(modifier = modifier) {
        val s = size.minDimension / 100f

        fun poly(vararg p: Float): Path {
            val path = Path()
            path.moveTo(p[0] * s, p[1] * s)
            var i = 2
            while (i < p.size) {
                path.lineTo(p[i] * s, p[i + 1] * s)
                i += 2
            }
            path.close()
            return path
        }

        fun shield(k: Float): Path {
            fun px(x: Float): Float = (50f + (x - 50f) * k) * s
            fun py(y: Float): Float = (52f + (y - 52f) * k) * s
            val p = Path()
            p.moveTo(px(50f), py(10f))
            p.lineTo(px(88f), py(24f))
            p.lineTo(px(84f), py(56f))
            p.quadraticBezierTo(px(76f), py(80f), px(50f), py(95f))
            p.quadraticBezierTo(px(24f), py(80f), px(16f), py(56f))
            p.lineTo(px(12f), py(24f))
            p.close()
            return p
        }

        val sw = 2.5f * s
        val sideBrush = Brush.verticalGradient(
            listOf(pal.accent, pal.light, pal.dark),
            startY = 0f,
            endY = size.height
        )

        // Alas / cuchillas laterales
        val wings = when {
            t >= 9 -> 3
            t >= 7 -> 2
            t >= 3 -> 1
            else -> 0
        }
        for (i in 0 until wings) {
            val yb = 34f + i * 14f
            val right = poly(86f, yb + 10f, 99f, yb - 18f, 91f, yb)
            val left = poly(14f, yb + 10f, 1f, yb - 18f, 9f, yb)
            drawPath(right, brush = sideBrush)
            drawPath(right, color = pal.edge, style = Stroke(width = 1.5f * s))
            drawPath(left, brush = sideBrush)
            drawPath(left, color = pal.edge, style = Stroke(width = 1.5f * s))
        }

        // Pico superior
        if (t >= 4) {
            val crown = poly(42f, 12f, 50f, 1f, 58f, 12f)
            drawPath(crown, brush = sideBrush)
            drawPath(crown, color = pal.edge, style = Stroke(width = 1.5f * s))
        }
        if (t >= 6) {
            val l = poly(28f, 18f, 31f, 6f, 38f, 14f)
            val r = poly(72f, 18f, 69f, 6f, 62f, 14f)
            drawPath(l, brush = sideBrush)
            drawPath(r, brush = sideBrush)
            drawPath(l, color = pal.edge, style = Stroke(width = 1.5f * s))
            drawPath(r, color = pal.edge, style = Stroke(width = 1.5f * s))
        }

        // Escudo exterior
        val body = Brush.verticalGradient(
            listOf(pal.light, pal.dark),
            startY = 0f,
            endY = size.height
        )
        drawPath(shield(1f), brush = body)
        drawPath(shield(1f), color = pal.edge, style = Stroke(width = sw))

        // Escudo interior (hundido)
        val inner = Brush.verticalGradient(
            listOf(pal.dark, pal.edge),
            startY = 0f,
            endY = size.height
        )
        drawPath(shield(0.74f), brush = inner)
        drawPath(shield(0.74f), color = pal.accent.copy(alpha = 0.8f), style = Stroke(width = 1.2f * s))

        // Adorno central
        if (t < 3) {
            val line = Path()
            line.moveTo(50f * s, 30f * s)
            line.lineTo(50f * s, 80f * s)
            drawPath(line, color = pal.accent.copy(alpha = 0.6f), style = Stroke(width = 2f * s))
        }

        // Gema
        if (t >= 2) {
            val g = when {
                t == 2 -> 0.65f
                t < 5 -> 0.85f
                t >= 9 -> 1.15f
                else -> 1.0f
            }
            val cx = 50f
            val cy = 48f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(pal.gem.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(cx * s, cy * s),
                    radius = 26f * g * s
                ),
                radius = 26f * g * s,
                center = Offset(cx * s, cy * s)
            )
            val gem = poly(
                cx, cy - 16f * g,
                cx + 11f * g, cy,
                cx, cy + 20f * g,
                cx - 11f * g, cy
            )
            drawPath(
                gem,
                brush = Brush.verticalGradient(
                    listOf(pal.accent, pal.gem),
                    startY = (cy - 16f * g) * s,
                    endY = (cy + 20f * g) * s
                )
            )
            drawPath(gem, color = pal.edge, style = Stroke(width = 1.2f * s))
        }

        // Halo del rango máximo
        if (t >= 10) {
            drawCircle(
                color = pal.accent.copy(alpha = 0.55f),
                radius = 48f * s,
                center = Offset(50f * s, 52f * s),
                style = Stroke(width = 1.5f * s)
            )
        }
    }
}
