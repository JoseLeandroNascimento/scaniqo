package com.joseleandro.scaniqo.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import com.joseleandro.scaniqo.core.navigation.AppRoute
import com.joseleandro.scaniqo.core.navigation.NavigationViewModel
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
    fun serializingAndRestoringTheNavigationStackPreservesRoutesAndDocumentIds() {
        val documentId = Long.MAX_VALUE - 1
        viewModel.navigateTo(AppRoute.Tags)
        viewModel.navigateTo(AppRoute.DocumentViewer(7))
        viewModel.navigateTo(AppRoute.DocumentViewer(documentId))

        // Exercita o mesmo serializer usado pela navegação, sem simular o ciclo de vida Android.
        val serializer = NavBackStackSerializer(AppRoute.serializer())
        val stack = NavBackStack<AppRoute>(*viewModel.backStack.toTypedArray())
        val serialized = Json.encodeToString(serializer, stack)
        val restored = Json.decodeFromString(serializer, serialized)

        assertEquals(viewModel.backStack, restored.toList())
        assertEquals(documentId, (restored.last() as AppRoute.DocumentViewer).documentId)
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
