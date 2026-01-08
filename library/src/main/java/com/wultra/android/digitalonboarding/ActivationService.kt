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
import android.os.Build
import com.wultra.android.digitalonboarding.log.WDOLogger
import com.wultra.android.digitalonboarding.networking.CustomerOnboardingApi
import com.wultra.android.digitalonboarding.networking.model.GetStatusResponse
import com.wultra.android.digitalonboarding.networking.model.OTPDetailResponse
import com.wultra.android.digitalonboarding.networking.model.OnboardingStatus
import com.wultra.android.digitalonboarding.networking.model.StartOnboardingResponse
import com.wultra.android.powerauth.networking.IApiCallResponseListener
import com.wultra.android.powerauth.networking.data.StatusResponse
import com.wultra.android.powerauth.networking.error.ApiError
import io.getlime.security.powerauth.networking.exceptions.FailedApiException
import io.getlime.security.powerauth.networking.response.CreateActivationResult
import io.getlime.security.powerauth.sdk.PowerAuthSDK
import okhttp3.OkHttpClient

typealias ActivationResult<T> = WDOResult<T, ActivationService.Fail>
typealias ProcessData = Pair<String, String?> // first = process ID, second = activation code

/**
 * Digital Onboarding Activation Service.
 *
 * Service that can activate PowerAuthSDK instance by user weak credentials (like his login and birthdate) + OTP.
 *
 * This service operations against `enrollment-onboarding-server` and you need to configure networking service with URL of this service.
 *
 * @property powerAuthSDK Configured PowerAuthSDK instance. This instance needs to be without valid activation otherwise you'll get errors.
 * @constructor Creates service instance.
 *
 * @param identityServerUrl Base URL for service requests. Usually ending with `enrollment-onboarding-server`.
 * @param appContext Application context.
 * @param okHttpClient HTTP client for server communication.
 * @param canRestoreSession If the activation session can be restored (when app restarts). `true` by default
 */
class ActivationService(
    identityServerUrl: String,
    appContext: Context,
    okHttpClient: OkHttpClient,
    private val powerAuthSDK: PowerAuthSDK,
    canRestoreSession: Boolean = true
) {

    /** PUBLIC PROPERTIES & CLASSES */

    /** Status of the Onboarding Activation */
    enum class Status {
        /** Activation is in the progress */
        ACTIVATION_IN_PROGRESS,
        /** Activation was already finished, not waiting for the verification */
        VERIFICATION_IN_PROGRESS,
        /** Activation failed */
        FAILED,
        /** Both activation and verification were finished */
        FINISHED;

        internal companion object {
            fun fromResponse(response: GetStatusResponse): Status {
                return when (response.responseObject.onboardingStatus) {
                    OnboardingStatus.ACTIVATION_IN_PROGRESS -> ACTIVATION_IN_PROGRESS
                    OnboardingStatus.VERIFICATION_IN_PROGRESS -> VERIFICATION_IN_PROGRESS
                    OnboardingStatus.FAILED -> FAILED
                    OnboardingStatus.FINISHED -> FINISHED
                }
            }
        }
    }

    data class Fail(val cause: ApiError)

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

    /** PRIVATE PROPERTIES & CLASSES */

    private val api = CustomerOnboardingApi(identityServerUrl, okHttpClient, powerAuthSDK, appContext)
    private val storage = Storage(appContext, "wdo-prefs-encrypted")
    private val keychainKey = "wdopid_${powerAuthSDK.configuration.instanceId}"
    private var processData: ProcessData?
        get() = dataFromStorage(storage.getValue(keychainKey))
        set(value) = storage.setValue(keychainKey, dataToStorage(value))

    // Read-only helper for processId, that is used in several places in this file.
    private val processId: String?
        get() = processData?.first

    init {
        if (!canRestoreSession) {
            WDOLogger.w("Created ActivationService without restoration.")
            processData = null
        }
    }

    /** PUBLIC API */

    /**
     * If the activation process is in progress.
     *
     * Note that when the result is `true` it can be already discontinued on the server.
     * Calling `status` in such case is recommended.
     */
    fun hasActiveProcess() = processId != null

    /**
     * Retrieves the status of the onboarding activation.
     *
     * @param callback Callback with the result.
     */
    fun status(callback: (ActivationResult<Status>) -> Unit) {

        WDOLogger.d("Retrieving the status")

        val processId = guardProcessId(callback) ?: return

        if (!verifyCanStartProcess(callback)) return

        api.getStatus(
            processId,
            object : IApiCallResponseListener<GetStatusResponse> {
                override fun onSuccess(result: GetStatusResponse) {
                    val success = Status.fromResponse(result)
                    WDOLogger.i("Status call success: ${success.name}")
                    callback(ActivationResult.success(success))
                }

                override fun onFailure(error: ApiError) {
                    WDOLogger.e(error)
                    callback(ActivationResult.failure(Fail(error)))
                }
            }
        )
    }

    /**
     * Starts onboarding activation with provided credentials.
     *
     * @param T Type that represents user credentials.
     * @param credentials Object with credentials. Which credentials are needed should be provided by a system/backend provider.
     * @param processType The process type identification. If not specified, the default process type will be used.
     * @param callback Callback with the result.
     */
    fun <T> start(
        credentials: T,
        processType: String? = null,
        callback: (ActivationResult<Unit>) -> Unit
    ) {

        if (processId != null) {
            WDOLogger.e("Activation can be started only when another activation is not in progress.")
            callback(ActivationResult.failure(Fail(ApiError(ActivationInProgressException))))
            return
        }

        if (!verifyCanStartProcess(callback)) return

        api.start(
            credentials,
            processType,
            object : IApiCallResponseListener<StartOnboardingResponse> {
                override fun onSuccess(result: StartOnboardingResponse) {
                    // cache result
                    processData = result.responseObject.processId to result.responseObject.activationCode
                    WDOLogger.i("Start successful")
                    callback(ActivationResult.success(Unit))
                }

                override fun onFailure(error: ApiError) {
                    WDOLogger.e(error)
                    processData = null
                    callback(ActivationResult.failure(Fail(error)))
                }
            }
        )
    }

    /**
     * Cancels the process.
     *
     * @param forceCancel When true, the process will be canceled in the SDK even when fails on the backend. `true` by default.
     * @param callback Callback with the result.
     */
    fun cancel(forceCancel: Boolean = true, callback: (ActivationResult<Unit>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        if (!verifyCanStartProcess(callback)) return

        api.cancel(
            processId,
            object : IApiCallResponseListener<StatusResponse> {
                override fun onSuccess(result: StatusResponse) {
                    this@ActivationService.processData = null
                    WDOLogger.i("Cancel successful")
                    callback(ActivationResult.success(Unit))
                }

                override fun onFailure(error: ApiError) {
                    if (forceCancel) {
                        this@ActivationService.processData = null
                        WDOLogger.w("Cancel failed, but forceCancel was used - returning success anyway.")
                        callback(ActivationResult.success(Unit))
                    } else {
                        WDOLogger.e(error)
                        callback(ActivationResult.failure(Fail(error)))
                    }
                }
            }
        )
    }

    /** Clears the stored data (without networking call). */
    fun clear() {
        processData = null
        WDOLogger.i("Clear successful")
    }

    /**
     * Requests OTP resend.
     *
     * @param callback Callback with the result.
     */
    fun resendOtp(callback: (ActivationResult<Unit>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        if (!verifyCanStartProcess(callback)) return

        api.resendOtp(
            processId,
            object : IApiCallResponseListener<StatusResponse> {
                override fun onSuccess(result: StatusResponse) {
                    WDOLogger.i("Clear successful")
                    callback(ActivationResult.success(Unit))
                }

                override fun onFailure(error: ApiError) {
                    WDOLogger.e(error)
                    callback(ActivationResult.failure(Fail(error)))
                }
            }
        )
    }

    fun createPowerAuthActivationData(otp: String): ActivationData? {
        val processId = processId
        if (processId == null) {
            WDOLogger.e("Cannot create activation data - process not started (missing processId).")
            return null
        }
        return ActivationDataWithOTP(processId, otp)
    }

    /**
     * Activates PowerAuthSDK instance that was passed in the initializer.
     *
     * @param otp OTP provided by the user. Optional when not required by backend.
     * @param activationName Name of the activation. Device name by default.
     * @param callback Callback with the result.
     */
    fun activate(
        otp: String?,
        activationName: String = Build.MODEL,
        callback: (ActivationResult<CreateActivationResult>) -> Unit
    ) {
        val processId = guardProcessId(callback) ?: return
        if (!verifyCanStartProcess(callback)) return

        fun handleResult(result: Result<CreateActivationResult>) {
            result.onSuccess {
                this.processData = null
                WDOLogger.i("PowerAuth activation created")
                callback(ActivationResult.success(it))
            }.onFailure {
                // when no longer possible to retry activation
                // reset the processID, because we cannot recover
                if ((it as? FailedApiException)?.allowOnboardingOtpRetry() == false) {
                    this.processData = null
                }
                WDOLogger.e("PowerAuth activation failed - $it")
                callback(ActivationResult.failure(Fail(ApiError(it))))
            }
        }

        val activationCode = processData?.second
        // use different call in case of ActivationCode
        if (activationCode != null) {
            powerAuthSDK.createActivation(
                activationCode,
                otp,
                activationName,
                ::handleResult
            )
        } else {
            val data = ActivationDataWithOTP(processId, otp)
            powerAuthSDK.createActivation(
                data,
                activationName,
                ::handleResult
            )
        }
    }

    private fun <S>guardProcessId(callback: (ActivationResult<S>) -> Unit): String? {

        val processId = this.processId
        if (processId == null) {
            WDOLogger.e("ProcessId is required for the requested method but not available. This mean that the process was not started.")
            callback(WDOResult.failure(Fail(ApiError(ActivationNotRunningException))))
            return null
        }
        return processId
    }

    private fun <T>verifyCanStartProcess(callback: (ActivationResult<T>) -> Unit): Boolean {

        if (!powerAuthSDK.canStartActivation()) {
            WDOLogger.e("Cannot start the activation: PowerAuthSDK.canStartActivation() == false")
            processData = null
            callback(ActivationResult.failure(Fail(ApiError(CannotActivateException))))
            return false
        }
        return true
    }

    /**
     * Demo endpoint available only in Wultra Demo systems
     *
     * @param callback Result callback.
     */
    fun getOtp(callback: (ActivationResult<String>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.getOtp(
            processId,
            object : IApiCallResponseListener<OTPDetailResponse> {
                override fun onSuccess(result: OTPDetailResponse) {
                    WDOLogger.i("Get OTP successful")
                    callback(ActivationResult.success(result.responseObject.otpCode))
                }

                override fun onFailure(error: ApiError) {
                    WDOLogger.e(error)
                    callback(ActivationResult.failure(Fail(error)))
                }
            }
        )
    }

    /** Exception when PowerAuth instance cannot start new activation. */
    object CannotActivateException: Exception("PowerAuth instance cannot start the activation.")
    /** When customer activation was already started. */
    object ActivationInProgressException: Exception("Wultra Digital Onboarding activation is already in progress.")
    /** Wultra Digital Onboarding activation was not started. */
    object ActivationNotRunningException: Exception("Wultra Digital Onboarding activation was not started.")
}

private data class ActivationDataWithOTP(
    val processId: String,
    val otp: String?,
): ActivationData {
    override fun processId() = processId
    override fun asAttributes(): Map<String, String> =
        buildMap {
            put("processId", processId)
            put("credentialsType", "ONBOARDING")
            otp?.let { put("otpCode", it) }
        }
}

private fun dataToStorage(processData: ProcessData?): String? {
    if (processData == null) {
        return null
    }
    return "${processData.first},${processData.second.orEmpty()}"
}

private fun dataFromStorage(stored: String?): ProcessData? {
    if (stored == null) {
        return null
    }
    val parts = stored.split(",", limit = 2)
    if (parts.size < 2) return null
    return parts[0] to parts[1].takeIf { it.isNotEmpty() }
}
