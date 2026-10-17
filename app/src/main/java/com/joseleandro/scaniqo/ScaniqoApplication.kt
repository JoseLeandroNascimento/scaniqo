package com.joseleandro.scaniqo

import android.app.Application
import android.util.Log
import com.joseleandro.scaniqo.domain.repository.PdfEditorRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.joseleandro.scaniqo.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ScaniqoApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        val dependencyGraph = startKoin {
            androidContext(this@ScaniqoApplication)
            modules(appModule)
        }
        applicationScope.launch {
            try {
                // A mesma barreira usada pelo editor impede abertura durante esta limpeza.
                dependencyGraph.koin.get<PdfEditorRepository>().initialize()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // A abertura fará nova tentativa e exibirá o erro sem interromper o aplicativo.
                Log.w("Scaniqo", "Não foi possível limpar as sessões antigas do editor.", failure)
            }
        }
    }
}
