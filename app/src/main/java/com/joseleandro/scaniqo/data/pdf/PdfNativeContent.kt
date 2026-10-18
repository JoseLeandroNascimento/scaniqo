package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.tom_roush.pdfbox.contentstream.PDContentStream
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.max

/** Detecta conteúdo que perderia editabilidade caso a página inteira virasse uma imagem. */
internal data class PdfNativeContent(
    val hasText: Boolean,
    val hasVectors: Boolean,
    val largestImageSide: Int,
    val captureBounds: PdfCropBounds? = null,
    val sourceWidth: Float = 0f,
    val sourceHeight: Float = 0f,
) {
    val shouldPreserve: Boolean get() = hasText || hasVectors

    companion object {
        suspend fun inspect(document: PDDocument, pageIndex: Int): PdfNativeContent {
            currentCoroutineContext().ensureActive()
            val page = document.getPage(pageIndex)
            var text = false
            var largestImage = 0
            var vectors = false
            val visited = mutableSetOf<COSStream>()
            suspend fun visit(content: PDContentStream, resources: PDResources?, depth: Int) {
                if (depth > 16) {
                    vectors = true
                    return
                }
                vectors = vectors || containsVectors(content)
                val parser = PDFStreamParser(content)
                parser.parse()
                val operands = mutableListOf<COSBase>()
                for ((index, token) in parser.tokens.withIndex()) {
                    if (index % 128 == 0) currentCoroutineContext().ensureActive()
                    if (token !is Operator) {
                        if (token is COSBase) operands += token
                        continue
                    }
                    if (token.name in listOf("Tj", "TJ", "'", "\"")) {
                        // Inclui OCR invisível: preservação não depende de decodificar fontes.
                        text = text || operands.any { value ->
                            when (value) {
                                is COSString -> value.bytes.isNotEmpty()
                                is COSArray -> (0 until value.size()).any {
                                    (value.getObject(it) as? COSString)?.bytes?.isNotEmpty() == true
                                }
                                else -> false
                            }
                        }
                    } else if (token.name == "Do") {
                        val name = operands.singleOrNull() as? COSName
                        when (val value = name?.let { resources?.getXObject(it) }) {
                            is PDImageXObject -> largestImage = max(largestImage, max(value.width, value.height))
                            is PDFormXObject -> if (visited.add(value.cosObject)) {
                                visit(value, value.resources ?: resources, depth + 1)
                            }
                        }
                    } else if (token.name == "BI") {
                        token.imageParameters?.let {
                            largestImage = max(largestImage, max(
                                it.getInt(COSName.W, COSName.WIDTH, 0),
                                it.getInt(COSName.H, COSName.HEIGHT, 0),
                            ))
                        }
                    }
                    operands.clear()
                }
            }
            // Recursos registrados mas nunca desenhados não alteram a natureza da página.
            visit(page, page.resources, 0)
            val rotated = abs(page.rotation % 180) == 90
            val box = page.cropBox
            return PdfNativeContent(
                text, vectors, largestImage,
                captureBounds = PdfCaptureInspector.inspect(page),
                sourceWidth = if (rotated) box.height else box.width,
                sourceHeight = if (rotated) box.width else box.height,
            )
        }

        internal fun containsVectors(content: PDContentStream): Boolean {
            val parser = PDFStreamParser(content)
            parser.parse()
            val path = PdfRectangularPath()
            var state = FillState()
            val stack = mutableListOf<FillState>()
            val operands = mutableListOf<COSBase>()
            var paintedContent = false
            val pageBox = content.bBox?.let {
                PdfContentBox(it.lowerLeftX, it.lowerLeftY, it.upperRightX, it.upperRightY)
            }
            for (token in parser.tokens) {
                if (token !is Operator) {
                    if (token is COSBase) operands += token
                    continue
                }
                fun numbers(count: Int): List<Float>? =
                    operands.takeIf { it.size == count }?.map { (it as? COSNumber)?.floatValue() ?: return null }
                        ?.takeIf { it.all(Float::isFinite) }
                fun white(space: String, values: List<Float>?) = when (space) {
                    "DeviceGray" -> values?.size == 1 && values.all { it >= .99f }
                    "DeviceRGB" -> values?.size == 3 && values.all { it >= .99f }
                    "DeviceCMYK" -> values?.size == 4 && values.all { it <= .01f }
                    else -> false
                }
                when (token.name) {
                    "q" -> stack += state
                    "Q" -> state = stack.removeLastOrNull() ?: return true
                    "cm" -> {
                        val v = numbers(6) ?: return true
                        state = state.copy(transform = state.transform.then(
                            PdfContentTransform(v[0], v[1], v[2], v[3], v[4], v[5])))
                    }
                    "g", "rg", "k" -> {
                        val space = when (token.name) {
                            "g" -> "DeviceGray"; "rg" -> "DeviceRGB"; else -> "DeviceCMYK"
                        }
                        state = state.copy(colorSpace = space, white = white(space, numbers(operands.size)))
                    }
                    "cs" -> {
                        val name = operands.singleOrNull() as? COSName
                        val space = when (name?.name) {
                            "DeviceGray", "DeviceRGB", "DeviceCMYK" -> name.name
                            else -> name?.let { content.resources?.getColorSpace(it)?.name }.orEmpty()
                        }
                        state = state.copy(colorSpace = space, white = false)
                    }
                    "sc", "scn" -> state = state.copy(white = white(state.colorSpace, numbers(operands.size)))
                    "re" -> {
                        val v = numbers(4) ?: return true
                        path.rectangle(state.transform.box(v[0], v[1], v[2], v[3]))
                    }
                    "m", "l" -> {
                        val v = numbers(2) ?: return true
                        val point = state.transform.point(v[0], v[1])
                        if (token.name == "m") path.move(point) else path.line(point)
                    }
                    "c", "v", "y" -> path.curve()
                    "S", "s", "B", "B*", "b", "b*", "sh" -> return true
                    "f", "F", "f*" -> {
                        val bounds = path.bounds()
                        val covered = if (bounds != null && pageBox != null) bounds.intersect(pageBox) else null
                        // Apenas o fundo branco anterior ao conteúdo é descartável.
                        if (paintedContent || !state.white || covered == null || pageBox == null ||
                            covered.width * covered.height < pageBox.width * pageBox.height * .9f
                        ) return true
                        path.clear()
                    }
                    "n" -> path.clear() // Clips não são desenhos.
                    "Do", "BI", "Tj", "TJ", "'", "\"" -> paintedContent = true
                }
                operands.clear()
            }
            return stack.isNotEmpty()
        }

        private data class FillState(
            val transform: PdfContentTransform = PdfContentTransform(),
            val colorSpace: String = "DeviceGray",
            val white: Boolean = false,
        )
    }
}
