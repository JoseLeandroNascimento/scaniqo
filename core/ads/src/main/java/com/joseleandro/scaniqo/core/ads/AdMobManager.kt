package com.joseleandro.scaniqo.core.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.joseleandro.scaniqo.domain.repository.PremiumEntitlement
import com.joseleandro.scaniqo.domain.repository.PremiumEntitlementRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AdMobState(
    val sdkReady: Boolean = false,
    val canRequestAds: Boolean = false,
    val consentUpdating: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
    val privacyFormOpen: Boolean = false,
    val requestGeneration: Int = 0,
    val rewardedAdShowing: Boolean = false,
    val entitlement: PremiumEntitlement = PremiumEntitlement.UNKNOWN,
) {
    val displayAdsAllowed: Boolean
        get() = entitlement == PremiumEntitlement.FREE && sdkReady && canRequestAds &&
            !consentUpdating && !privacyFormOpen && !rewardedAdShowing

    val rewardedAdsAllowed: Boolean
        get() = displayAdsAllowed
}

class AdMobManager(
    context: Context,
    private val premiumRepository: PremiumEntitlementRepository,
) {
    private val applicationContext = context.applicationContext
    private val configuration = AdMobConfiguration.from(applicationContext)
    private val consent = UserMessagingPlatform.getConsentInformation(applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(AdMobState(
        entitlement = premiumRepository.entitlement.value,
        privacyOptionsRequired = consent.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
    ))
    val state = mutableState.asStateFlow()
    val bannerUnitId: String? = configuration.unitId(AdPlacement.TOOLS_BANNER)

    fun unitId(placement: AdPlacement): String? = configuration.unitId(placement)
    private var initializing = false
    private var consentRequest = 0

    init {
        scope.launch {
            premiumRepository.entitlement.collect { entitlement ->
                val current = mutableState.value
                if (current.entitlement != entitlement) {
                    mutableState.value = current.copy(
                        entitlement = entitlement,
                        requestGeneration = current.requestGeneration + 1,
                    )
                }
                initializeIfAllowed()
            }
        }
    }

    @MainThread
    internal fun setRewardedAdShowing(showing: Boolean) {
        mutableState.value = mutableState.value.copy(rewardedAdShowing = showing)
    }

    fun gatherConsent(activity: Activity) {
        if (!configuration.enabled || premiumRepository.entitlement.value != PremiumEntitlement.FREE ||
            activity.isFinishing || activity.isDestroyed
        ) return
        val request = ++consentRequest
        mutableState.value = mutableState.value.copy(
            consentUpdating = true,
            privacyFormOpen = false,
            requestGeneration = mutableState.value.requestGeneration + 1,
        )
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                if (request != consentRequest || activity.isFinishing || activity.isDestroyed) return@requestConsentInfoUpdate
                if (premiumRepository.entitlement.value != PremiumEntitlement.FREE) {
                    completeConsent(request)
                    return@requestConsentInfoUpdate
                }
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    error?.let { Log.w("ScaniqoAds", "Consent form failed (${it.errorCode}): ${it.message}") }
                    completeConsent(request)
                }
            },
            { error ->
                Log.w("ScaniqoAds", "Consent update failed (${error.errorCode}): ${error.message}")
                completeConsent(request)
            },
        )
    }

    fun showPrivacyOptions(activity: Activity, onComplete: (Boolean) -> Unit) {
        if (activity.isFinishing || activity.isDestroyed || mutableState.value.privacyFormOpen ||
            mutableState.value.consentUpdating || !mutableState.value.privacyOptionsRequired
        ) return
        val request = ++consentRequest
        mutableState.value = mutableState.value.copy(
            privacyFormOpen = true,
            canRequestAds = false,
            requestGeneration = mutableState.value.requestGeneration + 1,
        )
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            error?.let { Log.w("ScaniqoAds", "Privacy options failed (${it.errorCode}): ${it.message}") }
            if (request == consentRequest) {
                completeConsent(request)
                onComplete(error == null)
            }
        }
    }

    private fun completeConsent(request: Int) {
        scope.launch {
            if (request != consentRequest) return@launch
            mutableState.value = mutableState.value.copy(
                canRequestAds = consent.canRequestAds(),
                consentUpdating = false,
                privacyFormOpen = false,
                privacyOptionsRequired = consent.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
            )
            initializeIfAllowed()
        }
    }

    private fun initializeIfAllowed() {
        if (premiumRepository.entitlement.value != PremiumEntitlement.FREE ||
            !mutableState.value.canRequestAds || mutableState.value.sdkReady || initializing
        ) return
        initializing = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    MobileAds.initialize(
                        applicationContext,
                        InitializationConfig.Builder(configuration.appId).build(),
                    ) {}
                }
                mutableState.value = mutableState.value.copy(sdkReady = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("ScaniqoAds", "Ad SDK initialization failed.", failure)
            } finally {
                initializing = false
            }
        }
    }
}
