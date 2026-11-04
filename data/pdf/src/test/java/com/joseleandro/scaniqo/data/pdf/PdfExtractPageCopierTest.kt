package com.joseleandro.scaniqo.data.pdf

import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class, manifest = Config.NONE)
class PdfExtractPageCopierTest {
    @Test fun extractionKeepsMixedPageSizesCropAndRotation() {
        PDDocument().use { source ->
            source.addPage(PDPage(PDRectangle.LETTER).apply {
                cropBox = PDRectangle(12f, 24f, 300f, 400f)
                rotation = 90
                resources = PDResources()
            })
            source.addPage(PDPage(PDRectangle(200f, 300f)).apply { resources = PDResources() })
            PDDocument().use { output ->
                val copier = PdfExtractPageCopier(output)
                copier.copy(source.getPage(1))
                copier.copy(source.getPage(0))
                val bytes = ByteArrayOutputStream().also(output::save).toByteArray()
                PDDocument.load(bytes).use { loaded ->
                    assertEquals(2, loaded.numberOfPages)
                    assertEquals(200f, loaded.getPage(0).mediaBox.width, 0f)
                    assertEquals(300f, loaded.getPage(0).mediaBox.height, 0f)
                    val page = loaded.getPage(1)
                    assertEquals(612f, page.mediaBox.width, 0f)
                    assertEquals(90, page.rotation)
                    assertEquals(12f, page.cropBox.lowerLeftX, 0f)
                    assertEquals(24f, page.cropBox.lowerLeftY, 0f)
                    assertEquals(300f, page.cropBox.width, 0f)
                }
            }
        }
    }

    @Test fun annotationPageReferencesDoNotBringUnselectedPagesIntoOutput() {
        PDDocument().use { source ->
            val selected = PDPage(PDRectangle.LETTER).apply { resources = PDResources() }
            val excluded = PDPage(PDRectangle.LETTER).apply {
                cosObject.setString(COSName.getPDFName("PrivateMarker"), "EXCLUDED_PAGE_SECRET")
            }
            source.addPage(selected)
            source.addPage(excluded)
            selected.annotations = listOf(com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText().apply {
                contents = "Comentário"
                page = selected
                rectangle = PDRectangle(20f, 20f, 16f, 16f)
            })
            PDDocument().use { output ->
                PdfExtractPageCopier(output).copy(selected)
                val bytes = ByteArrayOutputStream().also(output::save).toByteArray()
                org.junit.Assert.assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("EXCLUDED_PAGE_SECRET"))
                PDDocument.load(bytes).use { loaded ->
                    assertEquals(1, loaded.numberOfPages)
                    assertEquals("Comentário", loaded.getPage(0).annotations.single().contents)
                }
            }
        }
    }

    @Test fun inheritedResourcesAreClonedBeforeOriginalCloses() {
        val content = "0.2 0.4 0.6 rg 20 30 100 200 re f\n".toByteArray()
        PDDocument().use { output ->
            PDDocument().use { source ->
                val parentResources = PDResources().apply {
                    cosObject.setString(COSName.getPDFName("Test"), "original")
                }
                val page = PDPage(PDRectangle.LETTER).apply {
                    setContents(PDStream(source, ByteArrayInputStream(content)))
                }
                source.addPage(page)
                val parent = page.cosObject.getDictionaryObject(COSName.PARENT) as COSDictionary
                parent.setItem(COSName.RESOURCES, parentResources.cosObject)
                PdfExtractPageCopier(output).copy(page)
                parentResources.cosObject.setString(COSName.getPDFName("Test"), "changed")
            }
            val bytes = ByteArrayOutputStream().also(output::save).toByteArray()
            PDDocument.load(bytes).use { loaded ->
                assertEquals("original", loaded.getPage(0).resources.cosObject.getString(COSName.getPDFName("Test")))
                assertArrayEquals(content, loaded.getPage(0).contents.use { it.readBytes() })
            }
        }
    }
}
