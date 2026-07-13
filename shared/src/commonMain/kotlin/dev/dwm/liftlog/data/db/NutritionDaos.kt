package dev.dwm.liftlog.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: Food)

    @Query("SELECT * FROM Food WHERE deletedAt IS NULL AND barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): Food?

    @Query("SELECT * FROM Food WHERE id = :id")
    suspend fun byId(id: String): Food?

    @Query("SELECT * FROM Food WHERE deletedAt IS NULL AND name LIKE '%' || :query || '%' ORDER BY name LIMIT 50")
    suspend fun search(query: String): List<Food>

    @Query(
        """SELECT f.* FROM Food f JOIN (
             SELECT foodId, MAX(updatedAt) AS lastUsed FROM FoodLog WHERE deletedAt IS NULL GROUP BY foodId
           ) recent ON recent.foodId = f.id
           WHERE f.deletedAt IS NULL ORDER BY recent.lastUsed DESC LIMIT 20"""
    )
    suspend fun recentFoods(): List<Food>
}

@Dao
interface FoodLogDao {
    @Insert
    suspend fun insert(log: FoodLog)

    @Update
    suspend fun update(log: FoodLog)

    @Query("UPDATE FoodLog SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun delete(id: String, now: Long = nowMillis())

    @Query("SELECT * FROM FoodLog WHERE deletedAt IS NULL AND epochDay = :epochDay ORDER BY meal, updatedAt")
    fun forDay(epochDay: Long): Flow<List<FoodLog>>

    @Query("SELECT * FROM FoodLog WHERE deletedAt IS NULL AND epochDay BETWEEN :from AND :to")
    suspend fun forRangeOnce(from: Long, to: Long): List<FoodLog>

    @Query(
        """SELECT l.epochDay AS epochDay, SUM(l.grams * f.kcal / 100.0) AS kcal
           FROM FoodLog l JOIN Food f ON f.id = l.foodId
           WHERE l.deletedAt IS NULL AND l.epochDay >= :fromDay
           GROUP BY l.epochDay ORDER BY l.epochDay"""
    )
    suspend fun dailyKcals(fromDay: Long): List<DailyKcal>

    @Query(
        """SELECT l.epochDay AS epochDay,
                  SUM(l.grams * f.kcal / 100.0) AS kcal,
                  SUM(l.grams * f.protein / 100.0) AS protein,
                  SUM(l.grams * f.carbs / 100.0) AS carbs,
                  SUM(l.grams * f.fat / 100.0) AS fat
           FROM FoodLog l JOIN Food f ON f.id = l.foodId
           WHERE l.deletedAt IS NULL AND l.epochDay >= :fromDay
           GROUP BY l.epochDay ORDER BY l.epochDay"""
    )
    suspend fun dailyMacros(fromDay: Long): List<DailyMacro>
}

data class DailyMacro(val epochDay: Long, val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

@Dao
interface WeightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntry)

    @Query("SELECT * FROM WeightEntry WHERE deletedAt IS NULL AND epochDay = :epochDay LIMIT 1")
    suspend fun forDay(epochDay: Long): WeightEntry?

    @Query("SELECT * FROM WeightEntry WHERE deletedAt IS NULL ORDER BY epochDay")
    suspend fun all(): List<WeightEntry>
}

@Dao
interface GroceryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: GroceryItem)

    @Query("UPDATE GroceryItem SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun delete(id: String, now: Long = nowMillis())

    @Query("UPDATE GroceryItem SET deletedAt = :now, updatedAt = :now WHERE checked = 1 AND deletedAt IS NULL")
    suspend fun clearChecked(now: Long = nowMillis())

    @Query("SELECT * FROM GroceryItem WHERE deletedAt IS NULL ORDER BY checked, name")
    fun items(): Flow<List<GroceryItem>>
}

@Dao
interface SettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: Setting)

    @Query("SELECT value FROM Setting WHERE key = :key AND deletedAt IS NULL")
    suspend fun get(key: String): String?
}

data class DailyKcal(val epochDay: Long, val kcal: Double)
