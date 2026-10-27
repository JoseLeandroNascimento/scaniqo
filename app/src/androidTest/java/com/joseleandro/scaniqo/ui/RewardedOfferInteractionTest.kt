package com.joseleandro.scaniqo.ui

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.platform.app.InstrumentationRegistry
import com.joseleandro.scaniqo.R
import com.joseleandro.scaniqo.core.ads.AdMobState
import com.joseleandro.scaniqo.core.ads.RewardedAdCompletion
import com.joseleandro.scaniqo.core.ads.RewardedAdStatus
import com.joseleandro.scaniqo.core.ads.RewardedAdUiState
import com.joseleandro.scaniqo.core.ads.RewardedTool
import com.joseleandro.scaniqo.core.ads.ToolAdAccess
import com.joseleandro.scaniqo.ui.ads.ToolAdAction
import com.joseleandro.scaniqo.ui.ads.ToolAdActionContent
import com.joseleandro.scaniqo.ui.ads.ToolAdActionEnvironment
import com.joseleandro.scaniqo.ui.ads.ToolAdRewardHint
import com.joseleandro.scaniqo.ui.ads.ToolAdPreloadOwners
import com.joseleandro.scaniqo.ui.ads.rememberToolAdAction
import com.joseleandro.scaniqo.ui.theme.ScaniqoTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RewardedOfferInteractionTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private lateinit var action: ToolAdAction
    private lateinit var screen: TestLifecycleOwner
    private lateinit var process: TestLifecycleOwner

    @Test
    fun explicitActionShowsDirectlyAndOnlyAppliesAfterEarnedDismissalAndResume() {
        val environment = FakeEnvironment()
        val applications = mutableListOf<Boolean>()
        compose.setContent { TestAction(environment, onApply = applications::add) }
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onNodeWithText(text(R.string.tool_ad_reward_hint)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, environment.shows) }
        compose.onNodeWithText(text(R.string.tool_ad_watch_apply)).performClick()
        compose.runOnIdle {
            assertEquals(1, environment.shows)
            assertTrue(applications.isEmpty())
            screen.registry.currentState = Lifecycle.State.CREATED
            environment.complete(earned = true)
        }
        compose.runOnIdle { assertTrue(applications.isEmpty()) }
        compose.runOnIdle { screen.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals(listOf(false), applications) }
    }

    @Test
    fun clickWithANormalLabelCannotOpenAVideoThatJustFinishedLoading() {
        val environment = FakeEnvironment(RewardedAdStatus.LOADING)
        val applications = mutableListOf<Boolean>()
        compose.setContent { TestAction(environment, onApply = applications::add) }
        compose.runOnIdle {
            assertFalse(action.showsVideo)
            val displayedAction = action.onClick
            environment.videoState.value = RewardedAdUiState(RewardedAdStatus.READY)
            displayedAction()
        }
        compose.runOnIdle {
            assertEquals(0, environment.shows)
            assertEquals(listOf(true), applications)
        }
    }

    @Test
    fun noConsentOrInventoryAppliesWithoutWaitingForAnAd() {
        val environment = FakeEnvironment(RewardedAdStatus.UNAVAILABLE).apply {
            adsState.value = AdMobState(sdkReady = true, canRequestAds = false)
        }
        val applications = mutableListOf<Boolean>()
        compose.setContent { TestAction(environment, onApply = applications::add) }
        compose.onNodeWithText(APPLY).performClick()
        compose.runOnIdle {
            assertEquals(0, environment.shows)
            assertEquals(listOf(true), applications)
        }
    }

    @Test
    fun twoClicksInTheSameFrameShowOneVideoAndCanceledVideoDoesNotApply() {
        val environment = FakeEnvironment()
        val applications = mutableListOf<Boolean>()
        compose.setContent { TestAction(environment, onApply = applications::add) }
        compose.runOnIdle {
            action.onClick()
            action.onClick()
            assertEquals(1, environment.shows)
            environment.complete(earned = false)
        }
        compose.runOnIdle { assertTrue(applications.isEmpty()) }
    }

    @Test
    fun showFailureContinuesWithoutAnAd() {
        val environment = FakeEnvironment()
        val applications = mutableListOf<Boolean>()
        compose.setContent { TestAction(environment, onApply = applications::add) }
        compose.onNodeWithText(text(R.string.tool_ad_watch_apply)).performClick()
        compose.runOnIdle { environment.complete(earned = false, failed = true) }
        compose.runOnIdle { assertEquals(listOf(true), applications) }
    }

    @Test
    fun changingDocumentInvalidatesContinuationAndKeepsEarnedCredit() {
        val environment = FakeEnvironment()
        var document by mutableStateOf("first")
        val applications = mutableListOf<Boolean>()
        compose.setContent { TestAction(environment, document, onApply = applications::add) }
        compose.onNodeWithText(text(R.string.tool_ad_watch_apply)).performClick()
        compose.runOnIdle { document = "second" }
        compose.runOnIdle { environment.complete(earned = true) }
        compose.runOnIdle {
            assertTrue(applications.isEmpty())
            assertEquals(ToolAdAccess.REWARDED, environment.access(RewardedTool.COMPRESSION))
        }
        compose.onNodeWithText(APPLY).performClick()
        compose.runOnIdle { assertEquals(listOf(false), applications) }
    }

    @Test
    fun backgroundingTheProcessInvalidatesContinuationWithoutDiscardingReward() {
        val environment = FakeEnvironment()
        val applications = mutableListOf<Boolean>()
        compose.setContent { TestAction(environment, onApply = applications::add) }
        compose.onNodeWithText(text(R.string.tool_ad_watch_apply)).performClick()
        compose.runOnIdle {
            process.registry.currentState = Lifecycle.State.CREATED
            screen.registry.currentState = Lifecycle.State.STARTED
            environment.complete(earned = true)
        }
        compose.runOnIdle {
            process.registry.currentState = Lifecycle.State.RESUMED
            screen.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.runOnIdle {
            assertTrue(applications.isEmpty())
            assertEquals(ToolAdAccess.REWARDED, environment.access(RewardedTool.COMPRESSION))
        }
    }

    @Test
    fun disablingTheScreenPreventsAutomaticApplication() {
        val environment = FakeEnvironment()
        var enabled by mutableStateOf(true)
        val applications = mutableListOf<Boolean>()
        compose.setContent { TestAction(environment, active = enabled, onApply = applications::add) }
        compose.onNodeWithText(text(R.string.tool_ad_watch_apply)).performClick()
        compose.runOnIdle { enabled = false }
        compose.runOnIdle { environment.complete(earned = true) }
        compose.runOnIdle { enabled = true }
        compose.runOnIdle { assertTrue(applications.isEmpty()) }
    }

    @Test
    fun resumedCompletionUsesTheLatestApplyCallback() {
        val environment = FakeEnvironment()
        var callbackName by mutableStateOf("old")
        val applications = mutableListOf<String>()
        compose.setContent {
            val name = callbackName
            TestAction(environment, onApply = { applications += name })
        }
        compose.onNodeWithText(text(R.string.tool_ad_watch_apply)).performClick()
        compose.runOnIdle { callbackName = "new" }
        compose.runOnIdle { environment.complete(earned = true) }
        compose.runOnIdle { assertEquals(listOf("new"), applications) }
    }

    @Test
    fun inactiveControllersCannotReleaseTheActiveToolsPreload() {
        val owners = ToolAdPreloadOwners()
        val sharedAdManager = Any()
        val previousTool = Any()
        val currentTool = Any()
        var releases = 0
        owners.claim(sharedAdManager, previousTool)
        owners.claim(sharedAdManager, currentTool)
        owners.release(sharedAdManager, previousTool) { releases++ }
        assertEquals(0, releases)
        owners.release(sharedAdManager, currentTool) { releases++ }
        owners.release(sharedAdManager, currentTool) { releases++ }
        assertEquals(1, releases)
    }

    @Test
    fun actionAndDisclosureRemainVisibleWithLargeText() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                ScaniqoTheme {
                    Surface {
                        Column(Modifier.width(320.dp)) {
                            Button(onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                ToolAdActionContent(true, stringResource(R.string.tool_ad_watch_save))
                            }
                            ToolAdRewardHint()
                        }
                    }
                }
            }
        }
        compose.onNodeWithText(text(R.string.tool_ad_watch_save)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tool_ad_reward_hint)).assertIsDisplayed()
    }


    @Test
    fun eachSuccessfulActionRequiresANewVideoWithoutAFreeRepeat() {
        val environment = FakeEnvironment()
        var applications = 0
        compose.setContent {
            TestAction(environment, onApply = {
                applications++
                environment.grantedAccess = ToolAdAccess.AD_REQUIRED
            })
        }
        repeat(3) { index ->
            compose.runOnIdle {
                environment.videoState.value = RewardedAdUiState(RewardedAdStatus.READY)
            }
            compose.onNodeWithText(text(R.string.tool_ad_watch_apply)).performClick()
            compose.runOnIdle {
                assertEquals(index + 1, environment.shows)
                assertEquals(index, applications)
                environment.complete(earned = true)
            }
            compose.runOnIdle { assertEquals(index + 1, applications) }
        }
    }

    @Test
    fun editorActionHasNoVideoEvenWhenTheSdkHasOneReady() {
        val environment = FakeEnvironment()
        val applications = mutableListOf<Boolean>()
        compose.setContent { TestAction(environment, tool = null, onApply = applications::add) }

        repeat(2) {
            compose.onNodeWithText(APPLY).performClick()
            compose.waitForIdle()
        }
        compose.runOnIdle {
            assertFalse(action.showsVideo)
            assertEquals(0, environment.shows)
            assertEquals(listOf(false, false), applications)
        }
        compose.onNodeWithText(text(R.string.tool_ad_reward_hint)).assertDoesNotExist()
    }


    @Test
    fun synchronousDismissalPreloadsTheNextVideoWithoutALifecycleTransition() {
        val environment = FakeEnvironment().apply {
            finishSynchronously = true
            loadImmediately = true
        }
        val applications = mutableListOf<Boolean>()
        compose.setContent { TestAction(environment, onApply = applications::add) }
        var initialPrepares = 0
        compose.runOnIdle { initialPrepares = environment.prepares }

        compose.onNodeWithText(text(R.string.tool_ad_watch_apply)).performClick()
        compose.runOnIdle {
            assertEquals(1, environment.shows)
            assertTrue(environment.prepares > initialPrepares)
            assertTrue(applications.isEmpty())
        }
        compose.onNodeWithText(text(R.string.tool_ad_watch_apply)).performClick()
        compose.runOnIdle { assertEquals(2, environment.shows) }
    }

    @Test
    fun changedContextRefreshesConsumedCreditBeforeChoosingTheButtonLabel() {
        val environment = FakeEnvironment().apply {
            grantedAccess = ToolAdAccess.REWARDED
            loadImmediately = true
        }
        var document by mutableStateOf("first")
        compose.setContent { TestAction(environment, contextKey = document, onApply = {}) }
        compose.onNodeWithText(APPLY).assertIsDisplayed()

        compose.runOnIdle {
            environment.grantedAccess = ToolAdAccess.AD_REQUIRED
            document = "second"
        }
        compose.onNodeWithText(text(R.string.tool_ad_watch_apply)).performClick()
        compose.runOnIdle { assertEquals(1, environment.shows) }
    }

    @Composable
    private fun TestAction(
        environment: FakeEnvironment,
        contextKey: String = "document",
        active: Boolean = true,
        onApply: (Boolean) -> Unit,
        tool: RewardedTool? = RewardedTool.COMPRESSION,
    ) {
        val screenOwner = remember { TestLifecycleOwner() }
        val processOwner = remember { TestLifecycleOwner() }
        val currentAction = rememberToolAdAction(
            tool, contextKey, active, onApply,
            environment, screenOwner.lifecycle, processOwner.lifecycle,
        )
        SideEffect {
            action = currentAction
            screen = screenOwner
            process = processOwner
        }
        ScaniqoTheme {
            Column {
                Button(onClick = currentAction.onClick, enabled = active && !currentAction.inProgress) {
                    ToolAdActionContent(
                        currentAction.showsVideo,
                        if (currentAction.showsVideo) stringResource(R.string.tool_ad_watch_apply) else APPLY,
                    )
                }
                if (currentAction.showsVideo) ToolAdRewardHint()
            }
        }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private class FakeEnvironment(status: RewardedAdStatus = RewardedAdStatus.READY) : ToolAdActionEnvironment {
        override val adsState = MutableStateFlow(AdMobState(sdkReady = true, canRequestAds = true))
        override val videoState = MutableStateFlow(RewardedAdUiState(status))
        var grantedAccess = ToolAdAccess.AD_REQUIRED
        var shows = 0
        var prepares = 0
        var finishSynchronously = false
        var loadImmediately = false
        private var requestId: String? = null
        private var requestedTool = RewardedTool.COMPRESSION

        override fun access(tool: RewardedTool) = grantedAccess
        override fun hasAdUnit() = true
        override fun prepare() {
            prepares++
            if (loadImmediately && videoState.value.status != RewardedAdStatus.SHOWING) {
                videoState.value = videoState.value.copy(status = RewardedAdStatus.READY)
            }
        }
        override fun releasePendingAd() {
            if (videoState.value.status != RewardedAdStatus.SHOWING) {
                videoState.value = videoState.value.copy(status = RewardedAdStatus.IDLE)
            }
        }
        override fun show(activity: Activity, requestId: String, tool: RewardedTool): Boolean {
            shows++
            this.requestId = requestId
            requestedTool = tool
            adsState.value = adsState.value.copy(rewardedAdShowing = true)
            videoState.value = videoState.value.copy(status = RewardedAdStatus.SHOWING)
            if (finishSynchronously) complete(earned = false)
            return true
        }
        override fun acknowledgeCompletion(requestId: String) {
            if (videoState.value.completion?.requestId == requestId) {
                videoState.value = videoState.value.copy(completion = null)
            }
        }
        fun complete(earned: Boolean, failed: Boolean = false) {
            if (earned) grantedAccess = ToolAdAccess.REWARDED
            adsState.value = adsState.value.copy(rewardedAdShowing = false)
            videoState.value = RewardedAdUiState(
                status = if (failed) RewardedAdStatus.UNAVAILABLE else RewardedAdStatus.IDLE,
                completion = RewardedAdCompletion(requireNotNull(requestId), earned, failed, requestedTool),
            )
        }
    }

    private companion object {
        const val APPLY = "Apply without video"
    }
}
