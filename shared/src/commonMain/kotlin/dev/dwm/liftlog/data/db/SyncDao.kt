package dev.dwm.liftlog.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Raw since/upsert access per table for the sync engine. LWW checks happen in SyncEngine. */
@Dao
interface SyncDao {
    @Query("SELECT * FROM Exercise WHERE updatedAt > :since")
    suspend fun exercisesSince(since: Long): List<Exercise>

    @Query("SELECT * FROM Workout WHERE updatedAt > :since")
    suspend fun workoutsSince(since: Long): List<Workout>

    @Query("SELECT * FROM WorkoutSet WHERE updatedAt > :since")
    suspend fun setsSince(since: Long): List<WorkoutSet>

    @Query("SELECT * FROM Program WHERE updatedAt > :since")
    suspend fun programsSince(since: Long): List<Program>

    @Query("SELECT * FROM ProgramDay WHERE updatedAt > :since")
    suspend fun programDaysSince(since: Long): List<ProgramDay>

    @Query("SELECT * FROM ProgramExercise WHERE updatedAt > :since")
    suspend fun programExercisesSince(since: Long): List<ProgramExercise>

    @Query("SELECT * FROM Food WHERE updatedAt > :since")
    suspend fun foodsSince(since: Long): List<Food>

    @Query("SELECT * FROM FoodLog WHERE updatedAt > :since")
    suspend fun foodLogsSince(since: Long): List<FoodLog>

    @Query("SELECT * FROM WeightEntry WHERE updatedAt > :since")
    suspend fun weightsSince(since: Long): List<WeightEntry>

    @Query("SELECT updatedAt FROM Exercise WHERE id = :id")
    suspend fun exerciseUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM Workout WHERE id = :id")
    suspend fun workoutUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM WorkoutSet WHERE id = :id")
    suspend fun setUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM Program WHERE id = :id")
    suspend fun programUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM ProgramDay WHERE id = :id")
    suspend fun programDayUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM ProgramExercise WHERE id = :id")
    suspend fun programExerciseUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM Food WHERE id = :id")
    suspend fun foodUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM FoodLog WHERE id = :id")
    suspend fun foodLogUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM WeightEntry WHERE id = :id")
    suspend fun weightUpdatedAt(id: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercise(row: Exercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkout(row: Workout)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSet(row: WorkoutSet)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgram(row: Program)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgramDay(row: ProgramDay)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgramExercise(row: ProgramExercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFood(row: Food)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFoodLog(row: FoodLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeight(row: WeightEntry)
}
