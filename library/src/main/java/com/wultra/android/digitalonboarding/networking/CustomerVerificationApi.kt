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
import com.wultra.android.digitalonboarding.Utils
import com.wultra.android.powerauth.networking.*
import com.wultra.android.powerauth.networking.data.StatusResponse
import io.getlime.security.powerauth.sdk.PowerAuthAuthentication
import io.getlime.security.powerauth.sdk.PowerAuthSDK
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Class for all necessary communication for Identity Verification (After PowerAuth was enrolled with verification pending)
 *
 * @property powerAuthSDK Properly configured PowerAuth SDK
 * @property appContext Application context
 * @constructor Creates API based on the provided values
 *
 * @param identityServerUrl URL address of the enrollment-onboarding-server
 * @param okHttpClient Configured okHttp object
 */
internal class CustomerVerificationApi(
    identityServerUrl: String,
    okHttpClient: OkHttpClient,
    private val powerAuthSDK: PowerAuthSDK,
    private val appContext: Context
)
: Api(identityServerUrl, okHttpClient, powerAuthSDK, Utils.defaultGsonBuilder(), appContext) {

    companion object {
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
        private val otpVerifyEndpoint = EndpointBasic<VerifyOtpRequest, VerifyOtpResponse>("api/identity/otp/verify")
    }

    /**
     * Retrieves status of the Identity Verification status
     *
     * @param listener Result listener
     */
    fun getStatus(listener: IApiCallResponseListener<VerificationStatusResponse>) {
        // TODO: apiCoroutineScope.launch ?
        post(
            EmptyRequest,
            statusEndpoint,
            null,
            null,
            null,
            listener
        )
    }

    /**
     * Starts Identity Verification process.
     *
     * @param processId ID of the process.
     * @param listener Result listener.
     */
    fun start(processId: String, listener: IApiCallResponseListener<StatusResponse>) {
        post(
            StartRequest(processId),
            startEndpoint,
            PowerAuthAuthentication.possession(),
            null,
            null,
            null,
            listener
        )
    }

    /**
     * Cancel and clean the current verification process. Acts as a "reset".
     *
     * @param processId ID of the process.
     * @param listener Result listener.
     */
    fun cleanup(processId: String, listener: IApiCallResponseListener<StatusResponse>) {
        post(
            CancelRequest(processId),
            cancelEndpoint,
            PowerAuthAuthentication.possession(),
            null,
            null,
            null,
            listener
        )
    }

    /**
     * Retrieves a consent text (usually HTML) for user to approve/reject.
     *
     * @param processId ID of the process.
     * @param listener Result listener.
     */
    fun getConsentText(processId: String, listener: IApiCallResponseListener<ConsentResponse>) {
        post(
            ConsentRequest(processId),
            consentTextEndpoint,
            null,
            null,
            null,
            listener
        )
    }

    /**
     * Approves or rejects privacy consent.
     *
     * @param processId ID of the process.
     * @param approved If the user approved the consent.
     * @param listener Result listener.
     */
    fun resolveConsent(processId: String, approved: Boolean, listener: IApiCallResponseListener<ConsentApproveResponse>) {
        post(
            ConsentApproveRequest(processId, approved),
            consentApproveEndpoint,
            PowerAuthAuthentication.possession(),
            null,
            null,
            null,
            listener
        )
    }

    /**
     * Provides necessary data to init scan SDK (like ZenID).
     *
     * @param processId ID of the process.
     * @param challenge Challenge that the SDK provided.
     * @param listener Result listener.
     */
    fun initScanSDK(processId: String, challenge: String, listener: IApiCallResponseListener<SDKInitResponse>) {
        post(
            SDKInitRequest(processId, challenge),
            documentSdkInitEndpoint,
            PowerAuthAuthentication.possession(),
            null,
            powerAuthSDK.getEciesEncryptorForActivationScope(appContext),
            null,
            listener
        )
    }

    /**
     * Submits documents necessary for identity verification (like photos of ID or passport).
     *
     * Encrypted with the ECIES activation scope.
     *
     * @param data Data to be send.
     *             You can use `WDODocumentPayloadBuilder.build` for easier use.
     * @param listener Result listener.
     */
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

    /**
     * Asks for status of already uploaded documents.
     *
     * @param processId ID of the process.
     * @param listener Result listener.
     */
    fun documentsStatus(processId: String, listener: IApiCallResponseListener<DocumentsStatusResponse>) {
        post(
            DocumentsStatusRequest(processId),
            docsStatusEndpoint,
            null,
            null,
            null,
            listener
        )
    }

    /**
     * Starts presence check process (that user is actually physically present).
     *
     * Encrypted with the ECIES activation scope.
     *
     * @param processId ID of the process.
     * @param listener Result listener.
     */
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

    /**
     * Confirms presence check is done on the device.
     *
     * @param processId ID of the process.
     * @param listener Result listener.
     */
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

    /**
     * OTP resend  in case that the user didn't received it.
     *
     * @param processId ID of the process.
     * @param listener Result listener.
     */
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

    /**
     * OTP verification during identification of the user.
     *
     * @param processId ID of the process.
     * @param otp OTP that user received.
     * @param listener Result listener.
     */
    fun verifyOtp(processId: String, otp: String, listener: IApiCallResponseListener<VerifyOtpResponse>) {
        post(
            VerifyOtpRequest(processId, otp),
            otpVerifyEndpoint,
            null,
            powerAuthSDK.getEciesEncryptorForActivationScope(appContext),
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
            OTPDetailRequest(OTPDetailRequestData(processId, OTPDetailType.USER_VERIFICATION)),
            CustomerOnboardingApi.getOtpEndpoint,
            null,
            powerAuthSDK.eciesEncryptorForApplicationScope,
            null,
            listener
        )
    }
}
