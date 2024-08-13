/*
 * Copyright 2023 Wultra s.r.o.
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

import com.wultra.android.digitalonboarding.networking.model.VerificationPhase
import com.wultra.android.digitalonboarding.networking.model.IdentityVerificationStatus.*
import com.wultra.android.digitalonboarding.networking.model.VerificationPhase.*
import com.wultra.android.digitalonboarding.networking.model.IdentityVerificationStatus
import com.wultra.android.digitalonboarding.networking.model.VerificationStatusResponseData

// Internal status that works as a translation layer between server API and SDK API
internal class VerificationStatusNextStep(val value: Value, val statusCheckReason: StatusCheckReason? = null) {

    companion object {
        fun fromStatusResponse(response: VerificationStatusResponseData): VerificationStatusNextStep {

            fun match(phase: VerificationPhase?, status: IdentityVerificationStatus) = response.phase == phase && response.status == status

            return when {
                match(null, NOT_INITIALIZED) -> VerificationStatusNextStep(Value.INTRO)
                match(null, FAILED) -> VerificationStatusNextStep(Value.FAILED)
                match(DOCUMENT_UPLOAD, IN_PROGRESS) -> VerificationStatusNextStep(Value.DOCUMENT_SCAN)
                match(DOCUMENT_UPLOAD, VERIFICATION_PENDING) -> VerificationStatusNextStep(Value.STATUS_CHECK, StatusCheckReason.DOCUMENT_VERIFICATION)
                match(DOCUMENT_VERIFICATION, ACCEPTED) -> VerificationStatusNextStep(Value.STATUS_CHECK, StatusCheckReason.DOCUMENTS_ACCEPTED)
                match(DOCUMENT_VERIFICATION, IN_PROGRESS) -> VerificationStatusNextStep(Value.STATUS_CHECK, StatusCheckReason.DOCUMENT_VERIFICATION)
                match(DOCUMENT_VERIFICATION, FAILED) -> VerificationStatusNextStep(Value.FAILED)
                match(DOCUMENT_VERIFICATION_FINAL, REJECTED) -> VerificationStatusNextStep(Value.REJECTED)
                match(DOCUMENT_VERIFICATION_FINAL, ACCEPTED) -> VerificationStatusNextStep(Value.STATUS_CHECK, StatusCheckReason.DOCUMENTS_ACCEPTED)
                match(DOCUMENT_VERIFICATION_FINAL, IN_PROGRESS) -> VerificationStatusNextStep(Value.STATUS_CHECK, StatusCheckReason.DOCUMENT_VERIFICATION_FINAL)
                match(DOCUMENT_VERIFICATION_FINAL, FAILED) -> VerificationStatusNextStep(Value.FAILED)
                match(DOCUMENT_VERIFICATION, REJECTED) -> VerificationStatusNextStep(Value.REJECTED)
                match(CLIENT_EVALUATION, IN_PROGRESS) -> VerificationStatusNextStep(Value.STATUS_CHECK, StatusCheckReason.CLIENT_VERIFICATION)
                match(CLIENT_EVALUATION, ACCEPTED) -> VerificationStatusNextStep(Value.STATUS_CHECK, StatusCheckReason.CLIENT_ACCEPTED)
                match(DOCUMENT_VERIFICATION, REJECTED) -> VerificationStatusNextStep(Value.REJECTED)
                match(DOCUMENT_VERIFICATION, FAILED) -> VerificationStatusNextStep(Value.FAILED)
                match(PRESENCE_CHECK, NOT_INITIALIZED) -> VerificationStatusNextStep(Value.PRESENCE_CHECK)
                match(PRESENCE_CHECK, IN_PROGRESS) -> VerificationStatusNextStep(Value.PRESENCE_CHECK)
                match(PRESENCE_CHECK, VERIFICATION_PENDING) -> VerificationStatusNextStep(Value.STATUS_CHECK, StatusCheckReason.VERIFYING_PRESENCE)
                match(PRESENCE_CHECK, FAILED) -> VerificationStatusNextStep(Value.FAILED)
                match(PRESENCE_CHECK, REJECTED) -> VerificationStatusNextStep(Value.REJECTED)
                match(OTP_VERIFICATION, VERIFICATION_PENDING) -> VerificationStatusNextStep(Value.OTP)
                match(COMPLETED, ACCEPTED) -> VerificationStatusNextStep(Value.SUCCESS)
                match(COMPLETED, FAILED) -> VerificationStatusNextStep(Value.FAILED)
                match(COMPLETED, REJECTED) -> VerificationStatusNextStep(Value.REJECTED)
                else -> throw NotImplementedError("Unknown phase/status combo: ${response.phase?.name}, ${response.status.name}")
            }
        }
    }

    enum class Value {
        INTRO,
        DOCUMENT_SCAN,
        STATUS_CHECK,
        PRESENCE_CHECK,
        OTP,
        FAILED,
        REJECTED,
        SUCCESS
    }

    enum class StatusCheckReason {
        UNKNOWN,
        DOCUMENT_UPLOAD,
        DOCUMENT_VERIFICATION,
        DOCUMENT_VERIFICATION_FINAL,
        DOCUMENTS_ACCEPTED,
        CLIENT_VERIFICATION,
        CLIENT_ACCEPTED,
        VERIFYING_PRESENCE
    }
}
