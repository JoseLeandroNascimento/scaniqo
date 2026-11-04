package com.joseleandro.scaniqo.data.mapper

import com.joseleandro.scaniqo.data.local.tag.TagEntity
import com.joseleandro.scaniqo.data.mapper.toEntity
import com.joseleandro.scaniqo.data.mapper.toDomain
import com.joseleandro.scaniqo.domain.model.TagIcon
import com.joseleandro.scaniqo.domain.model.TagModel
import org.junit.Assert.assertEquals
import org.junit.Test

class TagMappersTest {
    @Test
    fun entityRoundTripPreservesMetadataAndColorTransparency() {
        val entity = TagEntity(
            id = 42L,
            name = "Recibos",
            colorArgb = 0x804080C0.toInt(),
            iconKey = TagIcon.DOCUMENT.key,
        )

        val uiModel = entity.toDomain()

        assertEquals(entity.colorArgb, uiModel.colorArgb)
        assertEquals(entity, uiModel.toEntity())
    }

    @Test
    fun uiRoundTripPreservesColorAndMetadata() {
        val uiModel = TagModel(
            id = 7L,
            name = "Contratos",
            colorArgb = 0x804080C0.toInt(),
            iconKey = TagIcon.DOCUMENT.key,
        )

        val entity = uiModel.toEntity()
        val restoredUiModel = entity.toDomain()

        assertEquals(uiModel.colorArgb, entity.colorArgb)
        assertEquals(uiModel.colorArgb, restoredUiModel.colorArgb)
        assertEquals(uiModel.id, restoredUiModel.id)
        assertEquals(uiModel.name, restoredUiModel.name)
        assertEquals(uiModel.iconKey, restoredUiModel.iconKey)
    }

    @Test
    fun documentKeyResolvesToDocumentIcon() {
        val entity = TagEntity(
            name = "Documentos",
            colorArgb = 0xFF4080C0.toInt(),
            iconKey = "document",
        )

        assertEquals(TagIcon.DOCUMENT, entity.toDomain().icon)
    }

    @Test
    fun unknownIconUsesFolderWhilePreservingItsStoredKey() {
        val entity = TagEntity(
            id = 11L,
            name = "Outros",
            colorArgb = 0x804080C0.toInt(),
            iconKey = "future_icon",
        )

        val uiModel = entity.toDomain()

        assertEquals(TagIcon.FOLDER, uiModel.icon)
        assertEquals("future_icon", uiModel.iconKey)
        assertEquals(entity, uiModel.toEntity())
    }
}
