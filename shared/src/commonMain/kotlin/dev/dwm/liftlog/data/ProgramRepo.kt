package dev.dwm.liftlog.data

import dev.dwm.liftlog.data.db.AppDatabase
import dev.dwm.liftlog.data.db.Exercise
import dev.dwm.liftlog.data.db.Program
import dev.dwm.liftlog.data.db.ProgramExercise
import dev.dwm.liftlog.data.db.Workout
import dev.dwm.liftlog.data.db.WorkoutSet
import dev.dwm.liftlog.data.db.nowMillis
import dev.dwm.liftlog.domain.prescription
import dev.dwm.liftlog.domain.progress

suspend fun getOrCreateExercise(db: AppDatabase, name: String, category: String = ""): Exercise {
    db.exerciseDao().byName(name)?.let { return it }
    val exercise = Exercise(name = name, category = category, muscles = "", equipment = "", custom = true)
    db.exerciseDao().insert(exercise)
    return exercise
}

/** Create a Workout pre-filled with today's prescribed sets. Returns it, or null if program has no days. */
suspend fun startProgramWorkout(db: AppDatabase, program: Program): Workout? {
    val days = db.programDao().daysFor(program.id)
    if (days.isEmpty()) return null
    val day = days[program.currentDayIndex % days.size]
    val workout = Workout(name = "${program.name} — ${day.name}", startedAt = nowMillis(), programDayId = day.id)
    db.workoutDao().insertWorkout(workout)
    for (pe in db.programDao().exercisesForDay(day.id)) {
        prescription(pe).forEachIndexed { i, p ->
            db.workoutDao().insertSet(
                WorkoutSet(
                    workoutId = workout.id,
                    exerciseId = pe.exerciseId,
                    setIndex = i,
                    weightKg = p.weightKg,
                    reps = p.reps,
                    targetReps = p.reps,
                    amrap = p.amrap,
                )
            )
        }
    }
    return workout
}

/** After finishing a program workout: apply progression per exercise, advance the program day pointer. */
suspend fun applyProgression(db: AppDatabase, workout: Workout) {
    val dayId = workout.programDayId ?: return
    val day = db.programDao().dayById(dayId) ?: return
    val program = db.programDao().programById(day.programId) ?: return
    val setsByExercise = db.workoutDao().setsForWorkoutOnce(workout.id)
        .filter { it.completed }
        .groupBy { it.exerciseId }
    for (pe in db.programDao().exercisesForDay(dayId)) {
        val done = setsByExercise[pe.exerciseId] ?: continue
        db.programDao().updateProgramExercise(
            progress(pe, done).copy(updatedAt = nowMillis())
        )
    }
    val dayCount = db.programDao().daysFor(program.id).size
    db.programDao().updateProgram(
        program.copy(currentDayIndex = (program.currentDayIndex + 1) % dayCount, updatedAt = nowMillis())
    )
}
