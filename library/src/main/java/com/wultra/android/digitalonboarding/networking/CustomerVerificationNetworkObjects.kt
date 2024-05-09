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

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import com.wultra.android.digitalonboarding.log.WDOLogger
import com.wultra.android.powerauth.networking.data.ObjectRequest
import com.wultra.android.powerauth.networking.data.ObjectResponse
import com.wultra.android.powerauth.networking.data.StatusResponse
import java.lang.reflect.Type

internal object EmptyRequest: ObjectRequest<EmptyRequestData>(EmptyRequestData)
internal object EmptyRequestData

internal class VerificationStatusResponse(responseObject: VerificationStatusResponseData, status: Status): ObjectResponse<VerificationStatusResponseData>(responseObject, status)
internal class VerificationStatusResponseData(
    @SerializedName("processId") val processId: String,
    @SerializedName("identityVerificationStatus") val status: IdentityVerificationStatus,
    @SerializedName("identityVerificationPhase") val phase: VerificationPhase?,
    @SerializedName("config") val config: IdentityVerificationConfig
)
internal class IdentityVerificationConfig(
    @SerializedName("otpResendPeriod") val otpResendPeriod: String
)

internal enum class IdentityVerificationStatus {
    @SerializedName("NOT_INITIALIZED") NOT_INITIALIZED,
    @SerializedName("IN_PROGRESS") IN_PROGRESS,
    @SerializedName("VERIFICATION_PENDING") VERIFICATION_PENDING,
    @SerializedName("ACCEPTED") ACCEPTED,
    @SerializedName("FAILED") FAILED,
    @SerializedName("REJECTED") REJECTED
}

internal enum class VerificationPhase {
    @SerializedName("DOCUMENT_UPLOAD") DOCUMENT_UPLOAD,
    @SerializedName("PRESENCE_CHECK") PRESENCE_CHECK,
    @SerializedName("CLIENT_EVALUATION") CLIENT_EVALUATION,
    @SerializedName("DOCUMENT_VERIFICATION") DOCUMENT_VERIFICATION,
    @SerializedName("DOCUMENT_VERIFICATION_FINAL") DOCUMENT_VERIFICATION_FINAL,
    @SerializedName("OTP_VERIFICATION") OTP_VERIFICATION,
    @SerializedName("COMPLETED") COMPLETED
}

internal class DocumentsStatusRequest(processId: String): ObjectRequest<VerificationProcessRequestData>(
    VerificationProcessRequestData(processId)
)
internal class DocumentsStatusResponse(responseObject: DocumentsStatusResponseData, status: Status): ObjectResponse<DocumentsStatusResponseData>(responseObject, status)
internal class DocumentsStatusResponseData(
    @SerializedName("status") val status: DocumentStatus,
    @SerializedName("documents") val documents: List<Document>
)

internal class Document(
    @SerializedName("filename") val filename: String,
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: DocumentSubmitFileType,
    @SerializedName("side") val side: DocumentFileSide?,
    @SerializedName("status") val status: DocumentStatus,
    @SerializedName("errors") val errors: List<String>?
)

internal enum class DocumentStatus {
    @SerializedName("ACCEPTED") ACCEPTED,
    @SerializedName("UPLOAD_IN_PROGRESS") UPLOAD_IN_PROGRESS,
    @SerializedName("IN_PROGRESS") IN_PROGRESS,
    @SerializedName("VERIFICATION_PENDING") VERIFICATION_PENDING,
    @SerializedName("VERIFICATION_IN_PROGRESS") VERIFICATION_IN_PROGRESS,
    @SerializedName("REJECTED") REJECTED,
    @SerializedName("FAILED") FAILED
}

internal enum class DocumentSubmitFileType {
    @SerializedName("ID_CARD") ID_CARD,
    @SerializedName("PASSPORT") PASSPORT,
    @SerializedName("DRIVING_LICENSE") DRIVING_LICENSE,
    @SerializedName("SELFIE_PHOTO") SELFIE_PHOTO
}

internal enum class DocumentFileSide {
    @SerializedName("FRONT") FRONT,
    @SerializedName("BACK") BACK
}

internal class StartRequest(processId: String): ObjectRequest<VerificationProcessRequestData>(
    VerificationProcessRequestData(processId)
)
internal class CancelRequest(processId: String): ObjectRequest<VerificationProcessRequestData>(
    VerificationProcessRequestData(processId)
)
internal open class VerificationProcessRequestData(
    @SerializedName("processId") val processId: String
)

internal class ConsentRequest(processId: String, consentType: String = "GDPR"): ObjectRequest<ConsentRequestData>(
    ConsentRequestData(processId, consentType)
)
internal class ConsentRequestData(
    @SerializedName("processId") val processId: String,
    @SerializedName("consentType") val consentType: String
)
internal class ConsentResponse(
    responseObject: ConsentResponseData,
    status: Status
): ObjectResponse<ConsentResponseData>(responseObject, status)

internal class ConsentResponseData(
    @SerializedName("consentText") val consentText: String
)

internal class ConsentApproveRequest(
    processId: String,
    approved: Boolean,
    consentType: String = "GDPR"
): ObjectRequest<ConsentApproveRequestData>(ConsentApproveRequestData(processId, approved, consentType))

internal class ConsentApproveRequestData(
    @SerializedName("processId") val processId: String,
    @SerializedName("approved") val approved: Boolean,
    @SerializedName("consentType") val consentType: String
)
internal class ConsentApproveResponse(status: Status): StatusResponse(status)

internal class SDKInitRequest(processId: String, challenge: String): ObjectRequest<SDKInitRequestData>(
    SDKInitRequestData(processId, SDKInitRequestDataAttributes(challenge))
)
internal class SDKInitRequestData(
    @SerializedName("processId") val processId: String,
    @SerializedName("attributes") val attributes: SDKInitRequestDataAttributes
)
internal class SDKInitRequestDataAttributes(
    @SerializedName("sdk-init-token") val challengeToken: String
)
internal class SDKInitResponse(
    responseObject: SDKInitResponseData,
    status: Status
): ObjectResponse<SDKInitResponseData>(responseObject, status)
internal class SDKInitResponseData(
    @SerializedName("attributes") val attributes: SDKInitResponseDataAttributes
)
internal class SDKInitResponseDataAttributes(
    val responseToken: String
)

internal class DocumentSubmitRequest(data: DocumentSubmitRequestData): ObjectRequest<DocumentSubmitRequestData>(data)
internal class DocumentSubmitRequestData(
    processId: String,
    @SerializedName("data") val data: String,
    @SerializedName("resubmit") val resubmit: Boolean,
    @SerializedName("documents") val documents: List<DocumentSubmitFile>
): VerificationProcessRequestData(processId)

internal data class DocumentSubmitFile(
    @SerializedName("filename") val filename: String,
    @SerializedName("type") val type: DocumentSubmitFileType,
    @SerializedName("side") val side: DocumentFileSide?,
    @SerializedName("originalDocumentId") val originalDocumentId: String?
)

internal class DocumentSubmitResponse(status: Status): StatusResponse(status)

internal class PresenceCheckRequest(processId: String): ObjectRequest<VerificationProcessRequestData>(
    VerificationProcessRequestData((processId))
)
internal class PresenceCheckResponse(responseObject: PresenceCheckResponseData, status: Status): ObjectResponse<PresenceCheckResponseData>(responseObject, status)
internal class PresenceCheckResponseData(
    @SerializedName("sessionAttributes") val attributes: Map<String, Any>
)

internal class PresenceCheckSubmitRequest(processId: String): ObjectRequest<VerificationProcessRequestData>(
    VerificationProcessRequestData(processId)
)

internal class VerificationResendOtpRequest(processId: String): ObjectRequest<VerificationProcessRequestData>(
    VerificationProcessRequestData((processId))
)
internal class ResendOtpResponse(status: Status): StatusResponse(status)

internal class VerifyOtpRequest(processId: String, otp: String): ObjectRequest<VerifyOtpRequestData>(
    VerifyOtpRequestData(processId, otp)
)
internal class VerifyOtpRequestData(
    @SerializedName("processId") val processId: String,
    @SerializedName("otpCode") val otp: String
)
internal class VerifyOtpResponse(
    responseObject: VerifyOtpResponseData,
    status: Status
): ObjectResponse<VerifyOtpResponseData>(responseObject, status)
internal class VerifyOtpResponseData(
    @SerializedName("processId") val processId: String,
    @SerializedName("onboardingStatus") val onboardingStatus: OnboardingStatus,
    @SerializedName("verified") val verified: Boolean,
    @SerializedName("expired") val expired: Boolean,
    @SerializedName("remainingAttempts") val remainingAttempts: Int
)

internal class SDKInitResponseDataAttributesDeserializer: JsonDeserializer<SDKInitResponseDataAttributes> {

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): SDKInitResponseDataAttributes {
        // This is pretty big oversimplification, but in general, we expect 1 string property with an unknown key (property name).
        // If this wont fit the customer needs, we gonna need to provide this API as generic or make it provider-based for
        // different SDK providers.
        val firstEntry = json.asJsonObject.asMap().entries.firstOrNull() ?: throw JsonParseException("No attribute in the response SDKInitResponseDataAttributes")
        WDOLogger.d("Using first SDKInitResponseDataAttributes attribute named ${firstEntry.key}")
        return SDKInitResponseDataAttributes(firstEntry.value.asString)
    }
}
