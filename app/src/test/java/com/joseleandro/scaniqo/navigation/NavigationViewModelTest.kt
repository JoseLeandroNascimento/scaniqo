package com.joseleandro.scaniqo.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import com.joseleandro.scaniqo.core.navigation.AppRoute
import com.joseleandro.scaniqo.core.navigation.NavigationViewModel
import com.joseleandro.scaniqo.ui.documentEditor.model.DocumentTool
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationViewModelTest {
    private val viewModel = NavigationViewModel(SavedStateHandle())

    @Test
    fun documentsIsTheInitialDestinationAndCannotBeRemoved() {
        assertEquals(listOf(AppRoute.Documents), viewModel.backStack)

        viewModel.goBack()

        assertEquals(listOf(AppRoute.Documents), viewModel.backStack)
    }

    @Test
    fun navigatingToTagsAndGoingBackReturnsToDocuments() {
        viewModel.navigateTo(AppRoute.Tags)

        assertEquals(
            listOf(AppRoute.Documents, AppRoute.Tags),
            viewModel.backStack,
        )

        viewModel.goBack()

        assertEquals(listOf(AppRoute.Documents), viewModel.backStack)
    }

    @Test
    fun selectingTheCurrentDestinationDoesNotDuplicateIt() {
        viewModel.navigateTo(AppRoute.Documents)
        assertEquals(listOf(AppRoute.Documents), viewModel.backStack)

        viewModel.navigateTo(AppRoute.Tags)
        viewModel.navigateTo(AppRoute.Tags)

        assertEquals(
            listOf(AppRoute.Documents, AppRoute.Tags),
            viewModel.backStack,
        )
    }

    @Test
    fun openingADocumentPreservesItsLongIdAndBackReturnsToTheList() {
        val documentId = Int.MAX_VALUE.toLong() + 42L
        viewModel.navigateTo(AppRoute.DocumentViewer(documentId))

        assertEquals(
            listOf(AppRoute.Documents, AppRoute.DocumentViewer(documentId)),
            viewModel.backStack,
        )

        viewModel.goBack()

        assertEquals(listOf(AppRoute.Documents), viewModel.backStack)
    }

    @Test
    fun theSameDocumentIsNotDuplicatedButDifferentDocumentsHaveSeparateEntries() {
        val firstDocument = AppRoute.DocumentViewer(7)
        val secondDocument = AppRoute.DocumentViewer(8)
        viewModel.navigateTo(firstDocument)
        viewModel.navigateTo(firstDocument)

        assertEquals(listOf(AppRoute.Documents, firstDocument), viewModel.backStack)

        viewModel.navigateTo(secondDocument)

        assertEquals(
            listOf(AppRoute.Documents, firstDocument, secondDocument),
            viewModel.backStack,
        )

        viewModel.goBack()

        assertEquals(listOf(AppRoute.Documents, firstDocument), viewModel.backStack)
    }

    @Test
    fun signingFromTheLibraryReturnsToTheSameEditorBeforeTheLibrary() {
        val id = Long.MAX_VALUE - 2
        viewModel.navigateTo(AppRoute.DocumentViewer(id))
        viewModel.navigateTo(AppRoute.DocumentSignature(id))
        viewModel.navigateTo(AppRoute.DocumentSignature(id))

        assertEquals(
            listOf(AppRoute.Documents, AppRoute.DocumentViewer(id), AppRoute.DocumentSignature(id)),
            viewModel.backStack,
        )
        val serializer = NavBackStackSerializer(AppRoute.serializer())
        val restored = Json.decodeFromString(serializer, Json.encodeToString(
            serializer, NavBackStack<AppRoute>(*viewModel.backStack.toTypedArray()),
        ))
        assertEquals(viewModel.backStack, restored.toList())
        viewModel.goBack()
        assertEquals(AppRoute.DocumentViewer(id), viewModel.backStack.last())
        viewModel.goBack()
        assertEquals(listOf(AppRoute.Documents), viewModel.backStack)
    }

    @Test
    fun serializingAndRestoringTheNavigationStackPreservesRoutesAndDocumentIds() {
        val documentId = Long.MAX_VALUE - 1
        viewModel.navigateTo(AppRoute.Tags)
        viewModel.navigateTo(AppRoute.DriveBackup)
        viewModel.navigateTo(AppRoute.DocumentViewer(7))
        viewModel.navigateTo(AppRoute.DocumentViewer(documentId))
        viewModel.navigateTo(AppRoute.DocumentMerge(documentId))
        viewModel.navigateTo(AppRoute.DocumentMerge())

        // Exercita o mesmo serializer usado pela navegação, sem simular o ciclo de vida Android.
        val serializer = NavBackStackSerializer(AppRoute.serializer())
        val stack = NavBackStack<AppRoute>(*viewModel.backStack.toTypedArray())
        val serialized = Json.encodeToString(serializer, stack)
        val restored = Json.decodeFromString(serializer, serialized)

        assertEquals(viewModel.backStack, restored.toList())
        assertEquals(AppRoute.DocumentMerge(), restored.last())
        assertEquals(documentId, (restored[restored.lastIndex - 1] as AppRoute.DocumentMerge).documentId)
        viewModel.goBack()
        assertEquals(AppRoute.DocumentMerge(documentId), viewModel.backStack.last())
        viewModel.goBack()
        assertEquals(AppRoute.DocumentViewer(documentId), viewModel.backStack.last())
    }

    @Test
    fun editorToolScreensRestoreTheirTypeAndReturnToTheSameDocument() {
        val id = Long.MAX_VALUE - 3
        viewModel.navigateTo(AppRoute.DocumentViewer(id))
        val serializer = NavBackStackSerializer(AppRoute.serializer())
        for (tool in listOf(DocumentTool.LAYOUT, DocumentTool.CORRECTIONS, DocumentTool.PAGES)) {
            val route = AppRoute.DocumentEditorTool(id, tool)
            viewModel.navigateTo(route)
            viewModel.navigateTo(route)
            val restored = Json.decodeFromString(serializer, Json.encodeToString(
                serializer, NavBackStack<AppRoute>(*viewModel.backStack.toTypedArray()),
            ))
            assertEquals(listOf(AppRoute.Documents, AppRoute.DocumentViewer(id), route), restored.toList())
            viewModel.goBack()
            assertEquals(AppRoute.DocumentViewer(id), viewModel.backStack.last())
        }
    }

    @Test
    fun navigatingToAnEarlierDestinationPushesItOntoTheStack() {
        viewModel.navigateTo(AppRoute.Tags)
        viewModel.navigateTo(AppRoute.Documents)

        assertEquals(
            listOf(AppRoute.Documents, AppRoute.Tags, AppRoute.Documents),
            viewModel.backStack,
        )

        viewModel.goBack()

        assertEquals(
            listOf(AppRoute.Documents, AppRoute.Tags),
            viewModel.backStack,
        )

        viewModel.goBack()

        assertEquals(listOf(AppRoute.Documents), viewModel.backStack)
    }
}
