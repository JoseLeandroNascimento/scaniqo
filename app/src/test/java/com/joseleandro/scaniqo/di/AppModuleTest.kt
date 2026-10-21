package com.joseleandro.scaniqo.di

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.joseleandro.scaniqo.core.navigation.AppRoute
import com.joseleandro.scaniqo.core.navigation.NavigationViewModel
import com.joseleandro.scaniqo.data.local.ScaniqoDatabase
import com.joseleandro.scaniqo.ui.documentCommon.DocumentActionsViewModel
import com.joseleandro.scaniqo.ui.documentEditor.DocumentViewerViewModel
import com.joseleandro.scaniqo.ui.documents.DocumentsViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Exercises the shipped definitions, including constructor defaults, without global Koin state. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class AppModuleTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModels = ViewModelStore()
    private var graph: KoinApplication? = null

    @Before fun prepare() {
        Dispatchers.setMain(dispatcher)
        graph = koinApplication {
            androidContext(RuntimeEnvironment.getApplication())
            modules(appModule)
        }
    }

    @After fun cleanUp() {
        try {
            // Cancel queued initialization before closing repositories and their database.
            viewModels.clear()
            dispatcher.scheduler.runCurrent()
            graph?.koin?.getOrNull<ScaniqoDatabase>()?.close()
        } finally {
            graph?.close()
            graph = null
            RuntimeEnvironment.getApplication().deleteDatabase("scaniqo.db")
            Dispatchers.resetMain()
        }
    }

    @Test fun initialLibraryViewModelsResolveWithoutRegisteringAnOptionalDispatcher() {
        val koin = requireNotNull(graph).koin
        assertNull(koin.getOrNull<CoroutineDispatcher>())

        // SavedStateHandle is supplied as a creation parameter, as the Android ViewModel factory does.
        val navigation = resolve<NavigationViewModel>("navigation")
        val documents = resolve<DocumentsViewModel>("documents")
        val actions = resolve<DocumentActionsViewModel>("document-actions")
        val viewer = resolve<DocumentViewerViewModel>("document-viewer")

        assertEquals(AppRoute.Documents, navigation.backStack.single())
        assertNotNull(documents.uiState.value)
        assertNotNull(actions.uiState.value)
        assertNotNull(viewer.uiState.value)
    }

    private inline fun <reified T : ViewModel> resolve(key: String): T =
        requireNotNull(graph).koin.get<T>(parameters = { parametersOf(SavedStateHandle()) })
            .also { viewModels.put(key, it) }
}
