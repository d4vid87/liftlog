package dev.dwm.liftlog.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.db.nowMillis
import dev.dwm.liftlog.domain.Muscle
import dev.dwm.liftlog.domain.MuscleLoad
import dev.dwm.liftlog.domain.MuscleReadiness
import dev.dwm.liftlog.domain.fatigueMap
import dev.dwm.liftlog.domain.musclesFor
import dev.dwm.liftlog.domain.readiness
import dev.dwm.liftlog.domain.recoveryHours
import dev.dwm.liftlog.ui.Palette
import dev.dwm.liftlog.ui.components.FlatBar
import dev.dwm.liftlog.ui.components.FullScreenDialog
import dev.dwm.liftlog.ui.components.SectionHeader

internal fun readinessSummary(items: List<MuscleReadiness>): String =
    items.filter { it.hoursLeft > 0 }.joinToString { "${it.muscle.label()} (${it.hoursLeft}h)" }

@Composable
fun RecoveryScreen(db: AppDatabase, onDismiss: () -> Unit) {
    var items by remember { mutableStateOf<List<MuscleReadiness>>(emptyList()) }
    var fatigue by remember { mutableStateOf<Map<Muscle, Double>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val now = nowMillis()
        items = readiness(lastTrainedByMuscle(db), now)
        // same 96h fatigue window as RecoveryCard for the heatmap tint
        val since = now - 96 * 3600_000L
        val loads = mutableListOf<MuscleLoad>()
        val exCache = mutableMapOf<String, List<Muscle>>()
        for (set in db.syncDao().setsSince(since)) {
            if (!set.completed || set.deletedAt != null) continue
            val muscles = exCache.getOrPut(set.exerciseId) {
                db.exerciseDao().byId(set.exerciseId)?.let { musclesFor(it.muscles, it.category) } ?: emptyList()
            }
            if (muscles.isEmpty()) continue
            val volume = if (set.weightKg > 0) set.weightKg * set.reps else set.reps.toDouble()
            loads.add(MuscleLoad(muscles, volume, (now - set.updatedAt) / 3600_000.0))
        }
        fatigue = fatigueMap(loads)
    }

    FullScreenDialog("Recovery", onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(240.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BodyCanvas(fatigue, front = true, Modifier.weight(1f))
                BodyCanvas(fatigue, front = false, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                Text("Front", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Back", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionHeader("Readiness")
            val now = nowMillis()
            for (r in items) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.width(110.dp)) {
                        Text(r.muscle.label(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            r.lastTrainedAt?.let { at ->
                                val d = ((now - at) / 3600_000L / 24).toInt()
                                if (d == 0) "trained today" else "trained ${d}d ago"
                            } ?: "not recently",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FlatBar(
                        r.readyFraction,
                        color = if (r.hoursLeft == 0) Palette.Success else Palette.Protein,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (r.hoursLeft == 0) "READY" else "${r.hoursLeft}h",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (r.hoursLeft == 0) Palette.Success else Palette.Protein,
                        modifier = Modifier.width(52.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }

            SectionHeader("Guide")
            Text(
                "Rough rest before hitting the same muscle hard again: legs and glutes ~72h, " +
                    "chest, back, shoulders and arms ~48h, abs, obliques and calves ~24h. " +
                    "Hard sessions sit at the top of the range; light sessions need less. " +
                    "Sleep and protein do most of the recovering — the clock is a guide, not a rule.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}
