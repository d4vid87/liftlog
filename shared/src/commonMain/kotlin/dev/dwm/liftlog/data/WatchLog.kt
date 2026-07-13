package dev.dwm.liftlog.data

import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.db.Workout
import dev.dwm.liftlog.data.db.WorkoutSet
import dev.dwm.liftlog.data.db.nowMillis

/** Log a completed set sent from the watch: reuses the active workout or starts one. */
suspend fun logSetFromWatch(db: AppDatabase, exerciseName: String, weightKg: Double, reps: Int) {
    val workout = db.workoutDao().activeWorkout() ?: Workout(name = "Watch Workout", startedAt = nowMillis())
        .also { db.workoutDao().insertWorkout(it) }
    val exercise = getOrCreateExercise(db, exerciseName)
    val nextIndex = db.workoutDao().setsForWorkoutOnce(workout.id)
        .filter { it.exerciseId == exercise.id }.size
    db.workoutDao().insertSet(
        WorkoutSet(
            workoutId = workout.id,
            exerciseId = exercise.id,
            setIndex = nextIndex,
            weightKg = weightKg,
            reps = reps,
            completed = true,
        )
    )
}
