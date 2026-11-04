package com.joseleandro.scaniqo.ui.onboarding

import com.joseleandro.scaniqo.core.resources.R as CoreResourcesR
import com.joseleandro.scaniqo.feature.onboarding.R

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class TutorialPage(
    @StringRes val title: Int,
    @StringRes val description: Int,
)

private val tutorialPages = listOf(
    TutorialPage(R.string.onboarding_scan_title, R.string.onboarding_scan_body),
    TutorialPage(R.string.onboarding_tags_title, R.string.onboarding_tags_body),
    TutorialPage(R.string.onboarding_edit_title, R.string.onboarding_edit_body),
    TutorialPage(R.string.onboarding_share_title, R.string.onboarding_share_body),
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    saveError: Boolean = false,
    initialPage: Int = 0,
    animateIllustrations: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val pager = rememberPagerState(initialPage = initialPage.coerceIn(tutorialPages.indices)) {
        tutorialPages.size
    }
    val scope = rememberCoroutineScope()
    val lastPage = pager.currentPage == tutorialPages.lastIndex
    val controlsEnabled = !isSaving && !pager.isScrollInProgress

    Surface(modifier.fillMaxSize(), color = colors.surface) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp)
                    .fillMaxSize().safeDrawingPadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onFinish,
                        enabled = controlsEnabled,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("onboarding_skip"),
                    ) { Text(stringResource(R.string.onboarding_skip)) }
                }

                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("onboarding_pager"),
                    userScrollEnabled = !isSaving,
                    key = { it },
                ) { index ->
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val illustrationHeight = minOf(
                            (maxHeight * 0.62f).coerceIn(180.dp, 400.dp),
                            (maxWidth - 16.dp) * (300f / 320f),
                        )
                        val minimumHeight = maxHeight
                        Column(
                            modifier = Modifier.fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .heightIn(min = minimumHeight)
                                .padding(horizontal = 8.dp, vertical = 16.dp)
                                .graphicsLayer {
                                    val offset = ((pager.currentPage - index) +
                                        pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                                    alpha = 1f - 0.35f * offset
                                    scaleX = 1f - 0.04f * offset
                                    scaleY = scaleX
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            val page = tutorialPages[index]
                            OnboardingIllustration(
                                page = index,
                                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
                                    .height(illustrationHeight).clearAndSetSemantics {},
                                animate = animateIllustrations && pager.settledPage == index &&
                                    !pager.isScrollInProgress,
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = stringResource(page.title),
                                modifier = Modifier.padding(horizontal = 16.dp)
                                    .widthIn(max = 360.dp).semantics { heading() },
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(page.description),
                                modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 360.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.clearAndSetSemantics {},
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            repeat(tutorialPages.size) { index ->
                                val width by animateDpAsState(
                                    if (pager.currentPage == index) 28.dp else 8.dp,
                                    tween(250), label = "tutorial progress",
                                )
                                Box(Modifier.size(width, 8.dp).clip(CircleShape)
                                    .background(if (pager.currentPage == index) colors.primary
                                    else colors.outlineVariant))
                            }
                        }
                        Text(stringResource(R.string.onboarding_progress,
                            pager.currentPage + 1, tutorialPages.size),
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                    if (saveError) {
                        Text(stringResource(R.string.onboarding_save_error),
                            Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error, textAlign = TextAlign.Center)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (pager.currentPage > 0) {
                            OutlinedIconButton(
                                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                                enabled = controlsEnabled,
                                modifier = Modifier.size(56.dp).testTag("onboarding_previous"),
                            ) {
                                Icon(painterResource(CoreResourcesR.drawable.arrow_back_24px),
                                    stringResource(R.string.onboarding_previous))
                            }
                        }
                        Button(
                            onClick = {
                                if (lastPage) onFinish()
                                else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                            },
                            enabled = controlsEnabled,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("onboarding_next"),
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                                    color = colors.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(stringResource(when {
                                isSaving -> R.string.onboarding_saving
                                lastPage -> R.string.onboarding_start
                                else -> R.string.onboarding_next
                            }), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Primeiro acesso", widthDp = 390, heightDp = 844, locale = "pt-rBR")
@Preview(name = "Primeiro acesso escuro", widthDp = 390, heightDp = 844, locale = "pt-rBR",
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Fonte ampliada", widthDp = 320, heightDp = 640, locale = "pt-rBR", fontScale = 1.5f)
@Composable
private fun OnboardingPreview() {
    ScaniqoTheme { OnboardingScreen(onFinish = {}, animateIllustrations = false) }
}

@Preview(name = "Organize com tags", widthDp = 390, heightDp = 844, locale = "pt-rBR")
@Composable
private fun OnboardingTagsPreview() {
    ScaniqoTheme { OnboardingScreen(onFinish = {}, initialPage = 1, animateIllustrations = false) }
}

@Preview(name = "Edite e assine", widthDp = 390, heightDp = 844, locale = "pt-rBR")
@Composable
private fun OnboardingEditPreview() {
    ScaniqoTheme { OnboardingScreen(onFinish = {}, initialPage = 2, animateIllustrations = false) }
}

@Preview(name = "Compartilhe e guarde", widthDp = 390, heightDp = 844, locale = "pt-rBR")
@Composable
private fun OnboardingSharePreview() {
    ScaniqoTheme { OnboardingScreen(onFinish = {}, initialPage = 3, animateIllustrations = false) }
}
