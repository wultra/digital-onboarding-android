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

package com.wultra.android.digitalonboarding

import android.content.Context
import com.wultra.android.digitalonboarding.VerificationStatusNextStep.Value
import com.wultra.android.digitalonboarding.networking.ConsentApproveResponse
import com.wultra.android.digitalonboarding.networking.ConsentResponse
import com.wultra.android.digitalonboarding.networking.CustomerOnboardingApi
import com.wultra.android.digitalonboarding.networking.CustomerVerificationApi
import com.wultra.android.digitalonboarding.networking.Document
import com.wultra.android.digitalonboarding.networking.DocumentStatus
import com.wultra.android.digitalonboarding.networking.DocumentSubmitResponse
import com.wultra.android.digitalonboarding.networking.DocumentsStatusResponse
import com.wultra.android.digitalonboarding.networking.IdentityVerificationStatus
import com.wultra.android.digitalonboarding.networking.OTPDetailResponse
import com.wultra.android.digitalonboarding.networking.PresenceCheckResponse
import com.wultra.android.digitalonboarding.networking.ResendOtpResponse
import com.wultra.android.digitalonboarding.networking.SDKInitResponse
import com.wultra.android.digitalonboarding.networking.VerificationStatusResponse
import com.wultra.android.digitalonboarding.networking.VerifyOtpResponse
import com.wultra.android.powerauth.networking.IApiCallResponseListener
import com.wultra.android.powerauth.networking.data.StatusResponse
import com.wultra.android.powerauth.networking.error.ApiError
import com.wultra.android.powerauth.networking.error.ApiErrorCode
import io.getlime.security.powerauth.core.ActivationStatus
import io.getlime.security.powerauth.networking.response.IActivationStatusListener
import io.getlime.security.powerauth.sdk.PowerAuthSDK
import okhttp3.OkHttpClient
import java.time.Duration

/**
 * Digital Onboarding Verification Service
 *
 * @property appContext Application context
 * @property powerAuthSDK Configured PowerAuthSDK instance. This instance needs to be with valid activation otherwise you'll get errors.
 * @constructor Creates the instance
 *
 * @param identityServerUrl Base URL for service requests. Usually ending with `enrollment-onboarding-server`.
 * @param okHttpClient HTTP client for server communication.
 */
class VerificationService(
    identityServerUrl: String,
    okHttpClient: OkHttpClient,
    private val appContext: Context,
    private val powerAuthSDK: PowerAuthSDK
) {

    /**
     * Accept language for the outgoing requests headers.
     * Default value is "en".
     *
     * Standard RFC "Accept-Language" https://tools.ietf.org/html/rfc7231#section-5.3.5
     * Response texts are based on this setting. For example when "de" is set, server
     * will return error texts and other in german (if available).
     */
    var acceptLanguage: String
        set(value) { api.acceptLanguage = value }
        get() { return api.acceptLanguage }

    /** Listener that will be notified about PowerAuth and Activation changes */
    var listener: VerificationServiceListener? = null

    /** PRIVATE PROPERTIES & CLASSES */

    private var lastStatus: VerificationStatusResponse? = null
    private val api = CustomerVerificationApi(identityServerUrl, okHttpClient, powerAuthSDK, appContext)
    private val onboardingApi = CustomerOnboardingApi(identityServerUrl, okHttpClient, powerAuthSDK, appContext)
    private val storage = Storage(appContext, "wdo-verif-encrypted")
    private val storageCacheKey = "wdocp_${powerAuthSDK.configuration.instanceId}"
    private var cachedProcess: VerificationScanProcess?
        get() = storage.getValue(storageCacheKey)?.let { VerificationScanProcess(it) }
        set(value) = storage.setValue(storageCacheKey, value?.dataForCache())

    init {
        if (!powerAuthSDK.hasValidActivation()) {
            cachedProcess = null
        }
    }

    /** Time in seconds that user needs to wait between OTP resend calls */
    fun otpResendPeriodInSeconds(): Long? {
        val period = lastStatus?.responseObject?.config?.otpResendPeriod ?: return null
        return try {
            Duration.parse(period).seconds
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Status of the verification.
     *
     * @param callback Callback with the result.
     */
    fun status(callback: (Result<Success>) -> Unit) {
        api.getStatus(object: IApiCallResponseListener<VerificationStatusResponse> {
            override fun onSuccess(result: VerificationStatusResponse) {
                when (result.responseObject.status) {
                    IdentityVerificationStatus.FAILED, IdentityVerificationStatus.REJECTED, IdentityVerificationStatus.NOT_INITIALIZED, IdentityVerificationStatus.ACCEPTED -> {
                        cachedProcess = null
                    }
                    else -> {
                        // nothing
                    }
                }

                lastStatus = result

                val nextStep = VerificationStatusNextStep.fromStatusResponse(result.responseObject)
                when (nextStep.value) {
                    Value.INTRO -> {
                        markCompleted(VerificationStateIntroData, callback)
                    }
                    Value.DOCUMENT_SCAN -> {
                        api.documentsStatus(
                            result.responseObject.processId,
                            object : IApiCallResponseListener<DocumentsStatusResponse> {
                                override fun onSuccess(result: DocumentsStatusResponse) {
                                    val documents = result.responseObject.documents

                                    val cachedProcess = this@VerificationService.cachedProcess

                                    if (cachedProcess != null) {
                                        cachedProcess.feed(documents)
                                        if (documents.any { it.action() == DocumentAction.ERROR } || documents.any { !it.errors.isNullOrEmpty() }) {
                                            markCompleted(VerificationStateScanDocumentData(cachedProcess), callback)
                                        } else if (documents.all { it.action() == DocumentAction.PROCEED }) {
                                            markCompleted(VerificationStateScanDocumentData(cachedProcess), callback)
                                        } else if (documents.any { it.action() == DocumentAction.WAIT }) {
                                            // TODO: really verification?
                                            markCompleted(VerificationStateProcessingData(ProcessingItem.DOCUMENT_VERIFICATION), callback)
                                        } else if (documents.isEmpty()) {
                                            markCompleted(VerificationStateScanDocumentData(cachedProcess), callback)
                                        } else {
                                            // TODO: is this ok?
                                            markCompleted(VerificationStateFailedData, callback)
                                        }
                                    } else {
                                        if (documents.isEmpty()) {
                                            markCompleted(VerificationStateDocumentsToScanSelectData, callback)
                                        } else {
                                            markCompleted(VerificationStateFailedData, callback)
                                        }
                                    }
                                }

                                override fun onFailure(error: ApiError) {
                                    markCompleted(error, callback)
                                }
                            }
                        )
                    }
                    Value.PRESENCE_CHECK -> {
                        markCompleted(VerificationStatePresenceCheckData, callback)
                    }
                    Value.STATUS_CHECK -> {
                        markCompleted(VerificationStateProcessingData(ProcessingItem.from(nextStep.statusCheckReason ?: VerificationStatusNextStep.StatusCheckReason.UNKNOWN)), callback)
                    }
                    Value.OTP -> {
                        markCompleted(VerificationStateOtpData(null), callback)
                    }
                    Value.FAILED -> {
                        markCompleted(VerificationStateFailedData, callback)
                    }
                    Value.REJECTED -> {
                        markCompleted(VerificationStateEndstateData(EndstateReason.REJECTED), callback)
                    }
                    Value.SUCCESS -> {
                        markCompleted(VerificationStateSuccessData, callback)
                    }
                }
            }

            override fun onFailure(error: ApiError) {
                lastStatus = null
                markCompleted(error, callback)
            }
        })
    }

    /**
     * Returns consent text for user to approve.
     *
     * @param callback Callback with the result.
     */
    fun consentGet(callback: (Result<Success>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.getConsentText(
            processId,
            object : IApiCallResponseListener<ConsentResponse> {
                override fun onSuccess(result: ConsentResponse) {
                    markCompleted(
                        VerificationStateConsentData(result.responseObject.consentText),
                        callback,
                    )
                }

                override fun onFailure(error: ApiError) {
                    markCompleted(error, callback)
                }
            },
        )
    }

    /**
     * Approves the consent for this process and starts the activation.
     *
     * @param callback Callback with the result.
     */
    fun consentApprove(callback: (Result<Success>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.resolveConsent(
            processId,
            true,
            object : IApiCallResponseListener<ConsentApproveResponse> {
                override fun onSuccess(result: ConsentApproveResponse) {
                    api.start(
                        processId,
                        object : IApiCallResponseListener<StatusResponse> {
                            override fun onSuccess(result: StatusResponse) {
                                markCompleted(VerificationStateDocumentsToScanSelectData, callback)
                            }

                            override fun onFailure(error: ApiError) {
                                markCompleted(error, callback)
                            }
                        },
                    )
                }

                override fun onFailure(error: ApiError) {
                    markCompleted(error, callback)
                }
            },
        )
    }

    /**
     * Returns a token for the document scanning SDK, when needed.
     *
     * @param challenge SDK generated challenge for the server
     * @param callback Callback with the token for the SDK.
     */
    fun documentsInitSDK(challenge: String, callback: (Result<String>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.initScanSDK(
            processId,
            challenge,
            object : IApiCallResponseListener<SDKInitResponse> {
                override fun onSuccess(result: SDKInitResponse) {
                    markCompleted(result.responseObject.attributes.responseToken, callback)
                }

                override fun onFailure(error: ApiError) {
                    markCompleted(error, callback)
                }
            },
        )
    }

    /**
     * Set document types to scan
     *
     * @param types Types of documents to scan.
     * @param callback Callback with the result.
     */
    fun documentsSetSelectedTypes(types: List<DocumentType>, callback: (Result<Success>) -> Unit) {
        // TODO: We should verify that we're in the expected state here
        val process = VerificationScanProcess(types)
        cachedProcess = process
        markCompleted(VerificationStateScanDocumentData(process), callback)
    }

    /**
     * Upload document files to the server.
     *
     * @param files Document files.
     * @param progressCallback Upload progress callback. (Not working at the moment)
     * @param callback Callback with the result.
     */
    fun documentsSubmit(files: List<DocumentFile>, progressCallback: (Double) -> Unit, callback: (Result<Success>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        // TODO: different thread?
        // TODO: progress callback
        try {
            val requestData = DocumentPayloadBuilder.build(processId, files)
            api.submitDocuments(
                requestData,
                object : IApiCallResponseListener<DocumentSubmitResponse> {
                    override fun onSuccess(result: DocumentSubmitResponse) {
                        markCompleted(
                            VerificationStateProcessingData(ProcessingItem.DOCUMENT_UPLOAD),
                            callback
                        )
                    }

                    override fun onFailure(error: ApiError) {
                        markCompleted(error, callback)
                    }
                }
            )
        } catch (e: Throwable) {
            markCompleted(ApiError(e), callback)
        }
    }

    /**
     * Starts presence check. This returns attributes that are needed to start presence check SDK.
     *
     * @param callback Callback with the result.
     */
    fun presenceCheckInit(callback: (Result<Map<String, Any>>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.presenceCheckInit(
            processId,
            object : IApiCallResponseListener<PresenceCheckResponse> {
                override fun onSuccess(result: PresenceCheckResponse) {
                    markCompleted(result.responseObject.attributes, callback)
                }

                override fun onFailure(error: ApiError) {
                    markCompleted(error, callback)
                }
            }
        )
    }

    /**
     * Marks that presence check was performed.
     *
     * @param callback Callback with the result.
     */
    fun presenceCheckSubmit(callback: (Result<Success>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.presenceCheckSubmit(
            processId,
            object : IApiCallResponseListener<StatusResponse> {
                override fun onSuccess(result: StatusResponse) {
                    markCompleted(
                        VerificationStateProcessingData(ProcessingItem.VERIFYING_PRESENCE),
                        callback
                    )
                }

                override fun onFailure(error: ApiError) {
                    markCompleted(error, callback)
                }
            }
        )
    }

    /**
     * Restarts verification. When sucessfully called, intro screen should be presented.
     *
     * @param callback Callback with the result.
     */
    fun restartVerification(callback: (Result<Success>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.cleanup(
            processId,
            object : IApiCallResponseListener<StatusResponse> {
                override fun onSuccess(result: StatusResponse) {
                    markCompleted(VerificationStateIntroData, callback)
                }

                override fun onFailure(error: ApiError) {
                    markCompleted(error, callback)
                }
            }
        )
    }

    /**
     * Cancels the whole activation/verification. After this it's no longer call any API endpoint and PowerAuth activation should be removed.
     *
     * @param callback Callback with the result.
     */
    fun cancelWholeProcess(callback: (Result<Unit>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        onboardingApi.cancel(
            processId,
            object : IApiCallResponseListener<StatusResponse> {
                override fun onSuccess(result: StatusResponse) {
                    callback(Result.success(Unit))
                }

                override fun onFailure(error: ApiError) {
                    callback(Result.failure(error.toException()))
                }
            }
        )
    }

    /**
     * Verify OTP that user entered as a last step of the verification.
     *
     * @param otp User entered OTP.
     * @param callback Callback with the result.
     */
    fun verifyOTP(otp: String, callback: (Result<Success>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.verifyOtp(
            processId,
            otp,
            object : IApiCallResponseListener<VerifyOtpResponse> {
                override fun onSuccess(result: VerifyOtpResponse) {
                    if (result.responseObject.verified) {
                        markCompleted(
                            VerificationStateProcessingData(ProcessingItem.OTHER),
                            callback,
                        )
                    } else {
                        if (result.responseObject.remainingAttempts > 0 && !result.responseObject.expired) {
                            markCompleted(
                                VerificationStateOtpData(result.responseObject.remainingAttempts),
                                callback,
                            )
                        } else {
                            // TODO: move closer to iOS implementation: self.markCompleted(.failure(.init(.init(reason: .wdo_verification_otpFailed))), completion)
                            markCompleted(ApiError(Exception("OTP_FAILED")), callback)
                        }
                    }
                }

                override fun onFailure(error: ApiError) {
                    markCompleted(error, callback)
                }
            },
        )
    }

    /**
     * Requests OTP resend.
     *
     * @param callback Callback with the result.
     */
    fun resendOTP(callback: (Result<Unit>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.resendOtp(
            processId,
            object : IApiCallResponseListener<ResendOtpResponse> {
                override fun onSuccess(result: ResendOtpResponse) {
                    callback(Result.success(Unit))
                }

                override fun onFailure(error: ApiError) {
                    markCompleted(error, callback)
                }
            },
        )
    }

    /**
     * Demo endpoint available only in Wultra Demo systems.
     *
     * @param callback Callback with the result.
     */
    fun getOTP(callback: (Result<String>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.getOtp(
            processId,
            object : IApiCallResponseListener<OTPDetailResponse> {
                override fun onSuccess(result: OTPDetailResponse) {
                    callback(Result.success(result.responseObject.otpCode))
                }

                override fun onFailure(error: ApiError) {
                    callback(Result.failure(Fail(error)))
                }
            }
        )
    }

    // Public Helper Classes

    /**
     * Success result with next state that should be presented
     *
     * @property state State of the verification for app to display
     */
    data class Success(val state: VerificationStateData)

    /**
     * Error result with cause of the error and state that should be presented (optional).
     *
     * Note that state will be filled only when the error indicates state change.
     *
     * @constructor
     *
     * @param cause Cause of the error.
     */
    class Fail(cause: ApiError): Exception(cause.toException()) {
        /** State of the verification for app to display */
        val state: VerificationStateData? = when (cause.error) {
            ApiErrorCode.ONBOARDING_FAILED -> VerificationStateEndstateData(EndstateReason.OTHER)
            ApiErrorCode.IDENTITY_VERIFICATION_FAILED -> VerificationStateFailedData
            ApiErrorCode.ONBOARDING_PROCESS_LIMIT_REACHED -> VerificationStateEndstateData(EndstateReason.LIMIT_REACHED)
            ApiErrorCode.IDENTITY_PRESENCE_CHECK_LIMIT_REACHED, ApiErrorCode.IDENTITY_VERIFICATION_LIMIT_REACHED -> VerificationStateFailedData
            else -> null
        }
    }
    /** PowerAuth instance cannot start the activation. */
    object ActivationNotActiveException: Exception("PowerAuth instance cannot start the activation.")
    /** Verification status needs to be fetched first */
    object ActivationMissingStatusException: Exception("Verification status needs to be fetched first.")

    // Private helper methods

    private fun <T>guardProcessId(callback: (Result<T>) -> Unit): String? {

        val processId = lastStatus?.responseObject?.processId
        if (processId == null) {
            markCompleted(Fail(ApiError(ActivationMissingStatusException)), callback)
            return null
        }
        return processId
    }

    private fun <T>markCompleted(error: ApiError, callback: (Result<T>) -> Unit) {
        if (!error.isOffline() || error.error == ApiErrorCode.POWERAUTH_AUTH_FAIL) {

            powerAuthSDK.fetchActivationStatusWithCallback(
                appContext,
                object : IActivationStatusListener {
                    override fun onActivationStatusSucceed(status: ActivationStatus?) {
                        if (status?.state != ActivationStatus.State_Active) {
                            listener?.powerAuthActivationStatusChanged(this@VerificationService, status)
                        }
                        markCompleted(Fail(ApiError(ActivationNotActiveException)), callback)
                    }

                    override fun onActivationStatusFailed(t: Throwable) {
                        markCompleted(Fail(ApiError(t)), callback)
                    }
                }
            )
        } else {
            markCompleted(Fail(error), callback)
        }
    }

    private fun <T>markCompleted(result: T, callback: (Result<T>) -> Unit) {
        callback(Result.success(result))
    }

    private fun markCompleted(state: VerificationStateData, callback: (Result<Success>) -> Unit) {
        val result = Success(state)
        listener?.verificationStatusChanged(this, state)
        callback(Result.success(result))
    }

    private fun <T>markCompleted(fail: Fail, callback: (Result<T>) -> Unit) {
        if (fail.state != null) {
            listener?.verificationStatusChanged(this, fail.state)
        }

        callback(Result.failure(fail))
    }
}

/**
 * Listener of the Onboarding Verification Service that can listen on Verification Status and PowerAuth Status changes.
 *
 */
interface VerificationServiceListener {

    /**
     * Called when PowerAuth activation status changed.
     *
     * Note that this happens only when error is returned in some of the Verification endpoints and this error indicates PowerAuth status change.
     *
     * @param service Origin service
     * @param status Status
     */
    fun powerAuthActivationStatusChanged(service: VerificationService, status: ActivationStatus?)

    /**
     * Called when state of the verification has changed.
     *
     * @param service Origin service
     * @param status Status
     */
    fun verificationStatusChanged(service: VerificationService, status: VerificationStateData)
}

internal enum class DocumentAction {
    PROCEED,
    ERROR,
    WAIT
}

internal fun Document.action(): DocumentAction {
    return when (status) {
        DocumentStatus.ACCEPTED -> DocumentAction.PROCEED
        DocumentStatus.UPLOAD_IN_PROGRESS, DocumentStatus.IN_PROGRESS, DocumentStatus.VERIFICATION_PENDING, DocumentStatus.VERIFICATION_IN_PROGRESS -> DocumentAction.WAIT
        DocumentStatus.REJECTED, DocumentStatus.FAILED -> DocumentAction.ERROR
    }
}
