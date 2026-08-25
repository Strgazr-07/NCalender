package com.ncalendar.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [EventEntity::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class NCalendarDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile private var instance: NCalendarDatabase? = null

        /**
         * Adds the per-occurrence exception-tracking column (EventEntity.repeatExceptionDates)
         * needed for "edit/delete just this occurrence" of a recurring local-only event. This
         * MUST stay an explicit migration, never left to fallbackToDestructiveMigration below —
         * that would silently wipe every local-only user's entire event store on upgrade. Any
         * future schema change needs the same treatment: add a Migration(n, n+1) here first.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN repeatExceptionDates TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): NCalendarDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NCalendarDatabase::class.java,
                "ncalendar.db"
            )
                .addMigrations(MIGRATION_2_3)
                // Only a genuinely unplanned future version jump should ever hit this path —
                // every migration this app ships must be listed above instead.
                .fallbackToDestructiveMigration(true)
                .build().also { instance = it }
        }
    }
}
