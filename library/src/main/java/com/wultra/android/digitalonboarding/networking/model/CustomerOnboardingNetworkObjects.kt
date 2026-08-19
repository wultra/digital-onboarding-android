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

package com.wultra.android.digitalonboarding.networking.model

import com.google.gson.annotations.SerializedName
import com.wultra.android.powerauth.networking.data.ObjectRequest
import com.wultra.android.powerauth.networking.data.ObjectResponse

internal class StartOnboardingRequest<T>(identification: T, processType: String?): ObjectRequest<StartOnboardingRequestData<T>>(
    StartOnboardingRequestData(identification, processType)
)
internal class StartOnboardingRequestData<T>(
    @SerializedName("identification") val identification: T,
    @SerializedName("processType") val processType: String?
)
internal class StartOnboardingResponse(responseObject: ProcessResponseData, status: Status): ObjectResponse<ProcessResponseData>(responseObject, status)
internal enum class OnboardingStatus {
    @SerializedName("ACTIVATION_IN_PROGRESS") ACTIVATION_IN_PROGRESS,
    @SerializedName("VERIFICATION_IN_PROGRESS") VERIFICATION_IN_PROGRESS,
    @SerializedName("FAILED") FAILED,
    @SerializedName("FINISHED") FINISHED
}

internal class OTPDetailRequest(data: OTPDetailRequestData): ObjectRequest<OTPDetailRequestData>(data)
internal class OTPDetailRequestData(
    @SerializedName("processId") val processId: String,
    @SerializedName("otpType") val otpType: OTPDetailType
)
internal enum class OTPDetailType {
    @SerializedName("ACTIVATION") ACTIVATION,
    @SerializedName("USER_VERIFICATION") USER_VERIFICATION
}
internal class OTPDetailResponse(responseObject: OTPDetailResponseData, status: Status): ObjectResponse<OTPDetailResponseData>(responseObject, status)
internal class OTPDetailResponseData(
    @SerializedName("otpCode") val otpCode: String
)

internal class CancelOnboardingRequest(processId: String): ObjectRequest<ProcessRequestData>(
    ProcessRequestData(processId)
)

internal class ResendOtpRequest(processId: String): ObjectRequest<ProcessRequestData>(
    ProcessRequestData(processId)
)

internal class GetStatusRequest(processId: String): ObjectRequest<ProcessRequestData>(
    ProcessRequestData(processId)
)
internal class GetStatusResponse(responseObject: ProcessResponseData, status: Status): ObjectResponse<ProcessResponseData>(responseObject, status)

internal class ProcessRequestData(@SerializedName("processId") val processId: String)
internal class ProcessResponseData(
    @SerializedName("processId") val processId: String,
    @SerializedName("onboardingStatus") val onboardingStatus: OnboardingStatus,
    @SerializedName("activationCode") val activationCode: String?,
    /** Type of the activation used for this process. */
    @SerializedName("activationType") val activationType: ActivationType?
)

/** Type of activation linkage used for a process. */
internal enum class ActivationType {
    /** Activation is initialized by the onboarding server; the activation code is returned when the process starts. */
    @SerializedName("CODE") CODE,
    /** Activation is initialized by the SDK. */
    @SerializedName("IDENTITY") IDENTITY,
    /** An already existing (and active) PowerAuth activation is reused for this process (Re-KYC). */
    @SerializedName("ACTIVATION_ALREADY_EXISTS") ACTIVATION_ALREADY_EXISTS
}

/** Default additional data sent together with the Re-KYC start request when no custom value is provided. */
internal class DefaultReVerificationData(@SerializedName("source") val source: String = "re-verification")
internal class FinishActivationResponse(responseObject: FinishActivationResponseData, status: Status): ObjectResponse<FinishActivationResponseData>(responseObject, status)
internal class FinishActivationResponseData(
    @SerializedName("activationCode") val activationCode: String
)
