package com.joseleandro.scaniqo.core.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.serialization.saved
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.serialization.NavBackStackSerializer

class NavigationViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    init {
        savedStateHandle.remove<Any>("navigation_back_stack_v3")
    }

    private val navigationStack by savedStateHandle.saved(
        key = "navigation_back_stack_v4",
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

    fun navigateToTopLevel(route: AppRoute) {
        if (navigationStack.last() == route) return
        while (navigationStack.size > 1) {
            navigationStack.removeAt(navigationStack.lastIndex)
        }
        if (route != AppRoute.Documents) navigationStack.add(route)
    }

    fun goBack() {
        if (navigationStack.size > 1) {
            navigationStack.removeAt(navigationStack.lastIndex)
        }
    }
}
