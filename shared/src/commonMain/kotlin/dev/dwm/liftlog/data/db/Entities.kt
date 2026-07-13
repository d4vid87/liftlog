package dev.dwm.liftlog.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val muscles: String,
    val equipment: String,
    val custom: Boolean = false,
)

@Entity
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
)

@Entity(
    indices = [Index("workoutId"), Index("exerciseId")]
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double? = null,
    val completed: Boolean = false,
)
