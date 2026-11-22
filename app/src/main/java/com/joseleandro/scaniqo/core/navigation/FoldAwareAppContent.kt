package com.joseleandro.scaniqo.core.navigation

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.round
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.map

internal data class SeparatingFold(val bounds: IntRect, val isVertical: Boolean)

internal fun foldSafeBounds(
    window: IntRect,
    folds: List<SeparatingFold>,
    rightToLeft: Boolean = false,
): IntRect = folds.fold(window) { available, fold ->
    val hinge = fold.bounds
    val candidates = if (fold.isVertical &&
        hinge.top <= available.top && hinge.bottom >= available.bottom &&
        hinge.left > available.left && hinge.right < available.right
    ) {
        listOf(
            IntRect(available.left, available.top, hinge.left, available.bottom),
            IntRect(hinge.right, available.top, available.right, available.bottom),
        ).let { if (rightToLeft) it.reversed() else it }
    } else if (!fold.isVertical &&
        hinge.left <= available.left && hinge.right >= available.right &&
        hinge.top > available.top && hinge.bottom < available.bottom
    ) {
        listOf(
            IntRect(available.left, available.top, available.right, hinge.top),
            IntRect(available.left, hinge.bottom, available.right, available.bottom),
        )
    } else {
        listOf(available)
    }
    candidates.maxBy { it.width.toLong() * it.height }
}

@Composable
internal fun FoldAwareAppContent(
    activity: Activity,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val layoutInfo = remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).map { info ->
            info.displayFeatures.filterIsInstance<FoldingFeature>()
                .filter { it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL }
                .map {
                    SeparatingFold(
                        IntRect(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom),
                        it.orientation == FoldingFeature.Orientation.VERTICAL,
                    )
                }
        }
    }
    val folds by layoutInfo.collectAsStateWithLifecycle(emptyList())
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    val density = LocalDensity.current
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(
        modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { origin = it.positionInWindow().round() },
    ) {
        val window = IntRect(origin.x, origin.y, origin.x + constraints.maxWidth, origin.y + constraints.maxHeight)
        val bounds = foldSafeBounds(window, folds, rightToLeft)
        val left = bounds.left - window.left
        val top = bounds.top - window.top
        val right = window.right - bounds.right
        val bottom = window.bottom - bounds.bottom
        Box(
            Modifier
                .offset { IntOffset(if (rightToLeft) right else left, top) }
                .requiredSize(
                    with(density) { bounds.width.toDp() },
                    with(density) { bounds.height.toDp() },
                )
                .consumeWindowInsets(
                    with(density) {
                        PaddingValues(
                            start = (if (rightToLeft) right else left).toDp(),
                            top = top.toDp(),
                            end = (if (rightToLeft) left else right).toDp(),
                            bottom = bottom.toDp(),
                        )
                    },
                )
                .clipToBounds(),
        ) {
            content()
        }
    }
}
