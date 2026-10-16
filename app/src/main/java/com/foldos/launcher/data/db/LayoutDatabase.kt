package com.foldos.launcher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Home layout database: pages and the items on them.
 *
 * Version history:
 * 1. pages + items (Phase 1).
 * 2. items gained the widget (`app_widget_id`, `builtin_id`), stack (`stack_id`, `stack_index`)
 *    and free-placement (`free`, `x_dp`, `y_dp`, `w_dp`, `h_dp`) columns.
 */
@Database(
    entities = [PageEntity::class, ItemEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(LayoutConverters::class)
abstract class LayoutDatabase : RoomDatabase() {

    abstract fun layoutDao(): LayoutDao

    companion object {
        const val DATABASE_NAME: String = "foldos_layout.db"

        /** Adds the Phase 2 item columns; existing rows keep their grid placement. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN app_widget_id INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN builtin_id TEXT")
                db.execSQL("ALTER TABLE items ADD COLUMN stack_id INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN stack_index INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE items ADD COLUMN free INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE items ADD COLUMN x_dp REAL")
                db.execSQL("ALTER TABLE items ADD COLUMN y_dp REAL")
                db.execSQL("ALTER TABLE items ADD COLUMN w_dp REAL")
                db.execSQL("ALTER TABLE items ADD COLUMN h_dp REAL")
            }
        }

        /** Builds a new instance; callers (the [com.foldos.launcher.data.AppGraphImpl]) keep it as a singleton. */
        fun create(context: Context): LayoutDatabase =
            Room.databaseBuilder(context.applicationContext, LayoutDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
