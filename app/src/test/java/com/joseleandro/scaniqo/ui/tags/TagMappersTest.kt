package com.joseleandro.scaniqo.ui.tags

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.joseleandro.scaniqo.data.local.tag.TagEntity
import com.joseleandro.scaniqo.domain.mapper.toEntity
import com.joseleandro.scaniqo.domain.mapper.toUiModel
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

        val uiModel = entity.toUiModel()

        assertEquals(entity.colorArgb, uiModel.color.toArgb())
        assertEquals(entity, uiModel.toEntity())
    }

    @Test
    fun uiRoundTripPreservesColorAndMetadata() {
        val uiModel = TagModel(
            id = 7L,
            name = "Contratos",
            color = Color(0x804080C0.toInt()),
            iconKey = TagIcon.DOCUMENT.key,
        )

        val entity = uiModel.toEntity()
        val restoredUiModel = entity.toUiModel()

        assertEquals(uiModel.color.toArgb(), entity.colorArgb)
        assertEquals(uiModel.color.toArgb(), restoredUiModel.color.toArgb())
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

        assertEquals(TagIcon.DOCUMENT, entity.toUiModel().icon)
    }

    @Test
    fun unknownIconUsesFolderWhilePreservingItsStoredKey() {
        val entity = TagEntity(
            id = 11L,
            name = "Outros",
            colorArgb = 0x804080C0.toInt(),
            iconKey = "future_icon",
        )

        val uiModel = entity.toUiModel()

        assertEquals(TagIcon.FOLDER, uiModel.icon)
        assertEquals("future_icon", uiModel.iconKey)
        assertEquals(entity, uiModel.toEntity())
    }
}
