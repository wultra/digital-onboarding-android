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
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.wultra.android.digitalonboarding.networking.model.ConfigurationDocument
import com.wultra.android.digitalonboarding.networking.model.ConfigurationResponseData
import com.wultra.android.powerauth.networking.error.ApiError
import io.getlime.security.powerauth.core.ActivationStatus
import io.getlime.security.powerauth.core.Password
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
        // Builds random but valid demo credentials for integration activation.
        fun demo() = SampleCredentials(
            clientNumber = UUID.randomUUID().toString(),
            birthDate = "1989/11/17",
        )
    }
}

internal data class ServerEnvironment(
    val name: String,
    val processTypes: List<String>,
    val reKycProcessType: String,
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

internal object IntegrationTestConfig {

    private const val CONFIG_FILE_NAME = "config.json"

    // Loads test environments from androidTest assets, or returns empty when config is missing.
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
            throw SimpleError("Failed to parse androidTest/assets/${CONFIG_FILE_NAME}: ${it.message}")
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

    // Creates a fresh PowerAuth instance bound to the current environment.
    fun createNewPowerAuth(): PowerAuthSDK = newPowerAuth(appContext, environment)

    // Fetches backend configuration for the configured process type.
    fun getConfig(): ConfigurationResponseData {
        return configuration.awaitConfiguration(processType)
    }

    // Starts onboarding activation for given credentials and validates expected in-progress state.
    fun start(credentials: SampleCredentials = SampleCredentials.demo()) {
        activation.awaitStart(credentials, processType)
        if (!activation.hasActiveProcess()) {
            throw SimpleError("Activation should be active after start()")
        }

        // Verify onboarding activation status.
        val status = activation.awaitStatus()
        if (status != ActivationService.Status.ACTIVATION_IN_PROGRESS) {
            throw SimpleError("Expected ACTIVATION_IN_PROGRESS after start(), got: ${status}")
        }
    }

    // Completes activation with optional OTP and verifies local PowerAuth persistence and status.
    fun activate(otp: String?) {
        // Verify status before activation.
        val status = activation.awaitStatus()
        if (status != ActivationService.Status.ACTIVATION_IN_PROGRESS) {
            throw SimpleError("Expected ACTIVATION_IN_PROGRESS before activate(), got: ${status}")
        }

        // Activate PowerAuth.
        activation.awaitActivate(otp)

        // Persist with random password.
        val persistCode = powerAuth.persistActivationWithPassword(appContext, UUID.randomUUID().toString())
        if (persistCode != PowerAuthErrorCodes.SUCCEED) {
            throw SimpleError("persistActivationWithPassword failed with code: ${persistCode}")
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

    // Runs the start + activate bootstrap flow and returns config with consent requirement flag.
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

    // Runs startAndActivate() and then completes the whole verification flow
    fun startAndActivateAndVerify(credentials: SampleCredentials = SampleCredentials.demo()): PowerAuthSDK? {
        val (config, consentRequired) = startAndActivate(credentials) ?: return null

        val startedVerification = verification.awaitStart(
            if (consentRequired) ConsentResponse.APPROVED else ConsentResponse.NOT_REQUIRED,
        )
        if (startedVerification.state.state != VerificationState.DOCUMENTS_TO_SCAN_SELECT) {
            throw SimpleError("Expected DOCUMENTS_TO_SCAN_SELECT after start(), got: ${startedVerification.state.state}")
        }

        val documentsToScan = config.getDocumentsToScan()
        verification.awaitDocumentsSetSelectedTypes(documentsToScan.map { it.patchedType() })

        if (!environment.servicesMock) {
            Log.i("IntegrationTestSupport", "[$processType] Cannot complete verification to SUCCESS — servicesMock is disabled for '${environment.name}'")
            return null
        }

        fun waitForNonProcessingStatus(): VerificationService.StatusResult {
            var statusResult = verification.awaitStatus()
            var attempts = 0
            val sleepDuration = 3_000L
            val maxAttempts = 10
            while (statusResult.state.state == VerificationState.PROCESSING && attempts < maxAttempts) {
                Thread.sleep(sleepDuration)
                attempts += 1
                statusResult = verification.awaitStatus()
            }
            if (statusResult.state.state == VerificationState.PROCESSING) {
                throw SimpleError(
                    "[$processType] Timed out waiting for non-processing verification state " +
                        "after ${maxAttempts * sleepDuration / 1000} seconds",
                )
            }
            return statusResult
        }

        var statusResult = waitForNonProcessingStatus()
        var state = statusResult.state

        if (state.state == VerificationState.SCAN_DOCUMENT) {
            for (document in documentsToScan) {
                verification.awaitDocumentsSubmit(document.uploadFiles())
                statusResult = waitForNonProcessingStatus()
                state = statusResult.state
            }
        }

        if (state.state == VerificationState.PRESENCE_CHECK) {
            verification.awaitPresenceCheckInit()
            verification.awaitPresenceCheckSubmit()
            statusResult = waitForNonProcessingStatus()
            state = statusResult.state
        }

        if (state.state == VerificationState.OTP) {
            val otp = getVerificationOtp()
            val otpResult = verification.awaitVerifyOtp(otp)
            state = otpResult.state
            if (state.state == VerificationState.PROCESSING) {
                statusResult = waitForNonProcessingStatus()
                state = statusResult.state
            }
        }

        var activePowerAuth = powerAuth

        if (state.state == VerificationState.ACTIVATION_FINISH) {
            val newPowerAuth = createNewPowerAuth()
            val finishResult = verification.awaitFinishActivation(
                newPowerAuthInstance = newPowerAuth,
                newActivationName = "Android Integration Test",
                newPassword = Password("1234"),
                validatePassword = false,
                userIdentification = null,
            )
            state = finishResult.state
            activePowerAuth = newPowerAuth
        }

        if (state.state != VerificationState.SUCCESS) {
            throw SimpleError("[$processType] Expected SUCCESS after completing verification, got: ${state.state}")
        }

        return activePowerAuth
    }

    // Asserts that current verification state equals expected state.
    fun assertVerificationState(expected: VerificationState) {
        val status = verification.awaitStatus()
        if (status.state.state != expected) {
            throw SimpleError("Unexpected verification state. Expected: ${expected}, got: ${status.state.state}")
        }
    }

    // Obtains activation OTP using the environment-selected OTP strategy.
    fun getActivationOtp(): String {
        val strategy = environment.otpStrategy()
        return activation.awaitOtpViaDemoEndpoints(strategy)
    }

    // Obtains verification OTP using the environment-selected OTP strategy.
    fun getVerificationOtp(): String {
        val strategy = environment.otpStrategy()
        return verification.awaitOtpViaDemoEndpoints(strategy)
    }
}

// Selects required documents and fills any missing required slots with unique fallback document types.
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

// Normalizes historical typo in document type naming used by backend configuration.
internal fun ConfigurationDocument.patchedType(): DocumentType {
    return if (type == "DRIVING_LICENCE") "DRIVING_LICENSE" else type
}

// Returns mock document-upload data expected by demo/mock scan providers used in integration tests.
internal fun ConfigurationDocument.getMockDocumentToUpload(side: DocumentSide): DocumentFile {
    val mockType = when (patchedType()) {
        "DRIVING_LICENSE" -> "Dl"
        "ID_CARD" -> "Id"
        "PASSPORT" -> "Passport"
        else -> throw SimpleError("Unsupported ${patchedType()} document type for testing")
    }

    val json = "{\"type\": \"${mockType}\", \"isoAlpha3CountryCode\": \"${country ?: "CZE"}\"}"
    val data = json.toByteArray(Charsets.UTF_8)

    return DocumentFile(
        data = data,
        type = patchedType(),
        side = side,
        originalDocumentId = null,
    )
}

// Builds the mock file(s) for a document upload, covering both sides when the document requires it.
internal fun ConfigurationDocument.uploadFiles(): List<DocumentFile> {
    val files = mutableListOf(getMockDocumentToUpload(DocumentSide.FRONT))
    if (sideCount == 2) {
        files += getMockDocumentToUpload(DocumentSide.BACK)
    }
    return files
}

// Detects whether API error maps to known PowerAuth transport/runtime error families.
internal fun ApiError.isPowerAuthError(): Boolean {
    return e is FailedApiException || e is PowerAuthErrorException
}

// Resolves OTP retrieval strategy from environment configuration.
internal fun ServerEnvironment.otpStrategy(): GetOTPEndpointStrategy {
    return when {
        otpMock.equals("ESO", ignoreCase = true) -> GetOTPEndpointStrategy.Eso
        otpMock.equals("AUTO", ignoreCase = true) -> GetOTPEndpointStrategy.AutomaticMock
        else -> GetOTPEndpointStrategy.Custom(URL(otpMock))
    }
}

// Creates a new PowerAuth SDK instance for the given environment.
internal fun newPowerAuth(appContext: Context, environment: ServerEnvironment): PowerAuthSDK {
    val configuration = PowerAuthConfiguration.Builder(
        UUID.randomUUID().toString(),
        environment.esUrl,
        environment.mobileConfig,
    ).build()
    return PowerAuthSDK.Builder(configuration).build(appContext)
}

// Executes a synchronous JSON HTTP request and returns response body on 2xx status.
internal fun executeHttp(url: URL, method: String, authorization: String? = null, body: String? = null): String {
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = method
    connection.connectTimeout = 60_000
    connection.readTimeout = 60_000
    connection.setRequestProperty("Content-Type", "application/json")
    if (authorization != null) {
        connection.setRequestProperty("Authorization", "Basic ${authorization}")
    }

    if (body != null) {
        connection.doOutput = true
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
    }

    val code = connection.responseCode
    if (code !in 200..299) {
        val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        connection.disconnect()
        throw SimpleError("HTTP ${method} ${url} failed with code ${code}: ${error}")
    }

    val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
    connection.disconnect()
    return response
}

// Await wrappers below convert async callback-based service APIs into blocking calls used by tests.
// They unify timeout handling, convert service failures into SimpleError, and return unwrapped success values.
// This keeps integration tests linear and easier to read while preserving explicit failure diagnostics.

// Starts activation and blocks until callback returns success or failure.
internal fun ActivationService.awaitStart(credentials: SampleCredentials, processType: String?) {
    val result = awaitStartResult(credentials, processType)
    result.requireSuccess { failure -> SimpleError("Activation start failed: ${failure.cause.e}") }
}

// Same as awaitStart, but returns the raw callback result envelope instead of throwing on failure.
internal fun ActivationService.awaitStartResult(credentials: SampleCredentials, processType: String?): ActivationResult<Unit> {
    return awaitWdoResult { callback ->
        start(credentials, processType, callback)
    }
}

// Fetches activation status with callback result envelope.
internal fun ActivationService.awaitStatusResult(): ActivationResult<ActivationService.Status> {
    return awaitWdoResult { callback -> status(callback) }
}

// Fetches activation status and throws when backend reports failure.
internal fun ActivationService.awaitStatus(): ActivationService.Status {
    return awaitStatusResult().requireSuccess { failure ->
        SimpleError("Activation status failed: ${failure.cause.e}")
    }
}

// Activates onboarding and returns callback result envelope.
internal fun ActivationService.awaitActivateResult(otp: String?): ActivationResult<CreateActivationResult> {
    return awaitWdoResult { callback -> activate(otp, callback = callback) }
}

// Activates onboarding and returns successful activation payload.
internal fun ActivationService.awaitActivate(otp: String?) {
    awaitActivateResult(otp).requireSuccess { failure ->
        SimpleError("Activation failed: ${failure.cause.e}")
    }
}

// Loads onboarding configuration for selected process type.
internal fun ConfigurationService.awaitConfiguration(processType: String): ConfigurationResponseData {
    val result = awaitWdoResult { callback ->
        getConfiguration(processType, callback)
    }
    val response = result.requireSuccess { failure ->
        SimpleError("Configuration fetch failed: ${failure.e}")
    }
    return response.responseObject
}

// Returns current verification status.
internal fun VerificationService.awaitStatus(): VerificationService.StatusResult {
    return awaitStatusResult().requireSuccess { failure ->
        SimpleError("Verification status failed: ${failure.reason.e}")
    }
}

// Same as awaitStatus, but returns the raw callback result envelope instead of throwing on failure.
internal fun VerificationService.awaitStatusResult(): VerificationStatusResult {
    return awaitWdoResult { callback ->
        status(callback)
    }
}

// Loads consent text/content required by verification intro.
internal fun VerificationService.awaitConsent(): String {
    val result = awaitWdoResult { callback ->
        getConsent(callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("Consent retrieval failed: ${failure.reason.e}")
    }
}

// Starts verification workflow with selected consent response.
internal fun VerificationService.awaitStart(consent: ConsentResponse): VerificationService.Success {
    return awaitStartResult(consent).requireSuccess { failure ->
        SimpleError("Verification start failed: ${failure.reason.e}")
    }
}

// Same as awaitStart, but returns the raw callback result envelope instead of throwing on failure.
internal fun VerificationService.awaitStartResult(consent: ConsentResponse): VerificationResult {
    return awaitWdoResult { callback ->
        start(consent, callback)
    }
}

// Starts a Re-KYC (re-verification) process for an already active PowerAuth instance and returns the
// resulting verification status (same shape as awaitStatus()/status()).
internal fun VerificationService.awaitStartReVerification(processType: String? = null): VerificationService.StatusResult {
    val result = awaitStartReVerificationResult(processType)
    return result.requireSuccess { failure ->
        SimpleError("startReVerification failed: ${failure.reason.e}")
    }
}

// Same as awaitStartReVerification, but returns the raw callback result envelope instead of
// throwing on failure - useful when the call is expected to (or might) fail.
internal fun VerificationService.awaitStartReVerificationResult(processType: String? = null): VerificationStatusResult {
    return awaitWdoResult { callback ->
        startReVerification(processType, callback)
    }
}

// Submits selected document types for scanning step.
internal fun VerificationService.awaitDocumentsSetSelectedTypes(types: List<DocumentType>): VerificationService.Success {
    val result = awaitWdoResult { callback ->
        documentsSetSelectedTypes(types, callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("documentsSetSelectedTypes failed: ${failure.reason.e}")
    }
}

// Uploads document files for the current scan step.
internal fun VerificationService.awaitDocumentsSubmit(files: List<DocumentFile>): VerificationService.Success {
    val result = awaitWdoResult { callback ->
        documentsSubmit(files, progressCallback = {}, callback = callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("documentsSubmit failed: ${failure.reason.e}")
    }
}

// Initializes presence-check challenge payload.
internal fun VerificationService.awaitPresenceCheckInit(): Map<String, Any> {
    val result = awaitWdoResult { callback ->
        presenceCheckInit(callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("presenceCheckInit failed: ${failure.reason.e}")
    }
}

// Submits presence-check confirmation.
internal fun VerificationService.awaitPresenceCheckSubmit(): VerificationService.Success {
    val result = awaitWdoResult { callback ->
        presenceCheckSubmit(callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("presenceCheckSubmit failed: ${failure.reason.e}")
    }
}

// Restarts verification workflow from intro-like state.
internal fun VerificationService.awaitRestartVerification(): VerificationService.Success {
    val result = awaitWdoResult { callback ->
        restartVerification(callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("restartVerification failed: ${failure.reason.e}")
    }
}

// Cancels the whole verification process and waits for backend confirmation.
internal fun VerificationService.awaitCancelWholeProcess() {
    val result = awaitWdoResult { callback ->
        cancelWholeProcess(callback)
    }
    result.requireSuccess { failure ->
        SimpleError("cancelWholeProcess failed: ${failure.reason.e}")
    }
}

// Verifies OTP code in verification OTP step.
internal fun VerificationService.awaitVerifyOtp(otp: String): VerificationService.Success {
    val result = awaitWdoResult { callback ->
        verifyOTP(otp, callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("verifyOTP failed: ${failure.reason.e}")
    }
}

// Finalizes activation in verification flow using new PowerAuth instance.
internal fun VerificationService.awaitFinishActivation(
    newPowerAuthInstance: PowerAuthSDK,
    newActivationName: String,
    newPassword: io.getlime.security.powerauth.core.Password,
    validatePassword: Boolean,
    userIdentification: Any?,
): VerificationService.Success {
    val result = awaitWdoResult { callback ->
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

// Loads current PowerAuth activation status using callback API and timeout protection.
internal fun PowerAuthSDK.awaitActivationStatus(appContext: Context): ActivationStatus {
    val latch = CountDownLatch(1)
    val statusRef = AtomicReference<ActivationStatus?>(null)
    val errorRef = AtomicReference<Throwable?>(null)

    fetchActivationStatusWithCallback(
        appContext,
        object : IActivationStatusListener {
            // Stores successful activation status from callback.
            override fun onActivationStatusSucceed(status: ActivationStatus?) {
                statusRef.set(status)
                latch.countDown()
            }

            // Stores callback failure so caller can receive a descriptive error.
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

// Calls DemoEndpoints OTP endpoint for activation flow.
private fun ActivationService.awaitOtpViaDemoEndpoints(strategy: GetOTPEndpointStrategy): String {
    val result = awaitWdoResult<String, ApiError> { callback ->
        DemoEndpoints.getOTP(this, strategy, callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("ActivationService.getOTP failed: ${failure.e.message}")
    }
}

// Calls DemoEndpoints OTP endpoint for verification flow.
private fun VerificationService.awaitOtpViaDemoEndpoints(strategy: GetOTPEndpointStrategy): String {
    val result = awaitWdoResult<String, ApiError> { callback ->
        DemoEndpoints.getOTP(this, strategy, callback)
    }
    return result.requireSuccess { failure ->
        SimpleError("VerificationService.getOTP failed: ${failure.e.message}")
    }
}

// Waits for callback-based WDO result and returns it, enforcing timeout.
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

// Unwraps successful result value or throws mapped failure error.
private fun <S, F> WDOResult<S, F>.requireSuccess(onFailure: (F) -> Throwable): S {
    success?.let { return it }
    failure?.let { throw onFailure(it) }
    throw SimpleError("Result has neither success nor failure value")
}
