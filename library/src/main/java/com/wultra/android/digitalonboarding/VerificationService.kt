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
import com.wultra.android.digitalonboarding.log.WDOLogger
import com.wultra.android.digitalonboarding.networking.model.ConsentApproveResponse
import com.wultra.android.digitalonboarding.networking.model.ConsentTextResponse
import com.wultra.android.digitalonboarding.networking.CustomerOnboardingApi
import com.wultra.android.digitalonboarding.networking.CustomerVerificationApi
import com.wultra.android.digitalonboarding.networking.model.Document
import com.wultra.android.digitalonboarding.networking.model.DocumentStatus
import com.wultra.android.digitalonboarding.networking.model.DocumentSubmitResponse
import com.wultra.android.digitalonboarding.networking.model.DocumentsStatusResponse
import com.wultra.android.digitalonboarding.networking.model.FinishActivationResponse
import com.wultra.android.digitalonboarding.networking.model.IdentityVerificationStatus
import com.wultra.android.digitalonboarding.networking.model.OTPDetailResponse
import com.wultra.android.digitalonboarding.networking.model.PresenceCheckResponse
import com.wultra.android.digitalonboarding.networking.model.ResendOtpResponse
import com.wultra.android.digitalonboarding.networking.model.SDKInitResponse
import com.wultra.android.digitalonboarding.networking.model.VerificationStatusResponse
import com.wultra.android.digitalonboarding.networking.model.VerifyOtpResponse
import com.wultra.android.powerauth.networking.IApiCallResponseListener
import com.wultra.android.powerauth.networking.data.StatusResponse
import com.wultra.android.powerauth.networking.error.ApiError
import com.wultra.android.powerauth.networking.error.ApiErrorCode
import io.getlime.security.powerauth.core.ActivationStatus
import io.getlime.security.powerauth.core.Password
import io.getlime.security.powerauth.exception.PowerAuthErrorCodes
import io.getlime.security.powerauth.networking.response.CreateActivationResult
import io.getlime.security.powerauth.networking.response.IActivationStatusListener
import io.getlime.security.powerauth.networking.response.ICreateActivationListener
import io.getlime.security.powerauth.networking.response.IValidatePasswordListener
import io.getlime.security.powerauth.sdk.PowerAuthActivation
import io.getlime.security.powerauth.sdk.PowerAuthSDK
import okhttp3.OkHttpClient
import java.time.Duration

typealias VerificationResult = WDOResult<VerificationService.Success, VerificationService.Fail>

/**
 * User response for the consent.
 */
enum class ConsentResponse {
    /** User approved the consent. */
    APPROVED,
    /** User declined the consent. */
    DECLINED,
    /** Consent is not required. */
    NOT_REQUIRED
}

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
            WDOLogger.d("PowerAuth has not a valid activation - clearing cache.")
            cachedProcess = null
        }
    }

    /** Time in seconds that user needs to wait between OTP resend calls */
    fun otpResendPeriodInSeconds(): Long? {
        val period = lastStatus?.responseObject?.config?.otpResendPeriod
        if (period == null) {
            WDOLogger.w("OTP resend period can be provided only when there was at least 1 status call made")
            return null
        }
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
    fun status(callback: (VerificationResult) -> Unit) {
        api.getStatus(object: IApiCallResponseListener<VerificationStatusResponse> {
            override fun onSuccess(result: VerificationStatusResponse) {
                when (result.responseObject.status) {
                    IdentityVerificationStatus.FAILED, IdentityVerificationStatus.REJECTED, IdentityVerificationStatus.NOT_INITIALIZED, IdentityVerificationStatus.ACCEPTED -> {
                        WDOLogger.d("We reached endstate, clearing cache")
                        cachedProcess = null
                    }
                    else -> {
                        // nothing
                    }
                }

                lastStatus = result

                val nextStep = VerificationStatusNextStep.fromStatusResponse(result.responseObject)

                WDOLogger.i("Response status: ${result.responseObject.status.name}:${result.responseObject.phase?.name}. NextStep: ${nextStep.value.name}")

                when (nextStep.value) {
                    Value.INTRO -> {
                        markCompleted(VerificationStateIntroData(nextStep.consentRequired), callback)
                    }
                    Value.DOCUMENT_SCAN -> {
                        WDOLogger.d("Asking for a document status.")
                        api.documentsStatus(
                            result.responseObject.processId,
                            object : IApiCallResponseListener<DocumentsStatusResponse> {
                                override fun onSuccess(result: DocumentsStatusResponse) {

                                    WDOLogger.d("Document status success.")

                                    val documents = result.responseObject.documents
                                    val cachedProcess = this@VerificationService.cachedProcess

                                    if (cachedProcess != null) {
                                        WDOLogger.d("Cached process obtained, processing retrieved documents")
                                        cachedProcess.feed(documents)
                                        if (documents.any { it.action() == DocumentAction.ERROR } || documents.any { !it.errors.isNullOrEmpty() }) {
                                            WDOLogger.i("There is an document error - returning.")
                                            markCompleted(VerificationStateScanDocumentData(cachedProcess), callback)
                                        } else if (documents.all { it.action() == DocumentAction.PROCEED }) {
                                            WDOLogger.i("Document is waiting - continue scanning.")
                                            markCompleted(VerificationStateScanDocumentData(cachedProcess), callback)
                                        } else if (documents.any { it.action() == DocumentAction.WAIT }) {
                                            // TODO: really verification?
                                            WDOLogger.i("Document is processing - wait..")
                                            markCompleted(VerificationStateProcessingData(ProcessingItem.DOCUMENT_VERIFICATION), callback)
                                        } else if (documents.isEmpty()) {
                                            WDOLogger.i("There are no document - scan first.")
                                            markCompleted(VerificationStateScanDocumentData(cachedProcess), callback)
                                        } else {
                                            // TODO: is this ok?
                                            WDOLogger.w("Unexpected document state - configuration error.")
                                            markCompleted(VerificationStateFailedData, callback)
                                        }
                                    } else {
                                        if (documents.isEmpty()) {
                                            WDOLogger.i("No documents scanned - start scanning")
                                            markCompleted(VerificationStateDocumentsToScanSelectData, callback)
                                        } else {
                                            WDOLogger.w("Unexpected document state - configuration/cache error.")
                                            markCompleted(VerificationStateFailedData, callback)
                                        }
                                    }
                                }

                                override fun onFailure(error: ApiError) {
                                    WDOLogger.e("Document status failed : ${error.e}")
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
                    Value.ACTIVATION_FINISH -> {
                        markCompleted(VerificationStateActivationFinishData, callback)
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
                WDOLogger.e("Status failed : ${error.e}")
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
    fun getConsent(callback: (WDOResult<String, Fail>) -> Unit) {
        val processId = guardProcessId(callback) ?: return
        api.getConsentText(
            processId,
            object : IApiCallResponseListener<ConsentTextResponse> {
                override fun onSuccess(result: ConsentTextResponse) {
                    WDOLogger.i("getConsent success")
                    callback(WDOResult.success(result.responseObject.consentText))
                }

                override fun onFailure(error: ApiError) {
                    WDOLogger.e("getConsent failed : ${error.e}")
                    callback(WDOResult.failure(Fail(reason = error)))
                }
            },
        )
    }

    /**
     * Starts the verification process with the user consent.
     *
     * @param consentApprovedByUser User response for the consent.
     * @param callback Callback with the result.
     */
    fun start(consentApprovedByUser: ConsentResponse, callback: (VerificationResult) -> Unit) {
        val processId = guardProcessId(callback) ?: return

        when (consentApprovedByUser) {
            ConsentResponse.APPROVED -> {
                WDOLogger.i("User approved consent - resolving on the server")
                api.resolveConsent(
                    processId,
                    true,
                    object : IApiCallResponseListener<ConsentApproveResponse> {
                        override fun onSuccess(result: ConsentApproveResponse) {
                            WDOLogger.i("Consent granted - starting the verification process.")
                            startProcess(processId, callback)
                        }
                        override fun onFailure(error: ApiError) {
                            WDOLogger.e("Consent approve failed : ${error.e}")
                            markCompleted(error, callback)
                        }
                    },
                )
            }
            ConsentResponse.DECLINED -> {
                WDOLogger.i("User declined consent - returning to intro state")
                api.resolveConsent(
                    processId,
                    false,
                    object : IApiCallResponseListener<ConsentApproveResponse> {
                        override fun onSuccess(result: ConsentApproveResponse) {
                            WDOLogger.i("Consent declined success.")
                            val consentRequired = lastStatus?.responseObject?.consentRequired ?: true
                            markCompleted(VerificationStateIntroData(consentRequired), callback)
                        }
                        override fun onFailure(error: ApiError) {
                            WDOLogger.e("Consent decline failed : ${error.e}")
                            markCompleted(error, callback)
                        }
                    },
                )
            }
            ConsentResponse.NOT_REQUIRED -> {
                WDOLogger.i("Consent not required - start verification process immediately")
                startProcess(processId, callback)
            }
        }
    }

    /**
     * Returns a token for the document scanning SDK, when needed.
     *
     * @param challenge SDK generated challenge for the server
     * @param callback Callback with the token for the SDK.
     */
    fun documentsInitSDK(challenge: String, callback: (WDOResult<String, Fail>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.initScanSDK(
            processId,
            challenge,
            object : IApiCallResponseListener<SDKInitResponse> {
                override fun onSuccess(result: SDKInitResponse) {
                    WDOLogger.i("documentsInitSDK success")
                    WDOLogger.d("documentsInitSDK success token: ${result.responseObject.attributes.responseToken}")
                    markCompleted(result.responseObject.attributes.responseToken, callback)
                }

                override fun onFailure(error: ApiError) {
                    WDOLogger.e("documentsInitSDK failed : ${error.e}")
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
    fun documentsSetSelectedTypes(types: List<DocumentType>, callback: (VerificationResult) -> Unit) {
        // TODO: We should verify that we're in the expected state here
        val process = VerificationScanProcess(types)
        cachedProcess = process
        WDOLogger.i("Setting documents to scan: ${types.joinToString(",") { it.name }}")
        markCompleted(VerificationStateScanDocumentData(process), callback)
    }

    /**
     * Upload document files to the server.
     *
     * @param files Document files.
     * @param progressCallback Upload progress callback. (Not working at the moment)
     * @param callback Callback with the result.
     */
    fun documentsSubmit(files: List<DocumentFile>, progressCallback: (Double) -> Unit, callback: (VerificationResult) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        // TODO: progress callback
        try {
            val requestData = DocumentPayloadBuilder.build(processId, files)
            api.submitDocuments(
                requestData,
                object : IApiCallResponseListener<DocumentSubmitResponse> {
                    override fun onSuccess(result: DocumentSubmitResponse) {
                        WDOLogger.i("document submitted")
                        markCompleted(
                            VerificationStateProcessingData(ProcessingItem.DOCUMENT_UPLOAD),
                            callback
                        )
                    }

                    override fun onFailure(error: ApiError) {
                        WDOLogger.e("documentsSubmit failed : ${error.e}")
                        markCompleted(error, callback)
                    }
                }
            )
        } catch (e: Throwable) {
            WDOLogger.e("Failed to create payload data : $e")
            markCompleted(ApiError(e), callback)
        }
    }

    /**
     * Starts presence check. This returns attributes that are needed to start presence check SDK.
     *
     * @param callback Callback with the result.
     */
    fun presenceCheckInit(callback: (WDOResult<Map<String, Any>, Fail>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.presenceCheckInit(
            processId,
            object : IApiCallResponseListener<PresenceCheckResponse> {
                override fun onSuccess(result: PresenceCheckResponse) {
                    WDOLogger.e("presenceCheckInit success")
                    WDOLogger.e("presenceCheckInit success with: ${result.responseObject.attributes}")
                    markCompleted(result.responseObject.attributes, callback)
                }

                override fun onFailure(error: ApiError) {
                    WDOLogger.e("presenceCheckInit failed : ${error.e}")
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
    fun presenceCheckSubmit(callback: (VerificationResult) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.presenceCheckSubmit(
            processId,
            object : IApiCallResponseListener<StatusResponse> {
                override fun onSuccess(result: StatusResponse) {
                    WDOLogger.i("presenceCheckSubmit success")
                    markCompleted(
                        VerificationStateProcessingData(ProcessingItem.VERIFYING_PRESENCE),
                        callback
                    )
                }

                override fun onFailure(error: ApiError) {
                    WDOLogger.e("presenceCheckSubmit failed : ${error.e}")
                    markCompleted(error, callback)
                }
            }
        )
    }

    /**
     * Restarts verification. When successfully called, intro screen should be presented.
     *
     * @param callback Callback with the result.
     */
    fun restartVerification(callback: (VerificationResult) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.cleanup(
            processId,
            object : IApiCallResponseListener<StatusResponse> {
                override fun onSuccess(result: StatusResponse) {
                    WDOLogger.i("restartVerification success")
                    status(callback)
                }

                override fun onFailure(error: ApiError) {
                    WDOLogger.e("restartVerification failed : ${error.e}")
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
    fun cancelWholeProcess(callback: (WDOResult<Unit, Fail>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        onboardingApi.cancel(
            processId,
            object : IApiCallResponseListener<StatusResponse> {
                override fun onSuccess(result: StatusResponse) {
                    WDOLogger.i("cancelWholeProcess success")
                    callback(WDOResult.success(Unit))
                }

                override fun onFailure(error: ApiError) {
                    WDOLogger.e("cancelWholeProcess failed : ${error.e}")
                    callback(WDOResult.failure(Fail(error)))
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
    fun verifyOTP(otp: String, callback: (VerificationResult) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.verifyOtp(
            processId,
            otp,
            object : IApiCallResponseListener<VerifyOtpResponse> {
                override fun onSuccess(result: VerifyOtpResponse) {
                    if (result.responseObject.verified) {
                        WDOLogger.i("verifyOTP success")
                        markCompleted(
                            VerificationStateProcessingData(ProcessingItem.OTHER),
                            callback,
                        )
                    } else {
                        WDOLogger.e("verifyOTP failed. remainingAttempts: ${result.responseObject.remainingAttempts}, expired: ${result.responseObject.expired}")
                        if (result.responseObject.remainingAttempts > 0 && !result.responseObject.expired) {
                            WDOLogger.i("There are remaining OTP attempts, returning the OTP Status")
                            markCompleted(
                                VerificationStateOtpData(result.responseObject.remainingAttempts),
                                callback,
                            )
                        } else {
                            WDOLogger.i("OTP cannot be tried again - returning an OTPFailedException.")
                            markCompleted(ApiError(OTPFailedException), callback)
                        }
                    }
                }

                override fun onFailure(error: ApiError) {
                    WDOLogger.e("verifyOTP failed : ${error.e}")
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
    fun resendOTP(callback: (WDOResult<Unit, Fail>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.resendOtp(
            processId,
            object : IApiCallResponseListener<ResendOtpResponse> {
                override fun onSuccess(result: ResendOtpResponse) {
                    WDOLogger.i("resendOTP success")
                    callback(WDOResult.success(Unit))
                }

                override fun onFailure(error: ApiError) {
                    WDOLogger.e("verifyOTP failed : ${error.e}")
                    markCompleted(error, callback)
                }
            },
        )
    }

    /**
     * Finishes verification by creating a new PowerAuth activation on given `newPowerAuthInstance`.
     *
     * Needs to be called when `ACTIVATION_FINISH` next step is returned from the `status()` call.
     *
     * The method verifies that the provided `password` is the same as used in the original activation
     * (if `validatePassword` is set to `true`), then it calls the server API to finish
     * the verification and obtain the activation code for the new activation. Finally, it creates
     * a new activation on the `newPowerAuthInstance` using the obtained activation code and persists it
     * with the provided `newPassword`.
     *
     * After successful completion, the original PowerAuth instance becomes invalid (`REMOVED` state) and cannot be used anymore.
     *
     * @param newPowerAuthInstance PowerAuth instance where to create new activation. This instance must not have an existing activation.
     * @param newActivationName Name of the new activation to be created on `newPowerAuthInstance`.
     * @param newPassword Password to protect the new activation. In case `validatePassword` is `true`, this password must match the password of the original activation.
     * @param validatePassword If set to `true`, the method verifies that the provided `newPassword` matches the password of the original activation.
     * @param userIdentification Optional user identification object to be sent to the server during the finish activation process.
     */
    fun finishActivation(
        newPowerAuthInstance: PowerAuthSDK,
        newActivationName: String,
        newPassword: Password,
        validatePassword: Boolean,
        userIdentification: Any?,
        callback: (VerificationResult) -> Unit
    ) {

        val processId = guardProcessId(callback) ?: return

        // Validate password if required
        validatePasswordIfRequired(validatePassword, newPassword) { error ->

            if (error != null) {
                // Password validation failed
                WDOLogger.e("finishActivation - password validation failed : ${error.message}")
                markCompleted(ApiError(error), callback)
                return@validatePasswordIfRequired
            }

            if (!newPowerAuthInstance.canStartActivation()) {
                WDOLogger.e("finishActivation - cannot activate, the `newPowerAuthInstance` is not in a state that allows it")
                markCompleted(ApiError(Exception("Cannot start activation on given PowerAuth object.")), callback)
                return@validatePasswordIfRequired
            }

            // Proceed with finish activation API call
            api.finishActivation(
                processId,
                userIdentification,
                object: IApiCallResponseListener<FinishActivationResponse> {

                    override fun onSuccess(result: FinishActivationResponse) { // onSuccess:finishActivation

                        WDOLogger.i("finishActivation success")

                        // Create new activation on the new PowerAuth instance with obtained activation code
                        val activation = PowerAuthActivation.Builder.activation(
                            result.responseObject.activationCode,
                            newActivationName
                        )

                        // Helper to clear the created activation in case of failure
                        val clearPaInstanceIfNeeded = {
                            if (!newPowerAuthInstance.canStartActivation()) {
                                newPowerAuthInstance.removeActivationLocal(appContext)
                            }
                        }

                        // create activation
                        newPowerAuthInstance.createActivation(
                            activation.build(),
                            object : ICreateActivationListener {

                                override fun onActivationCreateSucceed(result: CreateActivationResult) {
                                    // New activation created, now persist it with the provided password
                                    val persistResult = newPowerAuthInstance.persistActivationWithPassword(appContext, newPassword)
                                    if (persistResult == PowerAuthErrorCodes.SUCCEED) {
                                        // New activation persisted
                                        markCompleted(VerificationStateSuccessData, callback)
                                    } else {
                                        // Failed to persist new activation, clean up
                                        clearPaInstanceIfNeeded()
                                        WDOLogger.e("Failed to persist PowerAuth activation. Code: $persistResult")
                                        markCompleted(Fail(ApiError(Exception("Failed to persist PowerAuth activation. Code: $persistResult"))), callback)
                                    }
                                }

                                override fun onActivationCreateFailed(t: Throwable) {
                                    // Failed to create new activation, clean up
                                    clearPaInstanceIfNeeded()
                                    WDOLogger.e("finishActivation failed - failed to create activation : $t")
                                    markCompleted(Fail(ApiError(t)), callback)
                                }
                            }
                        )
                    }

                    override fun onFailure(error: ApiError) { // onFailure:finishActivation
                        // Finish activation API call failed
                        WDOLogger.e("finishActivation failed : ${error.e}")
                        markCompleted(error, callback)
                    }
                }
            )
        }
    }

    /**
     * Validates password if required. If not required, calls callback immediately.
     *
     * @param required Whether the password validation is required.
     * @param password Password to validate.
     * @param callback Callback with the error if any.
     */
    private fun validatePasswordIfRequired(
        required: Boolean,
        password: Password,
        callback: (Throwable?) -> Unit,
    ) {
        if (!required) {
            // Password validation not required
            callback(null)
        } else {
            powerAuthSDK.validatePassword(
                appContext,
                password,
                object : IValidatePasswordListener {
                    override fun onPasswordValid() {
                        callback(null)
                    }

                    override fun onPasswordValidationFailed(t: Throwable) {
                        WDOLogger.i("Password validation failed.")
                        callback(t)
                    }
                }
            )
        }
    }

    /**
     * Demo endpoint available only in Wultra Demo systems.
     *
     * @param callback Callback with the result.
     */
    internal fun getOTP(callback: (WDOResult<String, Fail>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.getOtp(
            processId,
            object : IApiCallResponseListener<OTPDetailResponse> {
                override fun onSuccess(result: OTPDetailResponse) {
                    WDOLogger.i("getOTP success")
                    callback(WDOResult.success(result.responseObject.otpCode))
                }

                override fun onFailure(error: ApiError) {
                    WDOLogger.e("verifyOTP failed : ${error.e}")
                    callback(WDOResult.failure(Fail(error)))
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
     * @param reason Cause of the error.
     */
    class Fail(val reason: ApiError): Exception() {
        /** State of the verification for app to display */
        val state: VerificationStateData? = when (reason.error) {
            ApiErrorCode.ONBOARDING_FAILED -> VerificationStateEndstateData(EndstateReason.OTHER)
            ApiErrorCode.IDENTITY_VERIFICATION_FAILED -> VerificationStateFailedData
            ApiErrorCode.ONBOARDING_PROCESS_LIMIT_REACHED -> VerificationStateEndstateData(EndstateReason.LIMIT_REACHED)
            ApiErrorCode.IDENTITY_PRESENCE_CHECK_LIMIT_REACHED, ApiErrorCode.IDENTITY_VERIFICATION_LIMIT_REACHED -> VerificationStateFailedData
            else -> null
        }
    }
    /** PowerAuth instance cannot start the activation. */
    object ActivationNotActiveException: Exception("PowerAuth instance is not in the active state.")
    /** Verification status needs to be fetched first */
    object ActivationMissingStatusException: Exception("Verification status needs to be fetched first.")
    /** OTP failed to verify. Refresh status to retrieve current status */
    object OTPFailedException: Exception("OTP failed to verify.")

    // Private helper methods

    // Starts the verification process on backend.
    private fun startProcess(processId: String, callback: (VerificationResult) -> Unit) {
        api.start(
            processId,
            object : IApiCallResponseListener<StatusResponse> {
                override fun onSuccess(result: StatusResponse) {
                    WDOLogger.i("Verification process start success")
                    markCompleted(VerificationStateDocumentsToScanSelectData, callback)
                }
                override fun onFailure(error: ApiError) {
                    WDOLogger.e("Verification process start failed : ${error.e}")
                    markCompleted(error, callback)
                }
            },
        )
    }

    private fun <T>guardProcessId(callback: (WDOResult<T, Fail>) -> Unit): String? {

        val processId = lastStatus?.responseObject?.processId
        if (processId == null) {
            WDOLogger.e("ProcessId is required for the requested method but not available. This mean that the process was not started or status was not fetched yet.")
            markCompleted(Fail(ApiError(ActivationMissingStatusException)), callback)
            return null
        }
        return processId
    }

    private fun <T>markCompleted(error: ApiError, callback: (WDOResult<T, Fail>) -> Unit) {
        if (!error.isOffline() || error.error == ApiErrorCode.POWERAUTH_AUTH_FAIL) {
            WDOLogger.e("Fetching activation status to determine if activation was not removed on the server")
            powerAuthSDK.fetchActivationStatusWithCallback(
                appContext,
                object : IActivationStatusListener {
                    override fun onActivationStatusSucceed(status: ActivationStatus?) {
                        if (status?.state != ActivationStatus.State_Active) {
                            WDOLogger.e("PowerAuth status not active.")
                            listener?.powerAuthActivationStatusChanged(this@VerificationService, status)
                            markCompleted(Fail(ApiError(ActivationNotActiveException)), callback)
                        } else {
                            markCompleted(Fail(error), callback)
                        }
                    }

                    override fun onActivationStatusFailed(t: Throwable) {
                        WDOLogger.e("Failed to retrieve PowerAuth status")
                        markCompleted(Fail(ApiError(t)), callback)
                    }
                }
            )
        } else {
            markCompleted(Fail(error), callback)
        }
    }

    private fun <T>markCompleted(result: T, callback: (WDOResult<T, Fail>) -> Unit) {
        callback(VerificationResult.success(result))
    }

    private fun markCompleted(state: VerificationStateData, callback: (VerificationResult) -> Unit) {
        val result = Success(state)
        listener?.verificationStatusChanged(this, state)
        callback(VerificationResult.success(result))
    }

    private fun <T>markCompleted(fail: Fail, callback: (WDOResult<T, Fail>) -> Unit) {
        if (fail.state != null) {
            listener?.verificationStatusChanged(this, fail.state)
        }

        callback(VerificationResult.failure(fail))
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
