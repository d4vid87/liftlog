package dev.dwm.liftlog.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT COUNT(*) FROM Exercise")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(exercises: List<Exercise>)

    @Insert
    suspend fun insert(exercise: Exercise)

    @Query("SELECT * FROM Exercise WHERE deletedAt IS NULL AND name LIKE '%' || :query || '%' ORDER BY name")
    fun search(query: String): Flow<List<Exercise>>

    @Query("SELECT * FROM Exercise WHERE id = :id")
    suspend fun byId(id: String): Exercise?

    @Query("SELECT * FROM Exercise WHERE deletedAt IS NULL AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): Exercise?
}

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertWorkout(workout: Workout)

    @Update
    suspend fun updateWorkout(workout: Workout)

    @Query("UPDATE Workout SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun deleteWorkout(id: String, now: Long = nowMillis())

    @Insert
    suspend fun insertSet(set: WorkoutSet)

    @Update
    suspend fun updateSet(set: WorkoutSet)

    @Query("UPDATE WorkoutSet SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun deleteSet(id: String, now: Long = nowMillis())

    @Query("SELECT * FROM Workout WHERE deletedAt IS NULL AND finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeWorkout(): Workout?

    @Query("SELECT * FROM Workout WHERE deletedAt IS NULL AND finishedAt IS NOT NULL ORDER BY startedAt DESC")
    fun history(): Flow<List<Workout>>

    @Query("SELECT * FROM WorkoutSet WHERE deletedAt IS NULL AND workoutId = :workoutId ORDER BY setIndex, updatedAt")
    fun setsForWorkout(workoutId: String): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM WorkoutSet WHERE deletedAt IS NULL AND workoutId = :workoutId ORDER BY setIndex, updatedAt")
    suspend fun setsForWorkoutOnce(workoutId: String): List<WorkoutSet>

    @Query(
        """SELECT * FROM WorkoutSet WHERE deletedAt IS NULL AND exerciseId = :exerciseId AND workoutId = (
             SELECT ws.workoutId FROM WorkoutSet ws
             JOIN Workout w ON w.id = ws.workoutId
             WHERE ws.exerciseId = :exerciseId AND w.id != :excludeWorkoutId
               AND w.finishedAt IS NOT NULL AND w.deletedAt IS NULL
             ORDER BY w.startedAt DESC LIMIT 1
           ) ORDER BY setIndex"""
    )
    suspend fun previousSets(exerciseId: String, excludeWorkoutId: String): List<WorkoutSet>

    @Query(
        """SELECT w.startedAt AS time, MAX(s.weightKg * (1 + s.reps / 30.0)) AS e1rm
           FROM WorkoutSet s
           JOIN Workout w ON w.id = s.workoutId
           WHERE s.exerciseId = :exerciseId AND w.finishedAt IS NOT NULL AND s.completed
             AND s.deletedAt IS NULL AND w.deletedAt IS NULL
           GROUP BY s.workoutId
           ORDER BY w.startedAt"""
    )
    suspend fun e1rmHistory(exerciseId: String): List<E1rmPoint>

    @Query(
        """SELECT DISTINCT e.* FROM Exercise e
           JOIN WorkoutSet s ON s.exerciseId = e.id
           JOIN Workout w ON w.id = s.workoutId
           WHERE w.finishedAt IS NOT NULL AND w.deletedAt IS NULL AND s.deletedAt IS NULL
           ORDER BY e.name"""
    )
    fun loggedExercises(): Flow<List<Exercise>>
}

@Dao
interface ProgramDao {
    @Insert
    suspend fun insertProgram(program: Program)

    @Update
    suspend fun updateProgram(program: Program)

    @Query("UPDATE Program SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun deleteProgram(id: String, now: Long = nowMillis())

    @Insert
    suspend fun insertDay(day: ProgramDay)

    @Insert
    suspend fun insertProgramExercise(pe: ProgramExercise)

    @Update
    suspend fun updateProgramExercise(pe: ProgramExercise)

    @Query("SELECT * FROM Program WHERE deletedAt IS NULL ORDER BY name")
    fun programs(): Flow<List<Program>>

    @Query("SELECT * FROM Program WHERE id = :id")
    suspend fun programById(id: String): Program?

    @Query("SELECT * FROM ProgramDay WHERE deletedAt IS NULL AND programId = :programId ORDER BY dayIndex")
    suspend fun daysFor(programId: String): List<ProgramDay>

    @Query("SELECT * FROM ProgramExercise WHERE deletedAt IS NULL AND programDayId = :dayId ORDER BY position")
    suspend fun exercisesForDay(dayId: String): List<ProgramExercise>

    @Query("SELECT * FROM ProgramDay WHERE id = :id")
    suspend fun dayById(id: String): ProgramDay?
}

data class E1rmPoint(val time: Long, val e1rm: Double)
