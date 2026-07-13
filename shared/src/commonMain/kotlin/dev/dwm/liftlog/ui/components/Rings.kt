package dev.dwm.liftlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dwm.liftlog.ui.Palette

/** MFP-style ring: Remaining = target − eaten. */
@Composable
fun CalorieRing(eaten: Double, target: Double, modifier: Modifier = Modifier) {
    val remaining = (target - eaten).toInt()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fillColor = if (remaining >= 0) Palette.Calories else Palette.Protein
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 22f, cap = StrokeCap.Round)
            val inset = 14f
            val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
            val topLeft = Offset(inset, inset)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = stroke)
            val sweep = ((eaten / target).coerceIn(0.0, 1.0) * 360).toFloat()
            drawArc(fillColor, -90f, sweep, false, topLeft, arcSize, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$remaining", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                if (remaining >= 0) "Remaining" else "Over",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MacroBar(label: String, grams: Double, targetGrams: Double, color: Color) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                "${grams.toInt()} / ${targetGrams.toInt()} g",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier.fillMaxWidth().height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
        ) {
            Box(
                Modifier.fillMaxWidth((grams / targetGrams).toFloat().coerceIn(0f, 1f))
                    .height(6.dp).background(color, RoundedCornerShape(3.dp))
            )
        }
    }
}
