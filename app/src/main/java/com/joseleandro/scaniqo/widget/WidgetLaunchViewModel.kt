package com.joseleandro.scaniqo.widget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class WidgetLaunchRequest(
    val id: String,
    val destination: WidgetDestination,
)

/** Keeps the latest tap while onboarding or another operation prevents opening it. */
internal class WidgetLaunchViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val state = MutableStateFlow(decode(savedStateHandle[PENDING_REQUEST]))
    val pending = state.asStateFlow()

    fun enqueue(destination: WidgetDestination) {
        val request = WidgetLaunchRequest(UUID.randomUUID().toString(), destination)
        savedStateHandle[PENDING_REQUEST] = listOf(
            request.id,
            destination.action.name,
            destination.documentId?.toString().orEmpty(),
        ).joinToString("|")
        state.value = request
    }

    /** Call immediately before dispatch, without suspending, to avoid repeating scanner/picker launches. */
    fun consume(request: WidgetLaunchRequest): WidgetDestination? {
        if (state.value != request) return null
        savedStateHandle.set<String?>(PENDING_REQUEST, null)
        state.value = null
        return request.destination
    }

    private fun decode(encoded: String?): WidgetLaunchRequest? {
        val parts = encoded?.split('|')?.takeIf { it.size == 3 } ?: return null
        if (parts[0].isBlank()) return null
        val action = WidgetAction.entries.firstOrNull { it.name == parts[1] } ?: return null
        val documentId = if (action == WidgetAction.OPEN_DOCUMENT) {
            parts[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
        } else {
            if (parts[2].isNotEmpty()) return null
            null
        }
        return WidgetLaunchRequest(parts[0], WidgetDestination(action, documentId))
    }

    private companion object {
        const val PENDING_REQUEST = "widget_pending_request"
    }
}
