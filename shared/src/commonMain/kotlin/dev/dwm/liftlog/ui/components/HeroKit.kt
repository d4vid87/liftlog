package dev.dwm.liftlog.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Giant count-up numeral + caption — the "one big number" that leads each screen. */
@Composable
fun HeroNumber(value: Int, caption: String, color: Color, modifier: Modifier = Modifier) {
    val animated by animateIntAsState(value, tween(900))
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$animated",
            style = MaterialTheme.typography.displayLarge,
            color = color,
        )
        Text(
            caption,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Flat uppercase section label with optional right-aligned value; replaces Card headers. */
@Composable
fun SectionHeader(
    label: String,
    trailing: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold,
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Thick rounded progress bar. */
@Composable
fun FlatBar(progress: Float, color: Color, modifier: Modifier = Modifier, height: Int = 10) {
    Row(modifier.fillMaxWidth().height(height.dp).background(Color(0xFF1C2536), RoundedCornerShape(height.dp))) {
        val p = progress.coerceIn(0f, 1f)
        if (p > 0f) {
            Row(
                Modifier.fillMaxWidth(p).height(height.dp)
                    .background(color, RoundedCornerShape(height.dp)),
            ) {}
        }
    }
}
