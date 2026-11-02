package com.example.mibbudget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** A real recorded-spend trend, NOT a fabricated account-balance chart. */
@Composable
internal fun NeoSevenDaySpendTrend(entries: List<Tx>) {
    val today = LocalDate.now()
    val spending = entries.filter { it.amountLaari < 0L && it.category != "Internal Transfer" }
        .groupBy { it.timestamp.take(10) }
        .mapValues { (_, rows) -> rows.sumOf { -it.amountLaari } }
    val amounts = (6 downTo 0).map { spending[today.minusDays(it.toLong()).toString()] ?: 0L }
    val max = (amounts.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val color = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val width = size.width
        val height = size.height
        val points = amounts.mapIndexed { index, amount ->
            Offset(width * index / 6f, height * (0.82f - amount.toFloat() / max * 0.66f))
        }
        drawLine(color = baseline, start = Offset(0f, height * 0.86f),
            end = Offset(width, height * 0.86f), strokeWidth = 1.dp.toPx())
        val curve = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (index in 1 until points.size) {
                val p = points[index]
                val prev = points[index - 1]
                val mid = (prev.x + p.x) / 2f
                quadraticBezierTo(mid, prev.y, mid, (prev.y + p.y) / 2f)
                quadraticBezierTo(mid, p.y, p.x, p.y)
            }
        }
        val shade = Path().apply {
            moveTo(points.first().x, height)
            lineTo(points.first().x, points.first().y)
            for (index in 1 until points.size) {
                val p = points[index]
                val prev = points[index - 1]
                val mid = (prev.x + p.x) / 2f
                quadraticBezierTo(mid, prev.y, mid, (prev.y + p.y) / 2f)
                quadraticBezierTo(mid, p.y, p.x, p.y)
            }
            lineTo(points.last().x, height)
            close()
        }
        drawPath(shade, brush = Brush.verticalGradient(
            listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.0f))))
        drawPath(curve, color = color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, radius = 4.dp.toPx(), center = points.last())
    }
}
