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
 * 3. items gained the folder (`parent_id` + index, `folder_style`) and app-pair
 *    (`package_name_2`, `activity_name_2`) columns.
 * 4. Round 2.0 (free canvas + cards): items gained `card_style` and `card_clear`; the per-style pages
 *    were converted to canvas content (see [MIGRATION_3_4] / [CanvasMigration]).
 * 5. 1.4.0: the widget card flavor was removed; its widgets became loose items (see [MIGRATION_4_5] / [WidgetCardMigration]). No column changes.
 */
@Database(
    entities = [PageEntity::class, ItemEntity::class],
    version = 5,
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

        /** Adds the Phase 4 folder and app-pair columns; the index name follows Room's `index_<table>_<column>` convention. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN parent_id INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN package_name_2 TEXT")
                db.execSQL("ALTER TABLE items ADD COLUMN activity_name_2 TEXT")
                db.execSQL("ALTER TABLE items ADD COLUMN folder_style TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_parent_id ON items (parent_id)")
            }
        }

        /**
         * Round 2.0: adds the card columns, then converts every page from its old style to free-canvas
         * content (DASHBOARD widgets re-spanned onto the page grid, LIST pages into one apps card in list
         * style, HYBRID pages into a widget card over an app card) and packs each page so nothing overlaps.
         * See [CanvasMigration].
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN card_style TEXT")
                db.execSQL("ALTER TABLE items ADD COLUMN card_clear INTEGER NOT NULL DEFAULT 0")
                CanvasMigration.convertPages(db)
            }
        }

        /**
         * 1.4.0: widget cards no longer exist. Each one is deleted and its widgets become loose items on the
         * same page (first at the card's cell and span, several in one stack there); see [WidgetCardMigration].
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                WidgetCardMigration.dissolve(db)
            }
        }

        /** Builds a new instance; callers (the [com.foldos.launcher.data.AppGraphImpl]) keep it as a singleton. */
        fun create(context: Context): LayoutDatabase =
            Room.databaseBuilder(context.applicationContext, LayoutDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
