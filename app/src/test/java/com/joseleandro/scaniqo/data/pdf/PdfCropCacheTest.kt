package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.joseleandro.scaniqo.domain.model.PdfPageSpec
import com.joseleandro.scaniqo.domain.model.PdfPerspective
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.joseleandro.scaniqo.domain.model.PdfWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfCropCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun cropAndBackgroundMaskInvalidateRasterFinalPageAndThumbnailCaches() {
        val source = temporary.newFile()
        val page = PdfPageSpec("page", source.toURI().toString(), 0)
        val original = PdfWorkspace(1L, "workspace", temporary.root.toURI().toString(), listOf(page))
        val layouts = listOf(
            PdfPageLayout(crop = PdfCropBounds(.1f, .2f, .9f, .8f)),
            PdfPageLayout(backgroundMask = PdfPerspective(topLeft = PdfPoint(.1f, .1f))),
        )
        for (layout in layouts) {
            val changedPage = page.copy(layout = layout)
            val changed = original.copy(pages = listOf(changedPage))
            assertNotEquals(PdfRasterKey.from(source, page), PdfRasterKey.from(source, changedPage))
            assertNotEquals(
                PdfRasterKey.from(source, page, 4000, .98f),
                PdfRasterKey.from(source, changedPage, 4000, .98f),
            )
            assertNotEquals(PdfPreviewKey.from(original), PdfPreviewKey.from(changed))
            assertNotEquals(
                PdfSinglePagePreviewKey.from(original, "page", source),
                PdfSinglePagePreviewKey.from(changed, "page", source),
            )
            assertNotEquals(
                PdfFilterThumbnailKey.from(original, "page", source, false),
                PdfFilterThumbnailKey.from(changed, "page", source, false),
            )
        }
    }

    @Test fun fullImageCropReusesOriginalRasterCacheEntry() {
        val source = temporary.newFile()
        val page = PdfPageSpec("page", source.toURI().toString(), 0)
        assertEquals(
            PdfRasterKey.from(source, page),
            PdfRasterKey.from(source, page.copy(layout = PdfPageLayout(crop = PdfCropBounds()))),
        )
    }
}
