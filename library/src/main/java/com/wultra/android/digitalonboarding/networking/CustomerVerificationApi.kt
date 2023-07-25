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
import com.google.gson.GsonBuilder
import com.wultra.android.powerauth.networking.*
import com.wultra.android.powerauth.networking.data.StatusResponse
import io.getlime.security.powerauth.sdk.PowerAuthAuthentication
import io.getlime.security.powerauth.sdk.PowerAuthSDK
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * CustomerVerificationApi
 */
class CustomerVerificationApi(
    identityServerUrl: String,
    okHttpClient: OkHttpClient,
    private val powerAuthSDK: PowerAuthSDK,
    private val appContext: Context
)
: Api(identityServerUrl, okHttpClient, powerAuthSDK, getGson(), appContext) {

    companion object {
        private fun getGson() = GsonBuilder()
        private val statusEndpoint = EndpointSignedWithToken<EmptyRequest, VerificationStatusResponse>("api/identity/status", "possession_universal")
        private val startEndpoint = EndpointSigned<StartRequest, StatusResponse>("api/identity/init", "/api/identity/init")
        private val cancelEndpoint = EndpointSigned<CancelRequest, StatusResponse>("api/identity/cleanup", "/api/identity/cleanup")
        private val consentTextEndpoint = EndpointSignedWithToken<ConsentRequest, ConsentResponse>("/api/identity/consent/text", "possession_universal")
        private val consentApproveEndpoint = EndpointSigned<ConsentApproveRequest, ConsentApproveResponse>("/api/identity/consent/approve", "/api/identity/consent/approve")
        private val docsStatusEndpoint = EndpointSignedWithToken<DocumentsStatusRequest, DocumentsStatusResponse>("api/identity/document/status", "possession_universal")
        private val documentSdkInitEndpoint = EndpointSigned<SDKInitRequest, SDKInitResponse>("/api/identity/document/init-sdk", "/api/identity/document/init-sdk")
        private val submitDocsEndpoint = EndpointSignedWithToken<DocumentSubmitRequest, DocumentSubmitResponse>("api/identity/document/submit", "possession_universal")
        private val presenceCheckEndpoint = EndpointSigned<PresenceCheckRequest, PresenceCheckResponse>("api/identity/presence-check/init", "/api/identity/presence-check/init")
        private val presenceCheckSubmitEndpoint = EndpointSigned<PresenceCheckSubmitRequest, StatusResponse>("api/identity/presence-check/submit", "/api/identity/presence-check/submit")
        private val resendOtpEndpoint = EndpointSigned<VerificationResendOtpRequest, ResendOtpResponse>("api/identity/otp/resend", "/api/identity/otp/resend")
        private val resendVerifyEndpoint = EndpointBasic<VerifyOtpRequest, VerifyOtpResponse>("api/identity/otp/verify")
    }

    fun status(listener: IApiCallResponseListener<VerificationStatusResponse>) {
        // TODO: apiCoroutineScope.launch ?
        post(EmptyRequest, statusEndpoint, null, null, null, listener)
    }

    fun start(processId: String, listener: IApiCallResponseListener<StatusResponse>) {
        val auth = PowerAuthAuthentication.possession()
        post(StartRequest(processId), startEndpoint, auth, null, null, null, listener)
    }

    fun cancel(processId: String, listener: IApiCallResponseListener<StatusResponse>) {
        val auth = PowerAuthAuthentication.possession()
        post(CancelRequest(processId), cancelEndpoint, auth, null, null, null, listener)
    }

    fun consentText(processId: String, listener: IApiCallResponseListener<ConsentResponse>) {
        post(ConsentRequest(processId), consentTextEndpoint, null, null, null, listener)
    }

    fun approveConsent(processId: String, approved: Boolean, listener: IApiCallResponseListener<ConsentApproveResponse>) {
        val auth = PowerAuthAuthentication.possession()
        post(ConsentApproveRequest(processId, approved), consentApproveEndpoint, auth, null, null, null, listener)
    }

    fun documentsStatus(processId: String, listener: IApiCallResponseListener<DocumentsStatusResponse>) {
        post(DocumentsStatusRequest(processId), docsStatusEndpoint, null, null, null, listener)
    }

    fun documentSdkInit(processId: String, challenge: String, listener: IApiCallResponseListener<SDKInitResponse>) {
        val auth = PowerAuthAuthentication.possession()
        post(SDKInitRequest(processId, challenge), documentSdkInitEndpoint, auth, null, powerAuthSDK.getEciesEncryptorForActivationScope(appContext), null, listener)
    }

    fun submitDocuments(data: DocumentSubmitRequestData, listener: IApiCallResponseListener<DocumentSubmitResponse>) {
        post(
            DocumentSubmitRequest(data),
            submitDocsEndpoint,
            null,
            powerAuthSDK.getEciesEncryptorForActivationScope(appContext),
            object: OkHttpBuilderInterceptor {
                override fun intercept(builder: OkHttpClient.Builder) {
                    // document upload can take some time
                    builder.callTimeout(120, TimeUnit.SECONDS)
                    builder.connectTimeout(120, TimeUnit.SECONDS)
                    builder.readTimeout(120, TimeUnit.SECONDS)
                    builder.writeTimeout(120, TimeUnit.SECONDS)
                }
            },
            listener
        )
    }

    fun presenceCheckInit(processId: String, listener: IApiCallResponseListener<PresenceCheckResponse>) {
        post(
            PresenceCheckRequest(processId),
            presenceCheckEndpoint,
            PowerAuthAuthentication.possession(),
            null,
            powerAuthSDK.getEciesEncryptorForActivationScope(appContext),
            null,
            listener
        )
    }

    fun presenceCheckSubmit(processId: String, listener: IApiCallResponseListener<StatusResponse>) {
        post(
            PresenceCheckSubmitRequest(processId),
            presenceCheckSubmitEndpoint,
            PowerAuthAuthentication.possession(),
            null,
            null,
            null,
            listener
        )
    }

    fun resendOtp(processId: String, listener: IApiCallResponseListener<ResendOtpResponse>) {
        post(
            VerificationResendOtpRequest(processId),
            resendOtpEndpoint,
            PowerAuthAuthentication.possession(),
            null,
            null,
            null,
            listener
        )
    }

    fun verifyOtp(processId: String, otp: String, listener: IApiCallResponseListener<VerifyOtpResponse>) {
        post(
            VerifyOtpRequest(processId, otp),
            resendVerifyEndpoint,
            null,
            powerAuthSDK.getEciesEncryptorForActivationScope(appContext),
            null,
            listener
        )
    }

    // This endpoint only works for wultra enrollment server
    fun getOtp(processId: String, listener: IApiCallResponseListener<OTPDetailResponse>) {
        post(
            OTPDetailRequest(OTPDetailRequestData(processId, OTPDetailType.USER_VERIFICATION)),
            CustomerOnboardingApi.getOtpEndpoint,
            null,
            powerAuthSDK.eciesEncryptorForApplicationScope,
            null,
            listener
        )
    }
}
