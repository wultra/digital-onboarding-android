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
import com.wultra.android.digitalonboarding.networking.CustomerOnboardingApi
import com.wultra.android.digitalonboarding.networking.GetStatusResponse
import com.wultra.android.digitalonboarding.networking.OTPDetailResponse
import com.wultra.android.digitalonboarding.networking.OnboardingStatus
import com.wultra.android.digitalonboarding.networking.StartOnboardingResponse
import com.wultra.android.powerauth.networking.IApiCallResponseListener
import com.wultra.android.powerauth.networking.data.StatusResponse
import com.wultra.android.powerauth.networking.error.ApiError
import io.getlime.security.powerauth.networking.exceptions.FailedApiException
import io.getlime.security.powerauth.networking.response.CreateActivationResult
import io.getlime.security.powerauth.sdk.PowerAuthSDK
import okhttp3.OkHttpClient
import java.lang.Exception

/**
 * ActivationService
 */
class ActivationService(
    identityServerUrl: String,
    appContext: Context,
    okHttpClient: OkHttpClient,
    private val powerAuthSDK: PowerAuthSDK,
    canRestoreSession: Boolean = true
) {

    /** PUBLIC PROPERTIES & CLASSES */

    enum class Status {
        ACTIVATION_IN_PROGRESS,
        VERIFICATION_IN_PROGRESS,
        FAILED,
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

    var acceptLanguage: String
        set(value) { api.acceptLanguage = value }
        get() { return api.acceptLanguage }

    /** PRIVATE PROPERTIES & CLASSES */

    private val api = CustomerOnboardingApi(identityServerUrl, okHttpClient, powerAuthSDK, appContext)
    private val storage = Storage(appContext, "wdo-prefs-encrypted")
    private val keychainKey = "wdopid_${powerAuthSDK.configuration.instanceId}"
    private var processId: String?
        get() = storage.getValue(keychainKey)
        set(value) = storage.setValue(keychainKey, value)

    init {
        if (!canRestoreSession) {
            processId = null
        }
    }

    /** PUBLIC API */

    fun hasActiveProcess() = processId != null

    fun status(callback: (Result<Status>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        if (!verifyCanStartProcess(callback)) return

        api.getStatus(
            processId,
            object : IApiCallResponseListener<GetStatusResponse> {
                override fun onSuccess(result: GetStatusResponse) {
                    callback(Result.success(Status.fromResponse(result)))
                }

                override fun onFailure(error: ApiError) {
                    callback(Result.failure(error.toException()))
                }
            }
        )
    }

    fun <T> start(credentials: T, callback: (Result<Unit>) -> Unit) {

        if (processId != null) {
            // TODO: better exception
            callback(Result.failure(Exception("TODO: better exception")))
            return
        }

        if (!verifyCanStartProcess(callback)) return

        api.start(
            credentials,
            object : IApiCallResponseListener<StartOnboardingResponse> {
                override fun onSuccess(result: StartOnboardingResponse) {
                    processId = result.responseObject.processId
                    callback(Result.success(Unit))
                }

                override fun onFailure(error: ApiError) {
                    callback(Result.failure(error.toException()))
                }
            }
        )
    }

    fun cancel(forceCancel: Boolean = true, callback: (Result<Unit>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        if (!verifyCanStartProcess(callback)) return

        api.cancel(
            processId,
            object : IApiCallResponseListener<StatusResponse> {
                override fun onSuccess(result: StatusResponse) {
                    this@ActivationService.processId = null
                    callback(Result.success(Unit))
                }

                override fun onFailure(error: ApiError) {
                    if (forceCancel) {
                        this@ActivationService.processId = null
                        callback(Result.success(Unit))
                    } else {
                        callback(Result.failure(error.toException()))
                    }
                }
            }
        )
    }

    fun clear() {
        processId = null
    }

    fun getOtp(callback: (Result<String>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        api.getOtp(
            processId,
            object : IApiCallResponseListener<OTPDetailResponse> {
                override fun onSuccess(result: OTPDetailResponse) {
                    callback(Result.success(result.responseObject.otpCode))
                }

                override fun onFailure(error: ApiError) {
                    callback(Result.failure(error.toException()))
                }
            }
        )
    }

    fun resendOtp(callback: (Result<Unit>) -> Unit) {

        val processId = guardProcessId(callback) ?: return

        if (!verifyCanStartProcess(callback)) return

        api.resendOtp(
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

    fun activate(
        otp: String,
        activationName: String = Build.MODEL,
        callback: (Result<CreateActivationResult>) -> Unit
    ) {
        val processId = guardProcessId(callback) ?: return

        if (!verifyCanStartProcess(callback)) return

        val data = ActivationDataWithOTP(processId, otp)

        powerAuthSDK.createActivation(data, activationName) { result ->
            result.onSuccess {
                this.processId = null
                callback(Result.success(it))
            }.onFailure {
                // when no longer possible to retry activation
                // reset the processID, because we cannot recover
                if ((it as? FailedApiException)?.allowOnboardingOtpRetry() == false) {
                    this.processId = null
                }
                callback(Result.failure(it))
            }
        }
    }

    private fun <T>guardProcessId(callback: (Result<T>) -> Unit): String? {

        val processId = this.processId
        if (processId == null) {
            // TODO: better exception
            callback(Result.failure(Exception("TODO: better exception")))
            return null
        }
        return processId
    }

    private fun <T>verifyCanStartProcess(callback: (Result<T>) -> Unit): Boolean {

        if (!powerAuthSDK.canStartActivation()) {
            processId = null
            // TODO: better exception
            callback(Result.failure(Exception("TODO: better exception")))
            return false
        }
        return true
    }
}

private data class ActivationDataWithOTP(val processId: String, val otp: String): ActivationData {
    override fun processId() = processId
    override fun asAttributes() = mapOf(Pair("processId", processId), Pair("otpCode", otp), Pair("credentialsType", "ONBOARDING"))
}
