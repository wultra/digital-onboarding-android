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
import com.wultra.android.digitalonboarding.networking.CustomerOnboardingApi
import com.wultra.android.digitalonboarding.networking.GetStatusResponse
import com.wultra.android.digitalonboarding.networking.OTPDetailResponse
import com.wultra.android.digitalonboarding.networking.OnboardingStatus
import com.wultra.android.digitalonboarding.networking.StartOnboardingResponse
import com.wultra.android.powerauth.networking.IApiCallResponseListener
import com.wultra.android.powerauth.networking.data.StatusResponse
import com.wultra.android.powerauth.networking.error.ApiError
import io.getlime.security.powerauth.sdk.PowerAuthSDK
import okhttp3.OkHttpClient

/**
 * ActivationService
 */
class ActivationService(
    identityServerUrl: String,
    appContext: Context,
    okHttpClient: OkHttpClient,
    powerAuthSDK: PowerAuthSDK,
    canRestoreSession: Boolean = true
) {

    init {
        if (!canRestoreSession) {
            setProcessId(null)
        }
    }

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

    /** PUBLIC API */

    fun hasActiveProcess() = getProcessId() != null

    fun status(callback: (Result<Status>) -> Unit) {

        val processId = getProcessId()
        if (processId == null) {
            // TODO: error
            return
        }

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

        if (getProcessId() != null) {
            // TODO: error
            return
        }

        api.start(
            credentials,
            object : IApiCallResponseListener<StartOnboardingResponse> {
                override fun onSuccess(result: StartOnboardingResponse) {
                    setProcessId(result.responseObject.processId)
                    callback(Result.success(Unit))
                }

                override fun onFailure(error: ApiError) {
                    callback(Result.failure(error.toException()))
                }
            }
        )
    }

    fun cancel(forceCancel: Boolean = true, callback: (Result<Unit>) -> Unit) {

        val processId = getProcessId()
        if (processId == null) {
            // TODO: error
            return
        }

        api.cancel(
            processId,
            object : IApiCallResponseListener<StatusResponse> {
                override fun onSuccess(result: StatusResponse) {
                    setProcessId(null)
                    callback(Result.success(Unit))
                }

                override fun onFailure(error: ApiError) {
                    if (forceCancel) {
                        setProcessId(null)
                        callback(Result.success(Unit))
                    } else {
                        callback(Result.failure(error.toException()))
                    }
                }
            }
        )
    }

    fun clear() {
        setProcessId(null)
    }

    fun getOtp(callback: (Result<String>) -> Unit) {

        val processId = getProcessId()
        if (processId == null) {
            // TODO: error
            return
        }

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

        val processId = getProcessId()
        if (processId == null) {
            // TODO: error
            return
        }

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

    private fun getProcessId() = storage.getValue(keychainKey)

    private fun setProcessId(processId: String?) = storage.setValue(keychainKey, processId)
}
