package com.joseleandro.scaniqo.data.pdf

import com.joseleandro.scaniqo.domain.model.PdfCropBounds
import com.joseleandro.scaniqo.domain.model.PdfPageLayout
import com.tom_roush.pdfbox.contentstream.PDContentStream
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

/**
 * Localiza a imagem realmente desenhada, sem procurar branco nos pixels nem adivinhar uma caixa de texto.
 * Aceita uma imagem ortogonal e clips retangulares, inclusive com decoração/OCR.
 * Preservar texto/vetores é uma decisão independente; múltiplas imagens continuam ambíguas.
 */
internal object PdfCaptureInspector {
    suspend fun inspect(page: PDPage): PdfCropBounds? {
        currentCoroutineContext().ensureActive()
        if (page.rotation % 90 != 0) return null
        val pageBox = page.cropBox
        val box = PdfContentBox(pageBox.lowerLeftX, pageBox.lowerLeftY, pageBox.upperRightX, pageBox.upperRightY)
        if (!box.valid()) return null
        val images = mutableListOf<PdfContentBox>()
        val forms = mutableSetOf<COSStream>()
        val valid = visit(page, page.resources, State(PdfContentTransform(), box), images, forms, 0)
        if (!valid || images.size != 1) return null
        val image = images.single()
        val rotated = abs(page.rotation % 180) == 90
        val width = if (rotated) box.height else box.width
        val height = if (rotated) box.width else box.height
        val transform = PdfPageGeometry.placement(
            box.left, box.bottom, box.width, box.height, page.rotation,
            PdfPageLayout(), width, height, 0f,
        )
        val xs = listOf(
            transform.x(image.left, image.bottom), transform.x(image.right, image.top),
            transform.x(image.left, image.top), transform.x(image.right, image.bottom),
        )
        val ys = listOf(
            transform.y(image.left, image.bottom), transform.y(image.right, image.top),
            transform.y(image.left, image.top), transform.y(image.right, image.bottom),
        )
        return PdfCropBounds(
            (xs.min() / width).coerceIn(0f, 1f),
            (1f - ys.max() / height).coerceIn(0f, 1f),
            (xs.max() / width).coerceIn(0f, 1f),
            (1f - ys.min() / height).coerceIn(0f, 1f),
        ).takeIf { it.isValid() }
    }

    private suspend fun visit(
        content: PDContentStream,
        resources: PDResources?,
        initial: State,
        images: MutableList<PdfContentBox>,
        forms: MutableSet<COSStream>,
        depth: Int,
    ): Boolean {
        if (depth > 16) return false
        val parser = PDFStreamParser(content)
        parser.parse()
        var state = initial
        val stack = mutableListOf<State>()
        val operands = mutableListOf<COSBase>()
        val path = PdfRectangularPath()
        var pendingClip = false
        fun finishPath(): Boolean {
            if (pendingClip) {
                state = state.copy(clip = state.clip.intersect(path.bounds() ?: return false) ?: return false)
            }
            path.clear()
            pendingClip = false
            return true
        }
        fun addImage(): Boolean {
            val image = state.transform.box(0f, 0f, 1f, 1f) ?: return false
            val visible = image.intersect(state.clip) ?: return true
            images += visible
            return images.size == 1
        }
        for ((index, token) in parser.tokens.withIndex()) {
            if (index % 128 == 0) currentCoroutineContext().ensureActive()
            if (token !is Operator) {
                if (token is COSBase) operands += token
                continue
            }
            fun numbers(count: Int): List<Float>? =
                operands.takeIf { it.size == count }?.map { (it as? COSNumber)?.floatValue() ?: return null }
                    ?.takeIf { it.all(Float::isFinite) }
            when (token.name) {
                "q" -> stack += state
                "Q" -> state = stack.removeLastOrNull() ?: return false
                "cm" -> {
                    val values = numbers(6) ?: return false
                    state = state.copy(transform = state.transform.then(PdfContentTransform(
                        values[0], values[1], values[2], values[3], values[4], values[5],
                    )))
                }
                "re" -> {
                    val values = numbers(4) ?: return false
                    path.rectangle(state.transform.box(values[0], values[1], values[2], values[3]))
                }
                "m", "l" -> {
                    val values = numbers(2) ?: return false
                    val point = state.transform.point(values[0], values[1])
                    if (token.name == "m") path.move(point) else path.line(point)
                }
                "c", "v", "y" -> path.curve()
                "W", "W*" -> pendingClip = true
                "n" -> if (!finishPath()) return false
                "f", "F", "f*", "B", "B*", "b", "b*" -> {
                    // Uma cobertura completa posterior torna a imagem invisível; molduras/rodapés não.
                    val fill = path.bounds()?.intersect(state.clip)
                    if (fill != null && images.any { fill.covers(it) }) return false
                    if (!finishPath()) return false
                }
                "S", "s" -> if (!finishPath()) return false
                "BI" -> {
                    val parameters = token.imageParameters ?: return false
                    if (parameters.getBoolean(COSName.IM, false) ||
                        parameters.getBoolean(COSName.IMAGE_MASK, false) ||
                        parameters.getInt(COSName.W, COSName.WIDTH, -1) <= 0 ||
                        parameters.getInt(COSName.H, COSName.HEIGHT, -1) <= 0 ||
                        token.imageData?.isNotEmpty() != true || !addImage()
                    ) return false
                }
                "Do" -> {
                    if (pendingClip) return false
                    val name = operands.singleOrNull() as? COSName ?: return false
                    when (val item = resources?.getXObject(name)) {
                        is PDImageXObject -> {
                            if (item.isStencil || item.width <= 0 || item.height <= 0) return false
                            if (!addImage()) return false
                        }
                        is PDFormXObject -> {
                            if (!forms.add(item.cosObject)) return false
                            val matrix = item.matrix
                            val transform = state.transform.then(PdfContentTransform(
                                matrix.getValue(0, 0), matrix.getValue(0, 1),
                                matrix.getValue(1, 0), matrix.getValue(1, 1),
                                matrix.getValue(2, 0), matrix.getValue(2, 1),
                            ))
                            val bounds = item.bBox ?: return false
                            val formBox = transform.box(bounds.lowerLeftX, bounds.lowerLeftY, bounds.width, bounds.height)
                                ?: return false
                            val clip = state.clip.intersect(formBox) ?: return false
                            if (!visit(item, item.resources ?: resources, State(transform, clip), images, forms, depth + 1)) {
                                return false
                            }
                            forms.remove(item.cosObject)
                        }
                        else -> return false
                    }
                }
            }
            operands.clear()
        }
        return stack.isEmpty() && !pendingClip
    }

    private data class State(val transform: PdfContentTransform, val clip: PdfContentBox)
}
