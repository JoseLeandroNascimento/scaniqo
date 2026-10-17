package com.joseleandro.scaniqo.data.pdf

import com.tom_roush.pdfbox.contentstream.PDContentStream
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.max

/** Detecta conteúdo que perderia editabilidade caso a página inteira virasse uma imagem. */
internal data class PdfNativeContent(
    val hasText: Boolean,
    val hasVectors: Boolean,
    val largestImageSide: Int,
) {
    val shouldPreserve: Boolean get() = hasText || hasVectors

    companion object {
        suspend fun inspect(document: PDDocument, pageIndex: Int): PdfNativeContent {
            currentCoroutineContext().ensureActive()
            val page = document.getPage(pageIndex)
            val text = PDFTextStripper().apply {
                startPage = pageIndex + 1
                endPage = pageIndex + 1
            }.getText(document).any { !it.isWhitespace() }
            var largestImage = 0
            var vectors = containsVectors(page)
            val visited = mutableSetOf<COSStream>()
            suspend fun visit(resources: PDResources?, depth: Int) {
                if (resources == null) return
                if (depth > 16) {
                    vectors = true // Conteúdo muito aninhado permanece vetorial por precaução.
                    return
                }
                for (name in resources.xObjectNames) {
                    currentCoroutineContext().ensureActive()
                    when (val value = resources.getXObject(name)) {
                        is PDImageXObject -> largestImage = max(largestImage, max(value.width, value.height))
                        is PDFormXObject -> if (visited.add(value.cosObject)) {
                            vectors = vectors || containsVectors(value)
                            visit(value.resources, depth + 1)
                        }
                    }
                }
            }
            visit(page.resources, 0)
            return PdfNativeContent(text, vectors, largestImage)
        }

        internal fun containsVectors(content: PDContentStream): Boolean {
            val parser = PDFStreamParser(content)
            parser.parse()
            var detailedPath = false
            var whiteFill = false
            val fillStack = mutableListOf<Boolean>()
            val operands = mutableListOf<COSNumber>()
            for (token in parser.tokens) {
                if (token is COSNumber) operands += token
                if (token !is Operator) continue
                when (token.name) {
                    "q" -> fillStack += whiteFill
                    "Q" -> whiteFill = fillStack.removeLastOrNull() ?: false
                    "g" -> whiteFill = operands.singleOrNull()?.floatValue()?.let { it >= .99f } == true
                    "rg" -> whiteFill = operands.size == 3 && operands.all { it.floatValue() >= .99f }
                    "k" -> whiteFill = operands.size == 4 && operands.all { it.floatValue() <= .01f }
                    "sc", "scn" -> whiteFill = false
                    "m", "l", "c", "v", "y" -> detailedPath = true
                    "re" -> {
                        // O retângulo branco da folha não deve impedir filtros em digitalizações.
                        val box = content.bBox
                        val width = operands.getOrNull(operands.size - 2)?.floatValue() ?: 0f
                        val height = operands.lastOrNull()?.floatValue() ?: 0f
                        if (!whiteFill || box == null ||
                            abs(width * height) < box.width * box.height * .9f
                        ) {
                            detailedPath = true
                        }
                    }
                    "S", "s", "B", "B*", "b", "b*", "sh" -> return true
                    "f", "F", "f*" -> {
                        if (detailedPath) return true
                        detailedPath = false
                    }
                    "n" -> detailedPath = false
                }
                operands.clear()
            }
            return false
        }
    }
}
