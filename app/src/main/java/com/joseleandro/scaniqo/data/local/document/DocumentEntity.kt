package com.joseleandro.scaniqo.data.local.document

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.joseleandro.scaniqo.data.local.tag.TagEntity

/** Metadados do PDF permanente, copiado para o armazenamento privado do aplicativo. */
@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["tagId"]),
        Index(value = ["pdfUri"]),
        Index(value = ["deletedAt", "createdAt", "id"]),
        Index(value = ["deletedAt", "id"]),
    ]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val pdfUri: String,
    val pageCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val sizeBytes: Long = 0,
    val thumbnailUri: String? = null,
    val tagId: Long? = null,
    val protectionKeyId: String? = null,
    val deletedAt: Long? = null
)
