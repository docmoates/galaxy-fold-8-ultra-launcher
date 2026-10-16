package com.foldos.launcher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** Home layout database: pages and the items on them. */
@Database(
    entities = [PageEntity::class, ItemEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(LayoutConverters::class)
abstract class LayoutDatabase : RoomDatabase() {

    abstract fun layoutDao(): LayoutDao

    companion object {
        const val DATABASE_NAME: String = "foldos_layout.db"

        /** Builds a new instance; callers (the [com.foldos.launcher.data.AppGraphImpl]) keep it as a singleton. */
        fun create(context: Context): LayoutDatabase =
            Room.databaseBuilder(context.applicationContext, LayoutDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
