package com.joseleandro.scaniqo.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import com.joseleandro.scaniqo.domain.repository.AppSettingsRepository
import com.joseleandro.scaniqo.domain.repository.DocumentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.context.GlobalContext

internal object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updateMutex = Mutex()
    private var observation: Job? = null
    private val configurationState = MutableStateFlow(WidgetConfiguration())
    val configuration = configurationState.asStateFlow()

    @OptIn(FlowPreview::class)
    @Synchronized
    fun start(context: Context) {
        if (observation?.isActive == true) return
        val appContext = context.applicationContext
        observation = scope.launch {
            if (!hasWidgets(appContext)) return@launch
            val koin = GlobalContext.get()
            val settings = koin.get<AppSettingsRepository>().settings
            val documents = recentWidgetDocumentUpdates(
                koin.get<DocumentRepository>().observeDocuments(),
            )
            val appearance = settings
                .map { it.themeMode to it.colorTheme }
                .distinctUntilChanged()
            combine(documents, appearance) { recent, colors -> recent to colors }
                .debounce(250)
                .collect { updateWidgets(appContext) }
        }
    }

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        val languageTags = ContextCompat.getContextForLanguage(appContext)
            .resources.configuration.locales.toLanguageTags()
        configurationState.update {
            WidgetConfiguration(languageTags = languageTags, revision = it.revision + 1)
        }
        requestRefresh(appContext)
    }

    fun localesChanged(context: Context, languageTags: String) {
        if (configurationState.value.languageTags == languageTags) return
        configurationState.update {
            WidgetConfiguration(languageTags = languageTags, revision = it.revision + 1)
        }
        requestRefresh(context.applicationContext)
    }

    private fun requestRefresh(appContext: Context) {
        start(appContext)
        scope.launch { updateWidgets(appContext) }
    }

    @Synchronized
    fun stopIfUnused(context: Context) {
        if (!hasWidgets(context)) {
            observation?.cancel()
            observation = null
        }
    }

    private fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return manager.getAppWidgetIds(ComponentName(context, QuickActionsWidgetReceiver::class.java)).isNotEmpty() ||
            manager.getAppWidgetIds(ComponentName(context, RecentDocumentsWidgetReceiver::class.java)).isNotEmpty()
    }

    private suspend fun updateWidgets(context: Context) = updateMutex.withLock {
        val manager = AppWidgetManager.getInstance(context)
        val widgets = listOf(
            QuickActionsWidgetReceiver::class.java to QuickActionsWidget(),
            RecentDocumentsWidgetReceiver::class.java to RecentDocumentsWidget(),
        )
        widgets.forEach { (receiver, widget) ->
            try {
                if (manager.getAppWidgetIds(ComponentName(context, receiver)).isNotEmpty()) {
                    widget.updateAll(context)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Log.w("ScaniqoWidgets", "Não foi possível atualizar ${receiver.simpleName}.")
            }
        }
    }
}
