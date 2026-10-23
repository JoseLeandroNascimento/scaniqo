package com.joseleandro.scaniqo.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.net.Uri
import android.util.AtomicFile
import com.joseleandro.scaniqo.core.graphics.ShapedTextLayout
import com.joseleandro.scaniqo.domain.model.SignatureFont
import com.joseleandro.scaniqo.domain.model.SignatureModel
import com.joseleandro.scaniqo.domain.model.SignatureSource
import com.joseleandro.scaniqo.domain.model.SignatureSourceType
import com.joseleandro.scaniqo.domain.model.SignatureStroke
import com.joseleandro.scaniqo.domain.repository.SignatureRepository
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Guarda PNGs transparentes e um índice atômico no armazenamento privado. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AndroidSignatureRepository(context: Context) : SignatureRepository {
    private val context = context.applicationContext
    private val root = File(this.context.filesDir, "signatures")
    private val index get() = AtomicFile(File(root, "index.json"))
    private val mutex = Mutex()
    private val revision = MutableStateFlow(0L)
    private val json = Json { ignoreUnknownKeys = true }
    private var pruneOldVersionsOnFirstRead = true

    override fun observeSignatures(): Flow<List<SignatureModel>> = revision.mapLatest {
        withContext(Dispatchers.IO) { mutex.withLock { readIndex() } }
    }

    override suspend fun createDrawing(name: String, strokes: List<SignatureStroke>): SignatureModel =
        withContext(Dispatchers.IO) {
            val bitmap = renderDrawing(strokes)
            try {
                persist(name, bitmap, SignatureSource(SignatureSourceType.DRAW, strokes = strokes))
            } finally { bitmap.recycle() }
        }

    override suspend fun updateDrawing(
        id: String,
        name: String,
        strokes: List<SignatureStroke>,
    ): SignatureModel = withContext(Dispatchers.IO) {
        val bitmap = renderDrawing(strokes)
        try {
            persist(name, bitmap, SignatureSource(SignatureSourceType.DRAW, strokes = strokes), id)
        } finally { bitmap.recycle() }
    }

    override suspend fun createTyped(
        name: String,
        text: String,
        font: SignatureFont,
        colorArgb: Int,
    ): SignatureModel = withContext(Dispatchers.IO) {
        val signature = text.trim()
        val bitmap = renderTyped(signature, font, colorArgb)
        try {
            persist(
                name, bitmap,
                SignatureSource(SignatureSourceType.TYPE, text = signature, font = font, colorArgb = colorArgb),
            )
        } finally { bitmap.recycle() }
    }

    override suspend fun updateTyped(
        id: String,
        name: String,
        text: String,
        font: SignatureFont,
        colorArgb: Int,
    ): SignatureModel = withContext(Dispatchers.IO) {
        val signature = text.trim()
        val bitmap = renderTyped(signature, font, colorArgb)
        try {
            persist(
                name, bitmap,
                SignatureSource(SignatureSourceType.TYPE, text = signature, font = font, colorArgb = colorArgb),
                id,
            )
        } finally { bitmap.recycle() }
    }

    override suspend fun importImage(
        name: String,
        uri: String,
        removeLightBackground: Boolean,
    ): SignatureModel = withContext(Dispatchers.IO) {
        val bitmap = loadImportedBitmap(uri, removeLightBackground)
        try {
            persist(name, bitmap, SignatureSource(SignatureSourceType.IMAGE))
        } finally { bitmap.recycle() }
    }

    override suspend fun updateImage(
        id: String,
        name: String,
        uri: String,
        removeLightBackground: Boolean,
    ): SignatureModel = withContext(Dispatchers.IO) {
        val saved = mutex.withLock {
            readIndex().firstOrNull { it.id == id } ?: error("Assinatura não encontrada.")
        }
        val ownedSource = if (uri == saved.imageUri) ownedImage(saved.id, saved.imageVersion) else null
        val bitmap = loadImportedBitmap(uri, removeLightBackground, ownedSource)
        try {
            persist(name, bitmap, SignatureSource(SignatureSourceType.IMAGE), id)
        } finally { bitmap.recycle() }
    }

    private suspend fun renderDrawing(strokes: List<SignatureStroke>): Bitmap {
        require(strokes.any { it.points.size >= 2 }) { "Desenhe a assinatura antes de salvar." }
        require(strokes.size <= 500 && strokes.sumOf { it.points.size } <= 100_000) {
            "O desenho está muito grande. Simplifique a assinatura."
        }
        val bitmap = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            strokes.forEach { stroke ->
                currentCoroutineContext().ensureActive()
                paint.color = stroke.colorArgb
                paint.strokeWidth = stroke.width.coerceIn(0.001f, 0.05f) * bitmap.width
                val path = Path()
                stroke.points.forEachIndexed { i, point ->
                    val x = point.x.coerceIn(0f, 1f) * bitmap.width
                    val y = point.y.coerceIn(0f, 1f) * bitmap.height
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, paint)
            }
            return bitmap
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    private fun renderTyped(text: String, font: SignatureFont, colorArgb: Int): Bitmap {
        require(text.isNotBlank() && text.length <= 80) { "Digite um nome com até 80 caracteres." }
        val fontStyle = when {
            font.bold && font.italic -> Typeface.BOLD_ITALIC
            font.bold -> Typeface.BOLD
            font.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val layout = ShapedTextLayout.create(
            text = text, fontSize = 160f, color = colorArgb,
            maxWidth = 1520f, maxHeight = 540f,
            typeface = Typeface.create(font.family, fontStyle),
        )
        val width = (ceil(layout.width).toInt() + 80).coerceIn(80, 1600)
        val height = (ceil(layout.height).toInt() + 60).coerceIn(80, 600)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            layout.draw(Canvas(it), 40f, 30f)
        }
    }

    private suspend fun loadImportedBitmap(
        uri: String,
        removeLightBackground: Boolean,
        ownedSource: File? = null,
    ): Bitmap {
        val stream = ownedSource?.inputStream() ?: context.contentResolver.openInputStream(Uri.parse(uri))
        val bytes = stream?.use { it.readBytesLimited(10 * 1024 * 1024) }
            ?: error("Não foi possível abrir a imagem.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Escolha uma imagem válida." }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
        try {
            if (removeLightBackground) {
                val row = IntArray(bitmap.width)
                for (y in 0 until bitmap.height) {
                    currentCoroutineContext().ensureActive()
                    bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                    for (x in row.indices) {
                        val pixel = row[x]
                        val lightestInk = minOf((pixel shr 16) and 255, (pixel shr 8) and 255, pixel and 255)
                        // Suaviza a transição para não deixar uma borda branca no traço.
                        val opacity = ((250 - lightestInk) / 40f).coerceIn(0f, 1f)
                        val alpha = (((pixel ushr 24) and 255) * opacity).roundToInt()
                        row[x] = (pixel and 0x00FFFFFF) or (alpha shl 24)
                    }
                    bitmap.setPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                }
            }
            return bitmap
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    override suspend fun rename(id: String, name: String) = withContext(Dispatchers.IO) {
        val normalized = validateName(name)
        mutex.withLock {
            val signatures = readIndex()
            require(signatures.any { it.id == id }) { "Assinatura não encontrada." }
            writeIndex(signatures.map { if (it.id == id) it.copy(name = normalized) else it })
        }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val signatures = readIndex()
            val signature = signatures.firstOrNull { it.id == id } ?: return@withLock
            // PDFs já salvos possuem sua própria cópia; excluir a biblioteca não os altera.
            writeIndex(signatures.filterNot { it.id == id })
            deleteOwnedImages(signature.id)
        }
    }

    private suspend fun persist(
        name: String,
        bitmap: Bitmap,
        source: SignatureSource,
        editingId: String? = null,
    ): SignatureModel {
        val normalized = validateName(name)
        val cropped = trimTransparent(bitmap)
        try {
            return mutex.withLock {
                check(root.isDirectory || root.mkdirs()) { "Não foi possível preparar a biblioteca." }
                val signatures = readIndex()
                val previous = editingId?.let { id ->
                    signatures.firstOrNull { it.id == id } ?: error("Assinatura não encontrada.")
                }
                val id = previous?.id ?: UUID.randomUUID().toString()
                val version = previous?.let {
                    check(it.imageVersion < Int.MAX_VALUE) { "Limite de edições da assinatura atingido." }
                    it.imageVersion + 1
                } ?: 0
                val target = ownedImage(id, version)
                try {
                    target.outputStream().use { check(cropped.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    val model = SignatureModel(
                        id = id,
                        name = normalized,
                        imageUri = Uri.fromFile(target).toString(),
                        aspectRatio = cropped.width.toFloat() / cropped.height,
                        imageVersion = version,
                        source = source,
                    )
                    writeIndex(
                        if (previous == null) signatures + model
                        else signatures.map { if (it.id == id) model else it },
                    )
                    // Versões antigas podem estar em uso enquanto um documento copia a assinatura.
                    model
                } catch (error: Exception) {
                    target.delete()
                    throw error
                }
            }
        } finally { if (cropped !== bitmap) cropped.recycle() }
    }

    private fun trimTransparent(bitmap: Bitmap): Bitmap {
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            row.forEachIndexed { x, pixel ->
                if ((pixel ushr 24) > 12) {
                    left = minOf(left, x); top = minOf(top, y)
                    right = maxOf(right, x); bottom = maxOf(bottom, y)
                }
            }
        }
        require(right >= left && bottom >= top) { "A assinatura está vazia." }
        left = (left - 8).coerceAtLeast(0); top = (top - 8).coerceAtLeast(0)
        right = (right + 8).coerceAtMost(bitmap.width - 1)
        bottom = (bottom + 8).coerceAtMost(bitmap.height - 1)
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }

    private fun readIndex(): List<SignatureModel> {
        val input = try {
            // openRead restaura o backup de uma gravação interrompida antes de abrir o índice.
            index.openRead()
        } catch (missing: FileNotFoundException) {
            if (index.baseFile.exists() || File(root, "index.json.bak").exists()) throw missing
            return emptyList()
        }
        val signatures = json.decodeFromString<List<SignatureModel>>(
            input.bufferedReader().use { it.readText() },
        ).map { model ->
            // A URI é reconstruída; o índice nunca pode apontar para fora da biblioteca.
            model.copy(imageUri = Uri.fromFile(ownedImage(model.id, model.imageVersion)).toString())
        }
        if (pruneOldVersionsOnFirstRead) {
            // Após reiniciar, nenhuma cópia em andamento usa mais as versões anteriores.
            val currentNames = signatures.map { ownedImage(it.id, it.imageVersion).name }.toSet()
            val ownedPatterns = signatures.map { Regex("${Regex.escape(it.id)}(?:-[0-9]+)?[.]png") }
            root.listFiles()?.forEach { file ->
                if (file.isFile && file.name !in currentNames && ownedPatterns.any { it.matches(file.name) }) {
                    file.delete()
                }
            }
            pruneOldVersionsOnFirstRead = false
        }
        return signatures
    }

    private fun writeIndex(signatures: List<SignatureModel>) {
        val stream = index.startWrite()
        try {
            stream.write(json.encodeToString(signatures).toByteArray(Charsets.UTF_8))
            index.finishWrite(stream)
            pruneOldVersionsOnFirstRead = false
            revision.value++
        } catch (failure: Exception) {
            index.failWrite(stream)
            throw failure
        }
    }

    private fun ownedImage(id: String, version: Int): File {
        require(UUID.fromString(id).toString() == id && version >= 0)
        return File(root, if (version == 0) "$id.png" else "$id-$version.png")
    }

    private fun deleteOwnedImages(id: String) {
        require(UUID.fromString(id).toString() == id)
        val pattern = Regex("${Regex.escape(id)}(?:-[0-9]+)?[.]png")
        root.listFiles()?.forEach { file ->
            if (file.isFile && pattern.matches(file.name)) file.delete()
        }
    }

    private fun validateName(name: String): String = name.trim().also {
        require(it.isNotEmpty() && it.length <= 80) { "Informe um nome com até 80 caracteres." }
    }
}

private suspend fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit) { "Use uma imagem de até 10 MB." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
