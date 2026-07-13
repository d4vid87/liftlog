package dev.dwm.liftlog.data.seed

import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.db.Exercise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import liftlog.shared.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

@Serializable
private data class SeedExercise(
    val id: Long,
    val name: String,
    val category: String = "",
    val muscles: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
)

@OptIn(ExperimentalResourceApi::class)
suspend fun seedExercisesIfEmpty(db: AppDatabase) {
    val dao = db.exerciseDao()
    if (dao.count() > 0) return
    val bytes = Res.readBytes("files/exercises.json")
    val seed = Json { ignoreUnknownKeys = true }
        .decodeFromString<List<SeedExercise>>(bytes.decodeToString())
    dao.insertAll(
        seed.map {
            Exercise(
                name = it.name,
                category = it.category,
                muscles = it.muscles.joinToString(", "),
                equipment = it.equipment.joinToString(", "),
            )
        }
    )
}
