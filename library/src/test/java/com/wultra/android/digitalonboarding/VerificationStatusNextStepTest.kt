/*
 * Copyright 2026 Wultra s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.wultra.android.digitalonboarding

import com.wultra.android.digitalonboarding.networking.model.IdentityVerificationStatus
import com.wultra.android.digitalonboarding.networking.model.VerificationPhase
import com.wultra.android.digitalonboarding.networking.model.VerificationStatusResponseData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationStatusNextStepTest {

    @Test
    fun notInitializedWithNoPhaseDefaultsToIntroAndConsentRequired() {
        val result = VerificationStatusNextStep.fromStatusResponse(
            response(phase = null, status = IdentityVerificationStatus.NOT_INITIALIZED, consentRequired = null)
        )

        assertEquals(VerificationStatusNextStep.Value.INTRO, result.value)
        assertTrue(result.consentRequired)
    }

    @Test
    fun documentUploadInProgressReturnsDocumentScan() {
        val result = VerificationStatusNextStep.fromStatusResponse(
            response(phase = VerificationPhase.DOCUMENT_UPLOAD, status = IdentityVerificationStatus.IN_PROGRESS)
        )

        assertEquals(VerificationStatusNextStep.Value.DOCUMENT_SCAN, result.value)
    }

    @Test
    fun documentUploadVerificationPendingReturnsStatusCheckDocumentVerification() {
        val result = VerificationStatusNextStep.fromStatusResponse(
            response(phase = VerificationPhase.DOCUMENT_UPLOAD, status = IdentityVerificationStatus.VERIFICATION_PENDING)
        )

        assertEquals(VerificationStatusNextStep.Value.STATUS_CHECK, result.value)
        assertEquals(VerificationStatusNextStep.StatusCheckReason.DOCUMENT_VERIFICATION, result.statusCheckReason)
    }

    @Test
    fun presenceCheckNotInitializedReturnsPresenceCheck() {
        val result = VerificationStatusNextStep.fromStatusResponse(
            response(phase = VerificationPhase.PRESENCE_CHECK, status = IdentityVerificationStatus.NOT_INITIALIZED)
        )

        assertEquals(VerificationStatusNextStep.Value.PRESENCE_CHECK, result.value)
    }

    @Test
    fun otpVerificationPendingReturnsOtp() {
        val result = VerificationStatusNextStep.fromStatusResponse(
            response(phase = VerificationPhase.OTP_VERIFICATION, status = IdentityVerificationStatus.VERIFICATION_PENDING)
        )

        assertEquals(VerificationStatusNextStep.Value.OTP, result.value)
    }

    @Test
    fun activationFinishAlwaysReturnsActivationFinish() {
        val statuses = listOf(
            IdentityVerificationStatus.NOT_INITIALIZED,
            IdentityVerificationStatus.IN_PROGRESS,
            IdentityVerificationStatus.ACCEPTED,
            IdentityVerificationStatus.VERIFICATION_PENDING,
        )

        statuses.forEach { status ->
            val result = VerificationStatusNextStep.fromStatusResponse(
                response(phase = VerificationPhase.ACTIVATION_FINISH, status = status)
            )
            assertEquals(VerificationStatusNextStep.Value.ACTIVATION_FINISH, result.value)
        }
    }

    @Test
    fun unknownPhaseStatusComboThrows() {
        var didThrow = false
        try {
            VerificationStatusNextStep.fromStatusResponse(
                response(phase = VerificationPhase.OTP_VERIFICATION, status = IdentityVerificationStatus.ACCEPTED)
            )
        } catch (_: NotImplementedError) {
            didThrow = true
        }
        assertTrue(didThrow)
    }

    private fun response(
        phase: VerificationPhase?,
        status: IdentityVerificationStatus,
        consentRequired: Boolean? = true,
        rejectReason: String? = null,
    ): VerificationStatusResponseData {
        return VerificationStatusResponseData(
            processId = "process-id",
            processType = "onboarding",
            rejectReason = rejectReason,
            status = status,
            phase = phase,
            consentRequired = consentRequired,
        )
    }
}
