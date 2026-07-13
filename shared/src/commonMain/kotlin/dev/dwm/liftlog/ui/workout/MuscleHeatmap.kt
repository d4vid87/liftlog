package dev.dwm.liftlog.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.db.nowMillis
import dev.dwm.liftlog.domain.Muscle
import dev.dwm.liftlog.domain.MuscleLoad
import dev.dwm.liftlog.domain.fatigueMap
import dev.dwm.liftlog.domain.musclesFor

private val RestedColor = Color(0xFF3A3E44)
private val FatiguedColor = Color(0xFF4D9FFF)
private val BodyColor = Color(0xFF2A2D32)

/** Fitbod-style recovery heat map: front + back body, muscles tinted by 96h fatigue. */
@Composable
fun RecoveryCard(db: AppDatabase, modifier: Modifier = Modifier) {
    var fatigue by remember { mutableStateOf<Map<Muscle, Double>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val now = nowMillis()
        val since = now - 96 * 3600_000L
        val loads = mutableListOf<MuscleLoad>()
        val exCache = mutableMapOf<String, List<Muscle>>()
        for (set in db.syncDao().setsSince(since)) {
            if (!set.completed || set.deletedAt != null) continue
            val muscles = exCache.getOrPut(set.exerciseId) {
                db.exerciseDao().byId(set.exerciseId)?.let { musclesFor(it.muscles, it.category) } ?: emptyList()
            }
            if (muscles.isEmpty()) continue
            // bodyweight sets: count reps as volume so abs/cardio still register
            val volume = if (set.weightKg > 0) set.weightKg * set.reps else set.reps.toDouble()
            loads.add(MuscleLoad(muscles, volume, (now - set.updatedAt) / 3600_000.0))
        }
        fatigue = fatigueMap(loads)
    }

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Muscle Recovery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (fatigue.isEmpty()) "Train to see fatigue from the last 4 days."
                else "Blue = recently worked (last 4 days, 48h half-life).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth().height(260.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BodyCanvas(fatigue, front = true, Modifier.weight(1f))
                BodyCanvas(fatigue, front = false, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                Text("Front", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Back", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BodyCanvas(fatigue: Map<Muscle, Double>, front: Boolean, modifier: Modifier) {
    Canvas(modifier.height(260.dp)) {
        fun tint(m: Muscle) = lerp(RestedColor, FatiguedColor, (fatigue[m] ?: 0.0).toFloat())
        // silhouette base
        oval(0.5f, 0.06f, 0.11f, 0.10f, BodyColor)                    // head
        rrect(0.36f, 0.13f, 0.28f, 0.34f, BodyColor)                  // torso
        rrect(0.24f, 0.15f, 0.10f, 0.38f, BodyColor)                  // left arm
        rrect(0.66f, 0.15f, 0.10f, 0.38f, BodyColor)                  // right arm
        rrect(0.37f, 0.47f, 0.115f, 0.48f, BodyColor)                 // left leg
        rrect(0.515f, 0.47f, 0.115f, 0.48f, BodyColor)                // right leg

        if (front) {
            oval(0.335f, 0.155f, 0.10f, 0.075f, tint(Muscle.SHOULDERS)); oval(0.565f, 0.155f, 0.10f, 0.075f, tint(Muscle.SHOULDERS))
            rrect(0.38f, 0.16f, 0.115f, 0.10f, tint(Muscle.CHEST)); rrect(0.505f, 0.16f, 0.115f, 0.10f, tint(Muscle.CHEST))
            oval(0.25f, 0.24f, 0.08f, 0.13f, tint(Muscle.BICEPS)); oval(0.67f, 0.24f, 0.08f, 0.13f, tint(Muscle.BICEPS))
            rrect(0.43f, 0.275f, 0.14f, 0.17f, tint(Muscle.ABS))
            rrect(0.375f, 0.275f, 0.045f, 0.14f, tint(Muscle.OBLIQUES)); rrect(0.58f, 0.275f, 0.045f, 0.14f, tint(Muscle.OBLIQUES))
            rrect(0.375f, 0.48f, 0.105f, 0.22f, tint(Muscle.QUADS)); rrect(0.52f, 0.48f, 0.105f, 0.22f, tint(Muscle.QUADS))
            oval(0.39f, 0.76f, 0.08f, 0.14f, tint(Muscle.CALVES)); oval(0.53f, 0.76f, 0.08f, 0.14f, tint(Muscle.CALVES))
        } else {
            rrect(0.40f, 0.13f, 0.20f, 0.07f, tint(Muscle.TRAPS))
            oval(0.335f, 0.155f, 0.10f, 0.075f, tint(Muscle.SHOULDERS)); oval(0.565f, 0.155f, 0.10f, 0.075f, tint(Muscle.SHOULDERS))
            rrect(0.375f, 0.21f, 0.11f, 0.17f, tint(Muscle.LATS)); rrect(0.515f, 0.21f, 0.11f, 0.17f, tint(Muscle.LATS))
            oval(0.25f, 0.24f, 0.08f, 0.13f, tint(Muscle.TRICEPS)); oval(0.67f, 0.24f, 0.08f, 0.13f, tint(Muscle.TRICEPS))
            oval(0.385f, 0.44f, 0.11f, 0.10f, tint(Muscle.GLUTES)); oval(0.505f, 0.44f, 0.11f, 0.10f, tint(Muscle.GLUTES))
            rrect(0.375f, 0.55f, 0.105f, 0.18f, tint(Muscle.HAMSTRINGS)); rrect(0.52f, 0.55f, 0.105f, 0.18f, tint(Muscle.HAMSTRINGS))
            oval(0.39f, 0.76f, 0.08f, 0.14f, tint(Muscle.CALVES)); oval(0.53f, 0.76f, 0.08f, 0.14f, tint(Muscle.CALVES))
        }
    }
}

private fun DrawScope.rrect(x: Float, y: Float, w: Float, h: Float, color: Color) {
    drawRoundRect(
        color,
        topLeft = Offset(x * size.width, y * size.height),
        size = Size(w * size.width, h * size.height),
        cornerRadius = CornerRadius(8f, 8f),
    )
}

private fun DrawScope.oval(x: Float, y: Float, w: Float, h: Float, color: Color) {
    drawOval(
        color,
        topLeft = Offset(x * size.width, y * size.height),
        size = Size(w * size.width, h * size.height),
    )
}
