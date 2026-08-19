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

/**
 * Raw activation flags reported by the server for this activation status.
 */
fun ActivationStatus.activationFlags() = (customObject?.let { it["activationFlags"] as? List<*> })?.filterIsInstance<String>() ?: emptyList()
fun ActivationStatus.verificationPending() = activationFlags().contains("VERIFICATION_PENDING")
fun ActivationStatus.verificationInProgress() = activationFlags().contains("VERIFICATION_IN_PROGRESS")

/**
 * Checks whether a re-verification (Re-KYC) identity verification process was already initialized.
 *
 * This checks for the `RE_KYC_IN_PROGRESS` flag, which is only one possible convention - the server
 * process configuration allows using an arbitrary custom flag name instead of the standard
 * `VERIFICATION_IN_PROGRESS`. If your backend is configured with a different custom flag, this method
 * won't detect it; check [activationFlags] for that flag name directly instead.
 */
fun ActivationStatus.reKycInProgress() = activationFlags().contains("RE_KYC_IN_PROGRESS")

/**
 * When true, activation needs to be verified via `VerificationService`. This is also `true` when a
 * re-verification (Re-KYC) process, triggered via `VerificationService.startReVerification`, is in
 * progress - by default the server signals this with the same flags as a regular verification, unless
 * it's configured to use a dedicated flag instead (see [reKycInProgress]).
 */
fun ActivationStatus.needVerification() = verificationPending() || verificationInProgress() || reKycInProgress()
