package com.joseleandro.scaniqo.data.local.tag

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "color_argb")
    val colorArgb: Int,
    @ColumnInfo(name = "icon_key")
    val iconKey: String
)
