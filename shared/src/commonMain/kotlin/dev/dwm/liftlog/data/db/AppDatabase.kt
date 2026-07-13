package dev.dwm.liftlog.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
        Exercise::class, Workout::class, WorkoutSet::class,
        Program::class, ProgramDay::class, ProgramExercise::class,
        Food::class, FoodLog::class, WeightEntry::class, Setting::class,
    ],
    version = 3,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun programDao(): ProgramDao
    abstract fun foodDao(): FoodDao
    abstract fun foodLogDao(): FoodLogDao
    abstract fun weightDao(): WeightDao
    abstract fun settingDao(): SettingDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
