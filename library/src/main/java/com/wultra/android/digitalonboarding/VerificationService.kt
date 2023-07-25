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

/**
 * VerificationService
 */
class VerificationService(
    identityServerUrl: String,
    okHttpClient: OkHttpClient,
    private val appContext: Context,
    private val powerAuthSDK: PowerAuthSDK
) {

    var acceptLanguage: String
        set(value) { api.acceptLanguage = value }
        get() { return api.acceptLanguage }

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

    fun status(callback: (Result<Success>) -> Unit) {
        api.status(object: IApiCallResponseListener<VerificationStatusResponse> {
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
                        markCompleted(VerificationStatePresenceCheckData, callback)
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

    fun consentGet(callback: (Result<Success>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.consentText(
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

    fun consentApprove(callback: (Result<Success>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.approveConsent(
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

    fun documentsInitSDK(challenge: String, callback: (Result<String>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.documentSdkInit(
            processId,
            challenge,
            object : IApiCallResponseListener<SDKInitResponse> {
                override fun onSuccess(result: SDKInitResponse) {
                    markCompleted(challenge, callback)
                }

                override fun onFailure(error: ApiError) {
                    markCompleted(error, callback)
                }
            },
        )
    }

    fun documentsSetSelectedTypes(types: List<DocumentType>, callback: (Result<Success>) -> Unit) {
        val process = VerificationScanProcess(types)
        cachedProcess = process
        markCompleted(VerificationStateScanDocumentData(process), callback)
    }

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

    fun restartVerification(callback: (Result<Success>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.cancel(
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

    fun getOTP(callback: (Result<String>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.getOtp(
            processId,
            object : IApiCallResponseListener<OTPDetailResponse> {
                override fun onSuccess(result: OTPDetailResponse) {
                    callback(Result.success(result.responseObject.otpCode))
                }

                override fun onFailure(error: ApiError) {
                    markCompleted(error, callback)
                }
            }
        )
    }

    // Public Helper Classes

    data class Success(val state: VerificationStateData)
    class Fail(cause: ApiError): Exception(cause.toException()) {
        val state: VerificationStateData? = when (cause.error) {
            ApiErrorCode.ONBOARDING_FAILED -> VerificationStateEndstateData(EndstateReason.OTHER)
            ApiErrorCode.IDENTITY_VERIFICATION_FAILED -> VerificationStateFailedData
            ApiErrorCode.ONBOARDING_PROCESS_LIMIT_REACHED -> VerificationStateEndstateData(EndstateReason.LIMIT_REACHED)
            ApiErrorCode.IDENTITY_PRESENCE_CHECK_LIMIT_REACHED, ApiErrorCode.IDENTITY_VERIFICATION_LIMIT_REACHED -> VerificationStateFailedData
            else -> null
        }
    }
    object ActivationNotActiveException: Exception()
    object ActivationMissingStatusException: Exception()

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
                            markCompleted(Fail(ApiError(ActivationNotActiveException)), callback)
                        }
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

interface VerificationServiceListener {
    fun verificationStatusChanged(service: VerificationService, status: VerificationStateData)
    fun powerAuthActivationStatusChanged(service: VerificationService, status: ActivationStatus?)
}

enum class DocumentAction {
    PROCEED,
    ERROR,
    WAIT
}

fun Document.action(): DocumentAction {
    return when (status) {
        DocumentStatus.ACCEPTED -> DocumentAction.PROCEED
        DocumentStatus.UPLOAD_IN_PROGRESS, DocumentStatus.IN_PROGRESS, DocumentStatus.VERIFICATION_PENDING, DocumentStatus.VERIFICATION_IN_PROGRESS -> DocumentAction.WAIT
        DocumentStatus.REJECTED, DocumentStatus.FAILED -> DocumentAction.ERROR
    }
}
