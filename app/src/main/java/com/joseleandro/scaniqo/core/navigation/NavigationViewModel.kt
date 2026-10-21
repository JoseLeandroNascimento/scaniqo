package com.joseleandro.scaniqo.core.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.serialization.saved
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer

class NavigationViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val navigationStack by savedStateHandle.saved(
        // Older saved stacks can contain the removed interactive forms destination.
        key = "navigation_back_stack_v3",
        serializer = NavBackStackSerializer(AppRoute.serializer()),
    ) {
        NavBackStack<AppRoute>(AppRoute.Documents)
    }

    val backStack: List<AppRoute>
        get() = navigationStack.toList()

    fun navigateTo(route: AppRoute) {
        if (navigationStack.last() != route) {
            navigationStack.add(route)
        }
    }

    fun goBack() {
        if (navigationStack.size > 1) {
            navigationStack.removeAt(navigationStack.lastIndex)
        }
    }
}
