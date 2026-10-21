package com.joseleandro.scaniqo.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameTable
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import com.joseleandro.scaniqo.data.local.tag.TagDao
import com.joseleandro.scaniqo.data.local.tag.TagEntity
import com.joseleandro.scaniqo.data.local.document.DocumentDao
import com.joseleandro.scaniqo.data.local.document.DocumentEntity

@Database(
    entities = [DocumentEntity::class, TagEntity::class],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = ScaniqoDatabase.RenameTagsMigration::class),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
    ],
)
abstract class ScaniqoDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun tagDao(): TagDao

    @RenameTable(fromTableName = "categories", toTableName = "tags")
    class RenameTagsMigration : AutoMigrationSpec
}
