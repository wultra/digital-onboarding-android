/*
 * Copyright 2026 Wultra s.r.o.
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

package com.wultra.android.digitalonboarding

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.wultra.android.digitalonboarding.networking.model.ConfigurationDocument
import com.wultra.android.digitalonboarding.networking.model.ConfigurationResponse
import com.wultra.android.digitalonboarding.networking.model.ConfigurationResponseData
import com.wultra.android.powerauth.networking.error.ApiError
import io.getlime.security.powerauth.core.ActivationStatus
import io.getlime.security.powerauth.exception.PowerAuthErrorCodes
import io.getlime.security.powerauth.exception.PowerAuthErrorException
import io.getlime.security.powerauth.networking.exceptions.FailedApiException
import io.getlime.security.powerauth.networking.response.CreateActivationResult
import io.getlime.security.powerauth.networking.response.IActivationStatusListener
import io.getlime.security.powerauth.sdk.PowerAuthConfiguration
import io.getlime.security.powerauth.sdk.PowerAuthSDK
import okhttp3.OkHttpClient
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal const val TEST_TIMEOUT_MS = 120_000L

internal class SimpleError(message: String) : Exception(message)

internal data class SampleCredentials(
    val clientNumber: String,
    val birthDate: String,
) {
    companion object {
        fun demo() = SampleCredentials(
            clientNumber = UUID.randomUUID().toString(),
            birthDate = "1989/11/17",
        )
    }
}

internal data class ServerEnvironment(
    val name: String,
    val processTypes: List<String>,
    val esUrl: String,
    val esoUrl: String,
    val mobileConfig: String,
    val otpMock: String,
    val servicesMock: Boolean,
    val authorization: String?,
)

internal data class ServerEnvironmentData(
    val environments: List<ServerEnvironment>,
)

internal sealed class OtpStrategy {
    object Eso : OtpStrategy()
    object AutomaticMock : OtpStrategy()
    data class Custom(val url: URL) : OtpStrategy()
}

internal enum class OtpType {
    ACTIVATION,
    USER_VERIFICATION,
}

internal object IntegrationTestConfig {

    private const val CONFIG_FILE_NAME = "config.json"

    fun loadEnvironments(): List<ServerEnvironment> {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val json = runCatching {
            assets.open(CONFIG_FILE_NAME).use { input ->
                BufferedInputStream(input).bufferedReader().use(BufferedReader::readText)
            }
        }.getOrElse {
            // Missing config means tests should be skipped by assumeTrue checks.
            return emptyList()
        }

        val data = runCatching {
            Gson().fromJson(json, ServerEnvironmentData::class.java)
        }.getOrElse {
            throw SimpleError("Failed to parse androidTest/assets/$CONFIG_FILE_NAME: ${it.message}")
        }

        return data?.environments.orEmpty()
    }
}

internal class TestHelper(
    private val appContext: Context,
    val environment: ServerEnvironment,
    val processType: String,
    customPaInstance: PowerAuthSDK? = null,
) {

    val powerAuth: PowerAuthSDK = customPaInstance ?: newPowerAuth(appContext, environment)
    val activation = ActivationService(environment.esoUrl, appContext, OkHttpClient(), powerAuth)
    val verification = VerificationService(environment.esoUrl, OkHttpClient(), appContext, powerAuth)
    val configuration = ConfigurationService(environment.esoUrl, appContext, OkHttpClient(), powerAuth)

    // Credentials used for activation (set after startAndActivate).
    var lastCredentials: SampleCredentials? = null
        private set

    fun createNewPowerAuth(): PowerAuthSDK = newPowerAuth(appContext, environment)

    fun getConfig(): ConfigurationResponseData {
        return configuration.awaitConfiguration(processType)
    }

    fun start(credentials: SampleCredentials = SampleCredentials.demo()) {
        activation.awaitStart(credentials, processType)
        if (!activation.hasActiveProcess()) {
            throw SimpleError("Activation should be active after start()")
        }

        // Verify onboarding activation status.
        val status = activation.awaitStatus()
        if (status != ActivationService.Status.ACTIVATION_IN_PROGRESS) {
            throw SimpleError("Expected ACTIVATION_IN_PROGRESS after start(), got: $status")
        }
    }

    fun activate(otp: String?) {
        // Verify status before activation.
        val status = activation.awaitStatus()
        if (status != ActivationService.Status.ACTIVATION_IN_PROGRESS) {
            throw SimpleError("Expected ACTIVATION_IN_PROGRESS before activate(), got: $status")
        }

        // Activate PowerAuth.
        activation.awaitActivate(otp)

        // Persist with random password.
        val persistCode = powerAuth.persistActivationWithPassword(appContext, UUID.randomUUID().toString())
        if (persistCode != PowerAuthErrorCodes.SUCCEED) {
            throw SimpleError("persistActivationWithPassword failed with code: $persistCode")
        }

        // Verify PowerAuth status after activation.
        val paStatus = powerAuth.awaitActivationStatus(appContext)
        if (!paStatus.needVerification()) {
            throw SimpleError("Expected activation status to require verification")
        }
        if (paStatus.state != ActivationStatus.State_Active) {
            throw SimpleError("Expected activation state ACTIVE, got: ${paStatus.state}")
        }
    }

    fun startAndActivate(credentials: SampleCredentials = SampleCredentials.demo()): Pair<ConfigurationResponseData, Boolean>? {
        lastCredentials = credentials
        val config = getConfig()
        start(credentials)

        // Get OTP when required.
        val otp = if (config.otpForIdentification) {
            getActivationOtp()
        } else {
            null
        }
        activate(otp)

        val statusResult = verification.awaitStatus()
        val intro = statusResult.state as? VerificationStateIntroData
            ?: throw SimpleError("Unexpected state after activation: ${statusResult.state.state}")

        return config to intro.consentRequired
    }

    fun assertVerificationState(expected: VerificationState) {
        val status = verification.awaitStatus()
        if (status.state.state != expected) {
            throw SimpleError("Unexpected verification state. Expected: $expected, got: ${status.state.state}")
        }
    }

    fun getActivationOtp(): String {
        val processId = activation.getProcessIdViaReflection()
        return when (val strategy = environment.otpStrategy()) {
            is OtpStrategy.Eso -> activation.awaitOtpViaReflection()
            is OtpStrategy.AutomaticMock -> fetchOtpFromMock(environment.automaticMockOtpUrl(), processId, OtpType.ACTIVATION)
            is OtpStrategy.Custom -> fetchOtpFromMock(strategy.url, processId, OtpType.ACTIVATION)
        }
    }

    fun getVerificationOtp(processId: String): String {
        return when (val strategy = environment.otpStrategy()) {
            is OtpStrategy.Eso -> verification.awaitOtpViaReflection()
            is OtpStrategy.AutomaticMock -> fetchOtpFromMock(environment.automaticMockOtpUrl(), processId, OtpType.USER_VERIFICATION)
            is OtpStrategy.Custom -> fetchOtpFromMock(strategy.url, processId, OtpType.USER_VERIFICATION)
        }
    }
}

internal fun ConfigurationResponseData.getDocumentsToScan(): List<ConfigurationDocument> {
    val selected = documents.groups
        .flatMap { group -> group.items.take(group.requiredDocumentsCount) }
        .toMutableList()

    if (selected.size < documents.totalRequiredDocumentsCount) {
        val allDocuments = documents.groups.flatMap { it.items }
        for (document in allDocuments) {
            if (selected.size >= documents.totalRequiredDocumentsCount) {
                break
            }
            if (selected.none { it.patchedType() == document.patchedType() }) {
                selected.add(document)
            }
        }
    }

    return selected
}

internal fun ConfigurationDocument.patchedType(): DocumentType {
    return if (type == "DRIVING_LICENCE") "DRIVING_LICENSE" else type
}

internal fun ApiError.isPowerAuthError(): Boolean {
    return e is FailedApiException || e is PowerAuthErrorException
}

internal fun ServerEnvironment.otpStrategy(): OtpStrategy {
    return when {
        otpMock.equals("ESO", ignoreCase = true) -> OtpStrategy.Eso
        otpMock.equals("AUTO", ignoreCase = true) -> OtpStrategy.AutomaticMock
        else -> OtpStrategy.Custom(URL(otpMock))
    }
}

internal fun ServerEnvironment.automaticMockOtpUrl(): URL {
    val eso = URL(esoUrl)
    val host = eso.host.replace("-eso", "-eso-mock")
    val portPart = if (eso.port != -1) ":${eso.port}" else ""
    return URL("${eso.protocol}://$host$portPart/otp/detail")
}

internal fun newPowerAuth(appContext: Context, environment: ServerEnvironment): PowerAuthSDK {
    val configuration = PowerAuthConfiguration.Builder(
        UUID.randomUUID().toString(),
        environment.esUrl,
        environment.mobileConfig,
    ).build()
    return PowerAuthSDK.Builder(configuration).build(appContext)
}

internal fun fetchOtpFromMock(url: URL, processId: String, type: OtpType): String {
    val connection = (url.openConnection() as HttpURLConnection)
    connection.requestMethod = "POST"
    connection.connectTimeout = 60_000
    connection.readTimeout = 60_000
    connection.setRequestProperty("Content-Type", "application/json")
    connection.doOutput = true

    val otpType = when (type) {
        OtpType.ACTIVATION -> "ACTIVATION"
        OtpType.USER_VERIFICATION -> "USER_VERIFICATION"
    }
    val body = "{\"processId\":\"${escapeJson(processId)}\",\"otpType\":\"$otpType\"}"
    OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }

    val responseText = runCatching {
        connection.inputStream.bufferedReader().use(BufferedReader::readText)
    }.getOrElse {
        val errorText = runCatching {
            connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
        }.getOrNull()
        throw SimpleError("Failed to fetch OTP from mock endpoint ${url}: HTTP ${connection.responseCode}, body=${errorText.orEmpty()}")
    }

    val otpCode = runCatching {
        Gson().fromJson(responseText, OtpResponse::class.java)?.otpCode
    }.getOrNull()

    connection.disconnect()

    return otpCode?.takeIf { it.isNotBlank() }
        ?: throw SimpleError("Mock OTP response does not contain otpCode. Response: $responseText")
}

private data class OtpResponse(
    val otpCode: String,
)

private fun escapeJson(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

internal fun ActivationService.awaitStart(credentials: SampleCredentials, processType: String?) {
    val result = awaitWdoResult<Unit, ActivationService.Fail> { callback ->
        start(credentials, processType, callback)
    }
    result.requireSuccess { failure -> SimpleError("Activation start failed: ${failure.cause.e}") }
}

internal fun ActivationService.awaitStatusResult(): ActivationResult<ActivationService.Status> {
    return awaitWdoResult { callback -> status(callback) }
}

internal fun ActivationService.awaitStatus(): ActivationService.Status {
    return awaitStatusResult().requireSuccess { failure ->
        SimpleError("Activation status failed: ${failure.cause.e}")
    }
}

internal fun ActivationService.awaitActivateResult(otp: String?): ActivationResult<CreateActivationResult> {
    return awaitWdoResult { callback -> activate(otp, callback = callback) }
}

internal fun ActivationService.awaitActivate(otp: String?) {
    awaitActivateResult(otp).requireSuccess { failure ->
        SimpleError("Activation failed: ${failure.cause.e}")
    }
}

internal fun ConfigurationService.awaitConfiguration(processType: String): ConfigurationResponseData {
    val result = awaitWdoResult<ConfigurationResponse, ApiError> { callback ->
        getConfiguration(processType, callback)
    }
    val response = result.requireSuccess { failure ->
        SimpleError("Configuration fetch failed: ${failure.e}")
    }
    return response.responseObject
}

internal fun VerificationService.awaitStatus(): VerificationService.StatusResult {
    val result = awaitWdoResult<VerificationService.StatusResult, VerificationService.Fail> { callback ->
        status(callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("Verification status failed: ${failure.reason.e}")
    }
}

internal fun VerificationService.awaitConsent(): String {
    val result = awaitWdoResult<String, VerificationService.Fail> { callback ->
        getConsent(callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("Consent retrieval failed: ${failure.reason.e}")
    }
}

internal fun VerificationService.awaitStart(consent: ConsentResponse): VerificationService.Success {
    val result = awaitWdoResult<VerificationService.Success, VerificationService.Fail> { callback ->
        start(consent, callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("Verification start failed: ${failure.reason.e}")
    }
}

internal fun VerificationService.awaitDocumentsSetSelectedTypes(types: List<DocumentType>): VerificationService.Success {
    val result = awaitWdoResult<VerificationService.Success, VerificationService.Fail> { callback ->
        documentsSetSelectedTypes(types, callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("documentsSetSelectedTypes failed: ${failure.reason.e}")
    }
}

internal fun VerificationService.awaitDocumentsSubmit(files: List<DocumentFile>): VerificationService.Success {
    val result = awaitWdoResult<VerificationService.Success, VerificationService.Fail> { callback ->
        documentsSubmit(files, progressCallback = {}, callback = callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("documentsSubmit failed: ${failure.reason.e}")
    }
}

internal fun VerificationService.awaitPresenceCheckInit(): Map<String, Any> {
    val result = awaitWdoResult<Map<String, Any>, VerificationService.Fail> { callback ->
        presenceCheckInit(callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("presenceCheckInit failed: ${failure.reason.e}")
    }
}

internal fun VerificationService.awaitPresenceCheckSubmit(): VerificationService.Success {
    val result = awaitWdoResult<VerificationService.Success, VerificationService.Fail> { callback ->
        presenceCheckSubmit(callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("presenceCheckSubmit failed: ${failure.reason.e}")
    }
}

internal fun VerificationService.awaitRestartVerification(): VerificationService.Success {
    val result = awaitWdoResult<VerificationService.Success, VerificationService.Fail> { callback ->
        restartVerification(callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("restartVerification failed: ${failure.reason.e}")
    }
}

internal fun VerificationService.awaitCancelWholeProcess() {
    val result = awaitWdoResult<Unit, VerificationService.Fail> { callback ->
        cancelWholeProcess(callback)
    }
    result.requireSuccess { failure ->
        SimpleError("cancelWholeProcess failed: ${failure.reason.e}")
    }
}

internal fun VerificationService.awaitVerifyOtp(otp: String): VerificationService.Success {
    val result = awaitWdoResult<VerificationService.Success, VerificationService.Fail> { callback ->
        verifyOTP(otp, callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("verifyOTP failed: ${failure.reason.e}")
    }
}

internal fun VerificationService.awaitFinishActivation(
    newPowerAuthInstance: PowerAuthSDK,
    newActivationName: String,
    newPassword: io.getlime.security.powerauth.core.Password,
    validatePassword: Boolean,
    userIdentification: Any?,
): VerificationService.Success {
    val result = awaitWdoResult<VerificationService.Success, VerificationService.Fail> { callback ->
        finishActivation(
            newPowerAuthInstance,
            newActivationName,
            newPassword,
            validatePassword,
            userIdentification,
            callback,
        )
    }
    return result.requireSuccess { failure ->
        SimpleError("finishActivation failed: ${failure.reason.e}")
    }
}

internal fun PowerAuthSDK.awaitActivationStatus(appContext: Context): ActivationStatus {
    val latch = CountDownLatch(1)
    val statusRef = AtomicReference<ActivationStatus?>(null)
    val errorRef = AtomicReference<Throwable?>(null)

    fetchActivationStatusWithCallback(
        appContext,
        object : IActivationStatusListener {
            override fun onActivationStatusSucceed(status: ActivationStatus?) {
                statusRef.set(status)
                latch.countDown()
            }

            override fun onActivationStatusFailed(t: Throwable) {
                errorRef.set(t)
                latch.countDown()
            }
        },
    )

    if (!latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        throw SimpleError("Timed out waiting for PowerAuth activation status")
    }

    errorRef.get()?.let {
        throw SimpleError("PowerAuth activation status failed: ${it.message}")
    }

    return statusRef.get() ?: throw SimpleError("PowerAuth activation status returned null")
}

private fun ActivationService.getProcessIdViaReflection(): String {
    val method = javaClass.declaredMethods.firstOrNull {
        it.name.contains("getProcessId") && it.parameterCount == 0
    } ?: throw SimpleError("ActivationService processId accessor not found")

    method.isAccessible = true
    return method.invoke(this) as? String
        ?: throw SimpleError("ActivationService processId is null")
}

private fun ActivationService.awaitOtpViaReflection(): String {
    val method = javaClass.declaredMethods.firstOrNull {
        it.name.startsWith("getOTP") && it.parameterCount == 1
    } ?: throw SimpleError("ActivationService getOTP(...) is not available")

    val latch = CountDownLatch(1)
    val resultRef = AtomicReference<ActivationResult<String>?>(null)

    val callback: (WDOResult<String, ActivationService.Fail>) -> Unit = { result ->
        resultRef.set(result)
        latch.countDown()
    }

    method.isAccessible = true
    method.invoke(this, callback)

    if (!latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        throw SimpleError("Timed out waiting for ActivationService.getOTP")
    }

    val result = resultRef.get() ?: throw SimpleError("ActivationService.getOTP returned no result")
    return result.requireSuccess { failure ->
        SimpleError("ActivationService.getOTP failed: ${failure.cause.e}")
    }
}

private fun VerificationService.awaitOtpViaReflection(): String {
    val method = javaClass.declaredMethods.firstOrNull {
        it.name.startsWith("getOTP") && it.parameterCount == 1
    } ?: throw SimpleError("VerificationService getOTP(...) is not available")

    val latch = CountDownLatch(1)
    val resultRef = AtomicReference<WDOResult<String, VerificationService.Fail>?>(null)

    val callback: (WDOResult<String, VerificationService.Fail>) -> Unit = { result ->
        resultRef.set(result)
        latch.countDown()
    }

    method.isAccessible = true
    method.invoke(this, callback)

    if (!latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        throw SimpleError("Timed out waiting for VerificationService.getOTP")
    }

    val result = resultRef.get() ?: throw SimpleError("VerificationService.getOTP returned no result")
    return result.requireSuccess { failure ->
        SimpleError("VerificationService.getOTP failed: ${failure.reason.e}")
    }
}

private fun <S, F> awaitWdoResult(
    timeoutMs: Long = TEST_TIMEOUT_MS,
    call: (((WDOResult<S, F>) -> Unit) -> Unit),
): WDOResult<S, F> {
    val latch = CountDownLatch(1)
    val resultRef = AtomicReference<WDOResult<S, F>?>(null)

    call { result ->
        resultRef.set(result)
        latch.countDown()
    }

    if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
        throw SimpleError("Timed out waiting for callback result")
    }

    return resultRef.get() ?: throw SimpleError("Callback returned no result")
}

private fun <S, F> WDOResult<S, F>.requireSuccess(onFailure: (F) -> Throwable): S {
    success?.let { return it }
    failure?.let { throw onFailure(it) }
    throw SimpleError("Result has neither success nor failure value")
}
