package dev.dwm.liftlog.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun createDatabase(context: Context): AppDatabase =
    Room.databaseBuilder<AppDatabase>(context, context.getDatabasePath("liftlog.db").absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
