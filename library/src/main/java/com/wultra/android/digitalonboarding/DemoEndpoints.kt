/*
 * Copyright (c) 2026, Wultra s.r.o. (www.wultra.com).
 *
 * All rights reserved. This source code can be used only for purposes specified
 * by the given license contract signed by the rightful deputy of Wultra s.r.o.
 * This source code can be used only by the owner of the license.
 *
 * Any disputes arising in respect of this agreement (license) shall be brought
 * before the Municipal Court of Prague.
 */

package com.wultra.android.digitalonboarding

import com.google.gson.Gson
import com.wultra.android.digitalonboarding.networking.model.OTPDetailRequest
import com.wultra.android.digitalonboarding.networking.model.OTPDetailRequestData
import com.wultra.android.digitalonboarding.networking.model.OTPDetailResponse
import com.wultra.android.digitalonboarding.networking.model.OTPDetailType
import com.wultra.android.powerauth.networking.Api
import com.wultra.android.powerauth.networking.E2EEConfiguration
import com.wultra.android.powerauth.networking.EndpointBasic
import com.wultra.android.powerauth.networking.IApiCallResponseListener
import com.wultra.android.powerauth.networking.error.ApiError
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Demo endpoints available only in Wultra Demo systems.
 */
object DemoEndpoints {

    /**
     * Demo endpoint available only in Wultra Demo systems.
     *
     * @param service Activation service instance.
     * @param strategy Which endpoint strategy should be used for OTP retrieval.
     * @param callback Result callback.
     */
    fun getOTP(
        service: ActivationService,
        strategy: GetOTPEndpointStrategy = GetOTPEndpointStrategy.AutomaticMock,
        callback: (WDOResult<String, ApiError>) -> Unit,
    ) {
        getOTP(
            processId = service.processId,
            identityServerUrl = service.identityServerUrl,
            esoApi = service.api,
            strategy = strategy,
            otpType = OTPDetailType.ACTIVATION,
            callback = callback,
        )
    }

    /**
     * Demo endpoint available only in Wultra Demo systems
     *
     * @param service Verification service instance.
     * @param strategy Which endpoint strategy should be used for OTP retrieval.
     * @param callback Result callback.
     */
    fun getOTP(
        service: VerificationService,
        strategy: GetOTPEndpointStrategy = GetOTPEndpointStrategy.AutomaticMock,
        callback: (WDOResult<String, ApiError>) -> Unit,
    ) {
        getOTP(
            processId = service.processId,
            identityServerUrl = service.identityServerUrl,
            esoApi = service.api,
            strategy = strategy,
            otpType = OTPDetailType.USER_VERIFICATION,
            callback = callback,
        )
    }

    private fun getOTP(
        processId: String?,
        identityServerUrl: String,
        esoApi: Api,
        strategy: GetOTPEndpointStrategy = GetOTPEndpointStrategy.AutomaticMock,
        otpType: OTPDetailType,
        callback: (WDOResult<String, ApiError>) -> Unit
    ) {
        val processId = processId ?: run {
            callback(WDOResult.failure(ApiError(IllegalStateException("Process ID is not available"))))
            return
        }

        when (strategy) {
            GetOTPEndpointStrategy.Eso -> {
                esoApi.getOtp(
                    processId = processId,
                    otpType = otpType,
                    listener = object : IApiCallResponseListener<OTPDetailResponse> {
                        override fun onSuccess(result: OTPDetailResponse) {
                            callback(WDOResult.success(result.responseObject.otpCode))
                        }

                        override fun onFailure(error: ApiError) {
                            callback(WDOResult.failure(error))
                        }
                    },
                )
            }
            GetOTPEndpointStrategy.AutomaticMock -> {
                val mockUrl = runCatching {
                    val eso = URL(identityServerUrl)
                    val host = eso.host.replace("-eso", "-eso-mock")
                    URL(eso.protocol, host, eso.port, "/otp/detail")
                }.getOrElse {
                    callback(WDOResult.failure(ApiError(it)))
                    return
                }

                OtpEndpointNetworking.fetchOtpFromMockEndpoint(mockUrl, processId, otpType.name) { result ->
                    result
                        .onSuccess { otp ->
                            callback(WDOResult.success(otp))
                        }
                        .onFailure { error ->
                            callback(WDOResult.failure(ApiError(error)))
                        }
                }
            }
            is GetOTPEndpointStrategy.Custom -> {
                OtpEndpointNetworking.fetchOtpFromMockEndpoint(strategy.url, processId, otpType.name) { result ->
                    result
                        .onSuccess { otp ->
                            callback(WDOResult.success(otp))
                        }
                        .onFailure { error ->
                            callback(WDOResult.failure(ApiError(error)))
                        }
                }
            }
        }
    }
}

/**
 * Strategy where the getOTP endpoint is located.
 */
sealed class GetOTPEndpointStrategy {
    /** Part of the enrollment-onboarding-server. */
    object Eso : GetOTPEndpointStrategy()

    /**
     * Mock server - automatic.
     *
     * For example, when deployment-mtoken-eso-dev.test.com is the url of the ESO server,
     * then path to the mock API is deployment-mtoken-eso-mock-dev.test.com/otp/detail.
     */
    object AutomaticMock : GetOTPEndpointStrategy()

    /** Exact url of the OTP endpoint (path including). */
    data class Custom(val url: URL) : GetOTPEndpointStrategy()
}

// Note: This endpoint is not part of the standard onboarding API and is available only in Wultra Demo systems for testing purposes.
internal fun Api.getOtp(
    processId: String,
    otpType: OTPDetailType,
    listener: IApiCallResponseListener<OTPDetailResponse>,
) {
    post(
        OTPDetailRequest(OTPDetailRequestData(processId, otpType)),
        EndpointBasic(
            "api/onboarding/otp/detail",
            E2EEConfiguration.APPLICATION_SCOPE,
        ),
        null,
        null,
        listener,
    )
}

internal class OtpEndpointNetworking {

    companion object {

        // The endpoint is expected to return JSON with the otpCode field, e.g. { "otpCode": "123456" }
        // there is no authentication or encryption, so the endpoint should be used only for testing with Wultra Demo systems and never in production.
        internal fun fetchOtpFromMockEndpoint(
            url: URL,
            processId: String,
            otpType: String,
            callback: (Result<String>) -> Unit,
        ) {
            Thread {
                val result = runCatching {
                    val connection = (url.openConnection() as HttpURLConnection)
                    try {
                        connection.requestMethod = "POST"
                        connection.connectTimeout = 60_000
                        connection.readTimeout = 60_000
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.doOutput = true

                        val body = "{\"processId\":\"${escapeJson(processId)}\",\"otpType\":\"${otpType}\"}"
                        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }

                        val code = connection.responseCode
                        if (code !in 200..299) {
                            val errorText = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                            throw IllegalStateException("HTTP ${code}: ${errorText}")
                        }

                        val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                        val otpCode = runCatching {
                            Gson().fromJson(response, OtpCodeResponse::class.java)?.otpCode
                        }.getOrNull()

                        otpCode?.takeIf { it.isNotBlank() }
                            ?: throw IllegalStateException("Mock OTP response does not contain otpCode")
                    } finally {
                        connection.disconnect()
                    }
                }

                callback(result)
            }.start()
        }

        private data class OtpCodeResponse(
            val otpCode: String?,
        )

        private fun escapeJson(value: String): String {
            return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
        }
    }
}
