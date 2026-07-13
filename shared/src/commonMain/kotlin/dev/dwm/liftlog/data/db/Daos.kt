package dev.dwm.liftlog.data.db

import androidx.room.Dao
import androidx.room.Delete
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
    suspend fun insert(exercise: Exercise): Long

    @Query("SELECT * FROM Exercise WHERE name LIKE '%' || :query || '%' ORDER BY name")
    fun search(query: String): Flow<List<Exercise>>

    @Query("SELECT * FROM Exercise WHERE id = :id")
    suspend fun byId(id: Long): Exercise?
}

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertWorkout(workout: Workout): Long

    @Update
    suspend fun updateWorkout(workout: Workout)

    @Query("DELETE FROM Workout WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Insert
    suspend fun insertSet(set: WorkoutSet): Long

    @Update
    suspend fun updateSet(set: WorkoutSet)

    @Delete
    suspend fun deleteSet(set: WorkoutSet)

    @Query("SELECT * FROM Workout WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeWorkout(): Workout?

    @Query("SELECT * FROM Workout WHERE finishedAt IS NOT NULL ORDER BY startedAt DESC")
    fun history(): Flow<List<Workout>>

    @Query("SELECT * FROM WorkoutSet WHERE workoutId = :workoutId ORDER BY id")
    fun setsForWorkout(workoutId: Long): Flow<List<WorkoutSet>>

    @Query(
        """SELECT * FROM WorkoutSet WHERE exerciseId = :exerciseId AND workoutId = (
             SELECT ws.workoutId FROM WorkoutSet ws
             JOIN Workout w ON w.id = ws.workoutId
             WHERE ws.exerciseId = :exerciseId AND w.id != :excludeWorkoutId AND w.finishedAt IS NOT NULL
             ORDER BY w.startedAt DESC LIMIT 1
           ) ORDER BY setIndex"""
    )
    suspend fun previousSets(exerciseId: Long, excludeWorkoutId: Long): List<WorkoutSet>

    @Query(
        """SELECT w.startedAt AS time, MAX(s.weightKg * (1 + s.reps / 30.0)) AS e1rm
           FROM WorkoutSet s
           JOIN Workout w ON w.id = s.workoutId
           WHERE s.exerciseId = :exerciseId AND w.finishedAt IS NOT NULL AND s.completed
           GROUP BY s.workoutId
           ORDER BY w.startedAt"""
    )
    suspend fun e1rmHistory(exerciseId: Long): List<E1rmPoint>

    @Query(
        """SELECT DISTINCT e.* FROM Exercise e
           JOIN WorkoutSet s ON s.exerciseId = e.id
           JOIN Workout w ON w.id = s.workoutId
           WHERE w.finishedAt IS NOT NULL
           ORDER BY e.name"""
    )
    fun loggedExercises(): Flow<List<Exercise>>
}

data class E1rmPoint(val time: Long, val e1rm: Double)
