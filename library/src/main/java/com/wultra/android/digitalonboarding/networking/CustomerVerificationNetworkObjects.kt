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

@file:Suppress("unused")

package com.wultra.android.digitalonboarding.networking

import com.google.gson.annotations.SerializedName
import com.wultra.android.powerauth.networking.data.ObjectRequest
import com.wultra.android.powerauth.networking.data.ObjectResponse
import com.wultra.android.powerauth.networking.data.StatusResponse

// TODO: make all internal

object EmptyRequest: ObjectRequest<EmptyRequestData>(EmptyRequestData)
object EmptyRequestData
class VerificationStatusResponse(responseObject: VerificationStatusResponseData, status: Status): ObjectResponse<VerificationStatusResponseData>(responseObject, status)
class VerificationStatusResponseData(
    @SerializedName("processId")
    val processId: String,

    @SerializedName("identityVerificationStatus")
    val status: IdentityVerificationStatus,

    @SerializedName("identityVerificationPhase")
    val phase: VerificationPhase?
)

enum class IdentityVerificationStatus {
    @SerializedName("NOT_INITIALIZED")
    NOT_INITIALIZED,

    @SerializedName("IN_PROGRESS")
    IN_PROGRESS,

    @SerializedName("VERIFICATION_PENDING")
    VERIFICATION_PENDING,

    @SerializedName("ACCEPTED")
    ACCEPTED,

    @SerializedName("FAILED")
    FAILED,

    @SerializedName("REJECTED")
    REJECTED
}

enum class VerificationPhase {
    @SerializedName("DOCUMENT_UPLOAD")
    DOCUMENT_UPLOAD,

    @SerializedName("PRESENCE_CHECK")
    PRESENCE_CHECK,

    @SerializedName("CLIENT_EVALUATION")
    CLIENT_EVALUATION,

    @SerializedName("DOCUMENT_VERIFICATION")
    DOCUMENT_VERIFICATION,

    @SerializedName("DOCUMENT_VERIFICATION_FINAL")
    DOCUMENT_VERIFICATION_FINAL,

    @SerializedName("OTP_VERIFICATION")
    OTP_VERIFICATION,

    @SerializedName("COMPLETED")
    COMPLETED
}

class DocumentsStatusRequest(processId: String): ObjectRequest<VerificationProcessRequestData>(
    VerificationProcessRequestData(processId)
)
class DocumentsStatusResponse(responseObject: DocumentsStatusResponseData, status: Status): ObjectResponse<DocumentsStatusResponseData>(responseObject, status)
class DocumentsStatusResponseData(
    @SerializedName("status")
    val status: DocumentStatus,

    @SerializedName("documents")
    val documents: List<Document>
)

class Document(
    @SerializedName("filename")
    val filename: String,

    @SerializedName("id")
    val id: String,

    @SerializedName("type")
    val type: DocumentSubmitFileType,

    @SerializedName("side")
    val side: DocumentFileSide?,

    @SerializedName("status")
    val status: DocumentStatus,

    @SerializedName("errors")
    val errors: List<String>?
)

enum class DocumentStatus {
    @SerializedName("ACCEPTED")
    ACCEPTED,

    @SerializedName("UPLOAD_IN_PROGRESS")
    UPLOAD_IN_PROGRESS,

    @SerializedName("IN_PROGRESS")
    IN_PROGRESS,

    @SerializedName("VERIFICATION_PENDING")
    VERIFICATION_PENDING,

    @SerializedName("VERIFICATION_IN_PROGRESS")
    VERIFICATION_IN_PROGRESS,

    @SerializedName("REJECTED")
    REJECTED,

    @SerializedName("FAILED")
    FAILED
}

enum class DocumentSubmitFileType {
    @SerializedName("ID_CARD")
    ID_CARD,

    @SerializedName("PASSPORT")
    PASSPORT,

    @SerializedName("DRIVING_LICENSE")
    DRIVING_LICENSE,

    @SerializedName("SELFIE_PHOTO")
    SELFIE_PHOTO
}

enum class DocumentFileSide {
    @SerializedName("FRONT")
    FRONT,

    @SerializedName("BACK")
    BACK
}

class StartRequest(processId: String): ObjectRequest<VerificationProcessRequestData>(
    VerificationProcessRequestData(processId)
)
class CancelRequest(processId: String): ObjectRequest<VerificationProcessRequestData>(
    VerificationProcessRequestData(processId)
)
open class VerificationProcessRequestData(
    @SerializedName("processId")
    val processId: String
)

class ConsentRequest(processId: String, consentType: String = "GDPR"): ObjectRequest<ConsentRequestData>(
    ConsentRequestData(processId, consentType)
)
class ConsentRequestData(
    @SerializedName("processId")
    val processId: String,

    @SerializedName("consentType")
    val consentType: String
)
class ConsentResponse(
    responseObject: ConsentResponseData,
    status: Status
): ObjectResponse<ConsentResponseData>(responseObject, status)
class ConsentResponseData(
    @SerializedName("consentText")
    val consentText: String
)

class ConsentApproveRequest(
    processId: String,
    approved: Boolean,
    consentType: String = "GDPR"
): ObjectRequest<ConsentApproveRequestData>(ConsentApproveRequestData(processId, approved, consentType))
class ConsentApproveRequestData(
    @SerializedName("processId")
    val processId: String,

    @SerializedName("approved")
    val approved: Boolean,

    @SerializedName("consentType")
    val consentType: String
)
class ConsentApproveResponse(status: Status): StatusResponse(status)

class SDKInitRequest(processId: String, challenge: String): ObjectRequest<SDKInitRequestData>(
    SDKInitRequestData(processId, SDKInitRequestDataAttributes(challenge))
)
class SDKInitRequestData(
    @SerializedName("processId")
    val processId: String,

    @SerializedName("attributes")
    val attributes: SDKInitRequestDataAttributes
)
class SDKInitRequestDataAttributes(
    @SerializedName("sdk-init-token")
    val challengeToken: String
)
class SDKInitResponse(
    responseObject: SDKInitResponseData,
    status: Status
): ObjectResponse<SDKInitResponseData>(responseObject, status)
class SDKInitResponseData(
    @SerializedName("attributes")
    val attributes: SDKInitResponseDataAttributes
)
// TODO: this needs to be more generic/configurable
class SDKInitResponseDataAttributes(
    @SerializedName("zenid-sdk-init-response")
    val responseToken: String
)

class DocumentSubmitRequest(data: DocumentSubmitRequestData): ObjectRequest<DocumentSubmitRequestData>(data)
class DocumentSubmitRequestData(
    processId: String,
    @SerializedName("data")
    val data: String,

    @SerializedName("resubmit")
    val resubmit: Boolean,

    @SerializedName("documents")
    val documents: List<DocumentSubmitFile>
): VerificationProcessRequestData(processId)

data class DocumentSubmitFile(
    @SerializedName("filename")
    val filename: String,

    @SerializedName("type")
    val type: DocumentSubmitFileType,

    @SerializedName("side")
    val side: DocumentFileSide?,

    @SerializedName("originalDocumentId")
    val originalDocumentId: String?
)

class DocumentSubmitResponse(status: Status): StatusResponse(status)

class PresenceCheckRequest(processId: String): ObjectRequest<VerificationProcessRequestData>(
    VerificationProcessRequestData((processId))
)
class PresenceCheckResponse(responseObject: PresenceCheckResponseData, status: Status): ObjectResponse<PresenceCheckResponseData>(responseObject, status)
class PresenceCheckResponseData(
    @SerializedName("sessionAttributes")
    val attributes: Map<String, Any>
)

class PresenceCheckSubmitRequest(processId: String): ObjectRequest<VerificationProcessRequestData>(
    VerificationProcessRequestData(processId)
)

class VerificationResendOtpRequest(processId: String): ObjectRequest<VerificationProcessRequestData>(
    VerificationProcessRequestData((processId))
)
class ResendOtpResponse(status: Status): StatusResponse(status)

class VerifyOtpRequest(processId: String, otp: String): ObjectRequest<VerifyOtpRequestData>(
    VerifyOtpRequestData(processId, otp)
)
class VerifyOtpRequestData(
    @SerializedName("processId")
    val processId: String,

    @SerializedName("otpCode")
    val otp: String
)
class VerifyOtpResponse(
    responseObject: VerifyOtpResponseData,
    status: Status
): ObjectResponse<VerifyOtpResponseData>(responseObject, status)
class VerifyOtpResponseData(
    @SerializedName("processId")
    val processId: String,

    @SerializedName("onboardingStatus")
    val onboardingStatus: OnboardingStatus,

    @SerializedName("verified")
    val verified: Boolean,

    @SerializedName("expired")
    val expired: Boolean,

    @SerializedName("remainingAttempts")
    val remainingAttempts: Int
)
