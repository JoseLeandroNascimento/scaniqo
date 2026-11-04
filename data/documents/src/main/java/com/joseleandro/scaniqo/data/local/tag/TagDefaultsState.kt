package com.joseleandro.scaniqo.data.local.tag

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Persiste junto às tags para não recriar sugestões excluídas, inclusive após restauração do sistema. */
@Entity(tableName = "tag_defaults_state")
data class TagDefaultsState(
    @PrimaryKey val id: Int = 1,
)
