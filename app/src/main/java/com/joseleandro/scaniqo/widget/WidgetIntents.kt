package com.joseleandro.scaniqo.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.joseleandro.scaniqo.MainActivity

enum class WidgetAction { SCAN, IMPORT, SEARCH, MERGE, LIBRARY, OPEN_DOCUMENT }

internal data class WidgetDestination(
    val action: WidgetAction,
    val documentId: Long? = null,
) {
    init {
        require(if (action == WidgetAction.OPEN_DOCUMENT) documentId != null && documentId > 0 else documentId == null)
    }
}

/** Explicit, distinct identities also keep different widget PendingIntents from colliding. */
internal fun widgetLaunchIntent(
    context: Context,
    action: WidgetAction,
    documentId: Long? = null,
): Intent {
    val destination = WidgetDestination(action, documentId)
    return Intent(context, MainActivity::class.java).apply {
        this.action = widgetIntentAction(context.packageName, action)
        data = widgetUri(context.packageName, destination)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}

/** MainActivity is exported; only accept our small action/ID protocol, never a forwarded Intent or URI. */
internal fun parseWidgetIntent(context: Context, intent: Intent?): WidgetDestination? {
    if (intent == null || intent.component != ComponentName(context, MainActivity::class.java) ||
        intent.selector != null
    ) return null
    val action = WidgetAction.entries.firstOrNull {
        intent.action == widgetIntentAction(context.packageName, it)
    } ?: return null
    val uri = intent.data ?: return null
    val documentId = if (action == WidgetAction.OPEN_DOCUMENT) {
        uri.lastPathSegment?.toLongOrNull()?.takeIf { it > 0 } ?: return null
    } else null
    val destination = WidgetDestination(action, documentId)
    // Equality rejects additional paths, alternate authorities, query parameters and URI fragments.
    return destination.takeIf { uri == widgetUri(context.packageName, it) }
}

private fun widgetIntentAction(packageName: String, action: WidgetAction): String =
    "${packageName}.widget.${action.name}"

private fun widgetUri(packageName: String, destination: WidgetDestination): Uri =
    Uri.Builder()
        .scheme("scaniqo-widget")
        .authority(packageName)
        .appendPath(destination.action.name)
        .apply { destination.documentId?.let { appendPath(it.toString()) } }
        .build()
