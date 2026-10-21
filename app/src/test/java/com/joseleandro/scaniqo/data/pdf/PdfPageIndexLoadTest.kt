package com.joseleandro.scaniqo.data.pdf

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfPageIndexLoadTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun tenThousandPersistedPagesSupportReorderingWithOnePageTreeTraversal() {
        val fixture = temporary.newFile("ten_thousand_pages.pdf")
        val marker = COSName.getPDFName("ScaniqoLoadPage")
        PDDocument(memory()).use { output ->
            repeat(PAGE_COUNT) { number ->
                output.addPage(PDPage().apply { cosObject.setInt(marker, number) })
            }
            output.save(fixture)
        }
        PDDocument.load(fixture, memory()).use { document ->
            assertEquals(PAGE_COUNT, document.numberOfPages)
            val root = document.pages.cosObject
            val kids = CountingKids(requireNotNull(root.getCOSArray(COSName.KIDS)))
            root.setItem(COSName.KIDS, kids)
            var checksum = 0L
            val elapsed = measureNanoTime {
                val pages = PdfPageIndex(document)
                assertEquals(PAGE_COUNT, pages.size)
                for (number in PAGE_COUNT - 1 downTo 0) {
                    val page = pages[number]
                    assertEquals(number, page.cosObject.getInt(marker))
                    checksum += page.cosObject.getInt(marker)
                }
                // Repeated access must preserve the original page and avoid a new tree walk.
                repeat(100) { assertEquals(PAGE_COUNT / 2, pages[PAGE_COUNT / 2].cosObject.getInt(marker)) }
                val extracted = temporary.newFile("reordered_ten_thousand_pages.pdf")
                PDDocument(memory()).use { output ->
                    val copier = PdfExtractPageCopier(output)
                    for (number in PAGE_COUNT - 1 downTo 0) copier.copy(pages[number])
                    output.save(extracted)
                }
                PDDocument.load(extracted, memory()).use { output ->
                    assertEquals(PAGE_COUNT, output.numberOfPages)
                    output.pages.forEachIndexed { index, page ->
                        assertEquals(PAGE_COUNT - 1 - index, page.cosObject.getInt(marker))
                    }
                }
            }
            println("PDF_PAGE_INDEX pages=$PAGE_COUNT bytes=${fixture.length()} kidsReads=${kids.reads} reorderedAndReopened=true elapsedMs=${elapsed / 1_000_000}")
            assertEquals(PAGE_COUNT.toLong() * (PAGE_COUNT - 1) / 2, checksum)
            assertTrue(
                "Indexed traversal must be linear: ${kids.reads} Kids reads for $PAGE_COUNT pages",
                kids.reads <= PAGE_COUNT * 2L,
            )
        }
    }

    private fun memory(): MemoryUsageSetting =
        MemoryUsageSetting.setupMixed(8L * 1024 * 1024).setTempDir(temporary.root)

    private class CountingKids(source: COSArray) : COSArray() {
        var reads = 0L
            private set

        init {
            for (index in 0 until source.size()) add(source.get(index))
        }

        override fun getObject(index: Int): COSBase? {
            reads++
            check(reads <= PAGE_COUNT * 2L) {
                "PDF_PAGE_INDEX pages=$PAGE_COUNT kidsReads=$reads exceeded the linear read budget; " +
                    "stopped before completing quadratic traversal"
            }
            return super.getObject(index)
        }
    }

    private companion object {
        const val PAGE_COUNT = 10_000
    }
}
