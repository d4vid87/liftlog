package dev.dwm.liftlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import dev.dwm.liftlog.ui.Palette

// ponytail: image URL guessed from the free-exercise-db name slug; wger-sourced
// exercises 404 and fall back to the initial tile. Proper fix = store image path in DB.
private fun exerciseImageUrl(name: String): String {
    val slug = name.trim()
        .replace("/", "_").replace(" ", "_")
        .replace(Regex("[^A-Za-z0-9_()'-]"), "")
    return "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/$slug/0.jpg"
}

@Composable
fun ExerciseImage(name: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        SubcomposeAsyncImage(
            model = exerciseImageUrl(name),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { InitialTile(name) },
            error = { InitialTile(name) },
        )
    }
}

@Composable
private fun InitialTile(name: String) {
    Box(
        Modifier.fillMaxSize().background(Palette.Volt.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase(),
            color = Palette.Volt,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
