package com.joseleandro.scaniqo.data.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageIndexTest {
    @Test fun indexedPagesKeepIdentityAndInheritedAttributes() {
        PDDocument().use { document ->
            val pages = List(3) { PDPage() }
            pages.forEach(document::addPage)
            val tree = document.pages.cosObject
            val resources = PDResources()
            tree.setItem(COSName.RESOURCES, resources)
            tree.setItem(COSName.MEDIA_BOX, PDRectangle(320f, 480f).cosArray)
            tree.setInt(COSName.ROTATE, 90)
            pages.forEach { page ->
                page.cosObject.removeItem(COSName.MEDIA_BOX)
                page.cosObject.removeItem(COSName.RESOURCES)
                page.cosObject.removeItem(COSName.ROTATE)
            }

            val index = PdfPageIndex(document)
            assertEquals(3, index.size)
            assertSame(index[2], index[2])
            assertSame(pages[2].cosObject, index[2].cosObject)
            assertSame(resources.cosObject, index[2].resources.cosObject)
            assertEquals(320f, index[2].mediaBox.width, 0f)
            assertEquals(480f, index[2].mediaBox.height, 0f)
            assertEquals(90, index[2].rotation)
            assertTrue(runCatching { index[-1] }.exceptionOrNull() is IndexOutOfBoundsException)
            assertTrue(runCatching { index[3] }.exceptionOrNull() is IndexOutOfBoundsException)
        }
    }

    @Test fun cancelledOperationDoesNotBuildItsPageIndex() {
        PDDocument().use { document ->
            document.addPage(PDPage())
            val context = Job().apply { cancel() }
            assertTrue(runCatching { PdfPageIndex(document, context) }.exceptionOrNull() is CancellationException)
        }
    }
}
