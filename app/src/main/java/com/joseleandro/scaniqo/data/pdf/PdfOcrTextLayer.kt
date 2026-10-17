package com.joseleandro.scaniqo.data.pdf

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.joseleandro.scaniqo.domain.model.PdfPoint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import java.io.Closeable
import java.io.File
import java.text.Normalizer
import java.util.concurrent.Executor
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** OCR latino inteiramente local. A camada invisível mantém a aparência da página. */
internal class PdfOcrTextLayer(private val document: PDDocument) : Closeable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val font: PDFont by lazy { loadFont() }

    suspend fun append(page: PDPage, bitmap: Bitmap) {
        currentCoroutineContext().ensureActive()
        // O SDK não cancela a leitura do bitmap: esperamos a página atual antes de liberá-lo.
        val recognized = withContext(NonCancellable) {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitCompletion()
        }
        currentCoroutineContext().ensureActive()
        if (recognized.text.isBlank()) return
        PDPageContentStream(
            document, page, PDPageContentStream.AppendMode.APPEND, true, true
        ).use { stream ->
            stream.saveGraphicsState()
            for (block in recognized.textBlocks) {
                for (line in block.lines) {
                    for (word in line.elements) {
                        currentCoroutineContext().ensureActive()
                        drawWord(stream, word, page, bitmap)
                    }
                }
            }
            stream.restoreGraphicsState()
        }
    }

    private fun drawWord(
        stream: PDPageContentStream,
        word: Text.Element,
        page: PDPage,
        bitmap: Bitmap,
    ) {
        val text = encodableText(word.text)
        if (text.isBlank()) return
        val box = word.boundingBox ?: return
        val points = word.cornerPoints
        val left = points?.getOrNull(3)?.x?.toFloat() ?: box.left.toFloat()
        val bottom = points?.getOrNull(3)?.y?.toFloat() ?: box.bottom.toFloat()
        val right = points?.getOrNull(2)?.x?.toFloat() ?: box.right.toFloat()
        val endY = points?.getOrNull(2)?.y?.toFloat() ?: box.bottom.toFloat()
        val topX = points?.getOrNull(0)?.x?.toFloat() ?: box.left.toFloat()
        val topY = points?.getOrNull(0)?.y?.toFloat() ?: box.top.toFloat()
        val placement = PdfOcrGeometry.place(
            PdfPoint(left, bottom), PdfPoint(right, endY), PdfPoint(topX, topY),
            bitmap.width, bitmap.height, page.mediaBox.width, page.mediaBox.height,
            font.getStringWidth(text) / 1000f,
        ) ?: return
        stream.beginText()
        stream.setRenderingMode(RenderingMode.NEITHER)
        stream.setFont(font, placement.fontSize)
        stream.setTextMatrix(Matrix(
            placement.a, placement.b, placement.c, placement.d, placement.x, placement.y
        ))
        stream.showText(text)
        stream.endText()
    }

    private fun encodableText(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
        val result = StringBuilder()
        var index = 0
        while (index < normalized.length) {
            val codePoint = normalized.codePointAt(index)
            index += Character.charCount(codePoint)
            if (Character.isISOControl(codePoint)) continue
            val character = String(Character.toChars(codePoint))
            try {
                font.encode(character)
                result.append(character)
            } catch (_: IllegalArgumentException) {
                // Caracteres fora do alfabeto da fonte não podem corromper o PDF inteiro.
                result.append('?')
            }
        }
        return result.toString()
    }

    private fun loadFont(): PDFont {
        // Roboto/Noto incluem os acentos do português; a fonte é incorporada ao PDF final.
        for (path in listOf(
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/RobotoStatic-Regular.ttf",
            "/system/fonts/NotoSans-Regular.ttf",
        )) {
            val file = File(path)
            if (!file.isFile) continue
            try {
                return PDType0Font.load(document, file)
            } catch (_: java.io.IOException) {
                // Fabricantes podem substituir as fontes do sistema por formatos diferentes.
            }
        }
        // WinAnsi mantém português pesquisável mesmo nos aparelhos sem fonte TTF compatível.
        return PDType1Font.HELVETICA
    }

    override fun close() = recognizer.close()

    private suspend fun <T> Task<T>.awaitCompletion(): T = suspendCoroutine { continuation ->
        addOnCompleteListener(Executor { it.run() }) { task ->
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(
                task.exception ?: java.io.IOException("Não foi possível reconhecer o texto desta página.")
            )
        }
    }
}
