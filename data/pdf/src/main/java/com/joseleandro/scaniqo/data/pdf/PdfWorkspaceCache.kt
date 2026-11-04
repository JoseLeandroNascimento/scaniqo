package com.joseleandro.scaniqo.data.pdf

import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** O gate pertence ao processo: outra instância do repository não apaga sessões recém-criadas. */
internal object PdfWorkspaceCache {
    private val mutex = Mutex()
    private val initializedRoots = mutableSetOf<String>()

    suspend fun initialize(root: File) = mutex.withLock {
        val directory = root.canonicalFile
        if (directory.path in initializedRoots) return@withLock
        currentCoroutineContext().ensureActive()
        if (directory.exists()) {
            if (!directory.isDirectory) throw cleanupFailure()
            val children = directory.listFiles() ?: throw cleanupFailure()
            children.forEach { child ->
                currentCoroutineContext().ensureActive()
                // Nunca seguimos links para fora da pasta privada do editor.
                if (child.canonicalFile.parentFile != directory) return@forEach
                when {
                    child.isDirectory && isWorkspaceName(child.name) -> clearWorkspace(child)
                    // PDFBox também mantém arquivos de trabalho diretamente nesta raiz.
                    child.isFile -> deleteChecked(child)
                }
            }
        }
        // Só liberamos novas sessões depois que toda a limpeza anterior foi concluída.
        initializedRoots += directory.path
    }

    private suspend fun clearWorkspace(directory: File) {
        val canonical = directory.canonicalFile
        val children = directory.listFiles() ?: throw cleanupFailure()
        children.forEach { child ->
            currentCoroutineContext().ensureActive()
            if (child.canonicalFile.parentFile == canonical && child.isFile) deleteChecked(child)
        }
        deleteChecked(directory)
    }

    private fun deleteChecked(file: File) {
        if (file.exists() && !file.delete() && file.exists()) throw cleanupFailure()
    }

    private fun isWorkspaceName(name: String): Boolean =
        runCatching { UUID.fromString(name).toString() == name }.getOrDefault(false)

    private fun cleanupFailure() = IOException(
        "Não foi possível limpar uma sessão anterior. Tente abrir o documento novamente."
    )
}
