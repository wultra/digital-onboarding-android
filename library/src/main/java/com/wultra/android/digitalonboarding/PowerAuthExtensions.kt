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

fun FailedApiException.onboardingOtpRemainingAttempts(): Int? = responseJson?.get("remainingAttempts")?.asInt
fun FailedApiException.allowOnboardingOtpRetry() = onboardingOtpRemainingAttempts()?.let { it > 0 }

private fun ActivationStatus.activationFlags() = (customObject?.let { it["activationFlags"] as? List<*> })?.filterIsInstance<String>() ?: emptyList()
fun ActivationStatus.verificationPending() = activationFlags().contains("VERIFICATION_PENDING")
fun ActivationStatus.verificationInProgress() = activationFlags().contains("VERIFICATION_IN_PROGRESS")
fun ActivationStatus.needVerification() = verificationPending() || verificationInProgress()
