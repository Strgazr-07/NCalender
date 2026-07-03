package com.ncalendar.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [EventEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class NCalendarDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile private var instance: NCalendarDatabase? = null

        fun get(context: Context): NCalendarDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NCalendarDatabase::class.java,
                "ncalendar.db"
            ).build().also { instance = it }
        }
    }
}
