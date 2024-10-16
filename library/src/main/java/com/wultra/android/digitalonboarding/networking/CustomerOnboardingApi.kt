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

package com.wultra.android.digitalonboarding.networking

import android.content.Context
import com.wultra.android.digitalonboarding.Utils
import com.wultra.android.digitalonboarding.networking.model.CancelOnboardingRequest
import com.wultra.android.digitalonboarding.networking.model.GetStatusRequest
import com.wultra.android.digitalonboarding.networking.model.GetStatusResponse
import com.wultra.android.digitalonboarding.networking.model.OTPDetailRequest
import com.wultra.android.digitalonboarding.networking.model.OTPDetailRequestData
import com.wultra.android.digitalonboarding.networking.model.OTPDetailResponse
import com.wultra.android.digitalonboarding.networking.model.OTPDetailType
import com.wultra.android.digitalonboarding.networking.model.ResendOtpRequest
import com.wultra.android.digitalonboarding.networking.model.StartOnboardingRequest
import com.wultra.android.digitalonboarding.networking.model.StartOnboardingResponse
import com.wultra.android.powerauth.networking.Api
import com.wultra.android.powerauth.networking.E2EEConfiguration
import com.wultra.android.powerauth.networking.EndpointBasic
import com.wultra.android.powerauth.networking.IApiCallResponseListener
import com.wultra.android.powerauth.networking.data.StatusResponse
import io.getlime.security.powerauth.sdk.PowerAuthSDK
import okhttp3.OkHttpClient

/**
 * Class that provides all necessary communication for Identity Onboarding (PowerAuth activation via user information such as client id and birthdate).
 *
 * @property powerAuthSDK Properly configured PowerAuth SDK
 * @constructor Creates API based on the provided values
 *
 * @param identityServerUrl URL address of the enrollment-onboarding-server
 * @param okHttpClient Configured okHttp object
 * @param appContext Application context
 */
internal class CustomerOnboardingApi(
    identityServerUrl: String,
    okHttpClient: OkHttpClient,
    private val powerAuthSDK: PowerAuthSDK,
    appContext: Context
) : Api(identityServerUrl, okHttpClient, powerAuthSDK, Utils.defaultGsonBuilder(), appContext) {

    companion object {
        private fun <T> startEndpoint() = EndpointBasic<StartOnboardingRequest<T>, StartOnboardingResponse>("api/onboarding/start", E2EEConfiguration.APPLICATION_SCOPE)
        private val cancelEndpoint = EndpointBasic<CancelOnboardingRequest, StatusResponse>("api/onboarding/cleanup", E2EEConfiguration.APPLICATION_SCOPE)
        private val statusEndpoint = EndpointBasic<GetStatusRequest, GetStatusResponse>("api/onboarding/status", E2EEConfiguration.APPLICATION_SCOPE)
        private val resendOtpEndpoint = EndpointBasic<ResendOtpRequest, StatusResponse>("api/onboarding/otp/resend", E2EEConfiguration.APPLICATION_SCOPE)
        val getOtpEndpoint = EndpointBasic<OTPDetailRequest, OTPDetailResponse>("api/onboarding/otp/detail", E2EEConfiguration.APPLICATION_SCOPE)
    }

    /**
     * Starts the Identity Onboarding process.
     *
     * Encrypted with the ECIES application scope.
     *
     * @param T Type that represents user credentials.
     * @param credentials Custom credentials object for user authentication.
     * @param listener Result listener
     */
    fun <T> start(credentials: T, listener: IApiCallResponseListener<StartOnboardingResponse>) {
        post(
            StartOnboardingRequest(credentials),
            startEndpoint(),
            null,
            null,
            listener
        )
    }

    /**
     * Cancel the Identity Onboarding process.
     *
     * Encrypted with the ECIES application scope.
     *
     * @param processId ID of the Identity Onboarding process
     * @param listener Result listener
     */
    fun cancel(processId: String, listener: IApiCallResponseListener<StatusResponse>) {
        post(
            CancelOnboardingRequest(processId),
            cancelEndpoint,
            null,
            null,
            listener
        )
    }

    /**
     * Retrieves status of the Identity Onboarding.
     *
     * Encrypted with the ECIES application scope.
     *
     * @param processId ID of the Identity Onboarding process
     * @param listener Result listener
     */
    fun getStatus(processId: String, listener: IApiCallResponseListener<GetStatusResponse>) {
        post(
            GetStatusRequest(processId),
            statusEndpoint,
            null,
            null,
            listener
        )
    }

    /**
     * Resends the OTP for users convenience (for example when SMS was not received by the user).
     *
     * Note that there will be some frequency limit implemented by the server. Default is 30 seconds
     * but we advise to consult this with the backend developers.
     *
     * Encrypted with the ECIES application scope.
     *
     * @param processId ID of the Identity Onboarding process
     * @param listener Result listener
     */
    fun resendOtp(processId: String, listener: IApiCallResponseListener<StatusResponse>) {
        post(
            ResendOtpRequest(processId),
            resendOtpEndpoint,
            null,
            null,
            listener
        )
    }

    /**
     * Retrieves OTP needed for Onboarding Process.
     *
     * **Note that this method is available only in demo Wultra implementation.**
     *
     * Encrypted with the ECIES activation scope.
     *
     * @param processId ID of the Identity Onboarding process
     * @param listener Result listener
     */
    fun getOtp(processId: String, listener: IApiCallResponseListener<OTPDetailResponse>) {
        post(
            OTPDetailRequest(OTPDetailRequestData(processId, OTPDetailType.ACTIVATION)),
            getOtpEndpoint,
            null,
            null,
            listener
        )
    }
}
