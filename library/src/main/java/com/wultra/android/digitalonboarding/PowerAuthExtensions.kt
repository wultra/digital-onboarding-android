/*
 * Copyright (c) 2023, Wultra s.r.o. (www.wultra.com).
 *
 * All rights reserved. This source code can be used only for purposes specified
 * by the given license contract signed by the rightful deputy of Wultra s.r.o.
 * This source code can be used only by the owner of the license.
 *
 * Any disputes arising in respect of this agreement (license) shall be brought
 * before the Municipal Court of Prague.
 */

@file:Suppress("unused", "KDocUnresolvedReference")

package com.wultra.android.digitalonboarding

import io.getlime.security.powerauth.core.ActivationStatus
import io.getlime.security.powerauth.networking.exceptions.FailedApiException
import io.getlime.security.powerauth.networking.response.CreateActivationResult
import io.getlime.security.powerauth.networking.response.ICreateActivationListener
import io.getlime.security.powerauth.sdk.PowerAuthActivation
import io.getlime.security.powerauth.sdk.PowerAuthSDK

/** Activation data used in PowerAuth activation process (createActivation method). */
interface ActivationData {
    /** Process ID retrieved from `start` call. */
    fun processId(): String
    /** Attributes needed for the PowerAuth activation. */
    fun asAttributes(): Map<String, String>
}

/**
 * Creates powerauth activation based on the data in the [ActivationData] object.
 *
 * @param data Custom activation data
 * @param activationName Name of the activation
 * @param callback Result callback
 *
 * @throws PowerAuthErrorException when powerauth data cannot be constructed.
 */
fun PowerAuthSDK.createActivation(
    data: ActivationData,
    activationName: String,
    callback: (Result<CreateActivationResult>) -> Unit
) {
    val activation = PowerAuthActivation.Builder.customActivation(data.asAttributes(), activationName).build()
    createActivation(
        activation,
        object : ICreateActivationListener {
            override fun onActivationCreateSucceed(result: CreateActivationResult) {
                callback(Result.success(result))
            }

            override fun onActivationCreateFailed(t: Throwable) {
                callback(Result.failure(t))
            }
        }
    )
}

fun FailedApiException.onboardingOtpRemainingAttempts(): Int? = responseJson?.get("remainingAttempts")?.asInt
fun FailedApiException.allowOnboardingOtpRetry() = onboardingOtpRemainingAttempts()?.let { it > 0 }

private fun ActivationStatus.activationFlags() = (customObject?.let { it["activationFlags"] as? List<*> })?.filterIsInstance<String>() ?: emptyList()
fun ActivationStatus.verificationPending() = activationFlags().contains("VERIFICATION_PENDING")
fun ActivationStatus.verificationInProgress() = activationFlags().contains("VERIFICATION_IN_PROGRESS")
fun ActivationStatus.needVerification() = verificationPending() || verificationInProgress()
