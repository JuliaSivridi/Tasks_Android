package com.stler.tasks.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.stler.tasks.data.local.dao.CalendarEventDao
import com.stler.tasks.data.local.dao.FolderDao
import com.stler.tasks.data.local.dao.LabelDao
import com.stler.tasks.data.local.dao.SyncQueueDao
import com.stler.tasks.data.local.dao.TaskDao
import com.stler.tasks.data.local.entity.CalendarEventEntity
import com.stler.tasks.data.local.entity.FolderEntity
import com.stler.tasks.data.local.entity.LabelEntity
import com.stler.tasks.data.local.entity.SyncQueueEntity
import com.stler.tasks.data.local.entity.TaskEntity

@Database(
    entities = [
        TaskEntity::class,
        FolderEntity::class,
        LabelEntity::class,
        SyncQueueEntity::class,
        CalendarEventEntity::class,
    ],
    version = 7,   // v7: add calendarId + recurringEventId indices on calendar_events
    exportSchema = true,
)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun folderDao(): FolderDao
    abstract fun labelDao(): LabelDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun calendarEventDao(): CalendarEventDao

    companion object {
        /** Adds the calendar_events table; preserves all existing data. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE calendar_events (
                        id            TEXT NOT NULL PRIMARY KEY,
                        calendarId    TEXT NOT NULL,
                        calendarName  TEXT NOT NULL,
                        calendarColor TEXT NOT NULL,
                        title         TEXT NOT NULL,
                        startDate     TEXT NOT NULL,
                        startTime     TEXT NOT NULL,
                        endDate       TEXT NOT NULL,
                        endTime       TEXT NOT NULL,
                        isAllDay      INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Adds recurringEventId column ('' default — pure cache, no data loss). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE calendar_events ADD COLUMN recurringEventId TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** Adds indices on calendarId and recurringEventId for faster queries. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_events_calendarId ON calendar_events(calendarId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_calendar_events_recurringEventId ON calendar_events(recurringEventId)")
            }
        }
    }
}
