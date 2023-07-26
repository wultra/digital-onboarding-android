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

enum class VerificationState {

    /** Show verification introduction screen */
    INTRO,
    /** Show approve/cancel user consent */
    CONSENT,
    /** Show document selection to the user */
    DOCUMENTS_TO_SCAN_SELECT,
    /** User should scan documents */
    SCAN_DOCUMENT,
    /** The system is processing data */
    PROCESSING,
    /** User should be presented with a presence check */
    PRESENCE_CHECK,
    /** User should enter OTP */
    OTP,
    /** Verification failed and can be restarted */
    FAILED,
    /** Verification is canceled and user needs to start again with an activation */
    ENDSTATE,
    /** Verification was successfully ended */
    SUCCESS
}

abstract class VerificationStateData(
    /** State of the verification process */
    val state: VerificationState
)

object VerificationStateIntroData: VerificationStateData(VerificationState.INTRO)
object VerificationStateDocumentsToScanSelectData: VerificationStateData(VerificationState.DOCUMENTS_TO_SCAN_SELECT)
object VerificationStatePresenceCheckData: VerificationStateData(VerificationState.PRESENCE_CHECK)
object VerificationStateSuccessData: VerificationStateData(VerificationState.SUCCESS)
object VerificationStateFailedData: VerificationStateData(VerificationState.FAILED)

class VerificationStateConsentData(
    /** HTML data for the `CONSENT` state */
    val consentHtml: String
): VerificationStateData(VerificationState.CONSENT)

class VerificationStateScanDocumentData(
    /** Data for the `SCAN_DOCUMENT` state */
    val scanDocumentProcess: VerificationScanProcess
): VerificationStateData(VerificationState.SCAN_DOCUMENT)

class VerificationStateProcessingData(
    /** Reason for the `PROCESSING` state */
    val processingItem: ProcessingItem
): VerificationStateData(VerificationState.PROCESSING)

class VerificationStateOtpData(
    /** Remaining attempts for the `OTP` state */
    val remainingAttempts: Int?
): VerificationStateData(VerificationState.OTP)

class VerificationStateEndstateData(
    /** Endstate reason for the `ENDSTATE` state. */
    val endstateReason: EndstateReason
): VerificationStateData(VerificationState.ENDSTATE)

enum class ProcessingItem {
    /** Reason cannot be specified */
    OTHER,
    /** Documents are being uploaded to a internal systems */
    DOCUMENT_UPLOAD,
    /** Documents are being verified */
    DOCUMENT_VERIFICATION,
    /** Documents were accepted and we're waiting for a process change */
    DOCUMENT_ACCEPTED,
    /** Uploaded are being cross-checked if there are issues for the same person. */
    DOCUMENT_CROSS_VERIFICATION,
    /** Verifying presence of the user in front of the phone (selfie verification). */
    VERIFYING_PRESENCE,
    /** Client data provided are being verified by the system. */
    CLIENT_VERIFICATION,
    /** Client data were accepted and we're waiting for a process change */
    CLIENT_ACCEPTED;

    internal companion object {
        fun from(reason: VerificationStatusNextStep.StatusCheckReason): ProcessingItem {
            return when (reason) {
                VerificationStatusNextStep.StatusCheckReason.UNKNOWN -> OTHER
                VerificationStatusNextStep.StatusCheckReason.DOCUMENT_UPLOAD -> DOCUMENT_UPLOAD
                VerificationStatusNextStep.StatusCheckReason.DOCUMENT_VERIFICATION -> DOCUMENT_VERIFICATION
                VerificationStatusNextStep.StatusCheckReason.DOCUMENTS_ACCEPTED -> DOCUMENT_ACCEPTED
                VerificationStatusNextStep.StatusCheckReason.DOCUMENT_VERIFICATION_FINAL -> DOCUMENT_CROSS_VERIFICATION
                VerificationStatusNextStep.StatusCheckReason.CLIENT_VERIFICATION -> CLIENT_VERIFICATION
                VerificationStatusNextStep.StatusCheckReason.CLIENT_ACCEPTED -> CLIENT_ACCEPTED
                VerificationStatusNextStep.StatusCheckReason.VERIFYING_PRESENCE -> VERIFYING_PRESENCE
            }
        }
    }
}

enum class EndstateReason {
    /** The verification was rejected */
    REJECTED,
    /** Limit of repeat tries was reached */
    LIMIT_REACHED,
    /** Other reason */
    OTHER
}
