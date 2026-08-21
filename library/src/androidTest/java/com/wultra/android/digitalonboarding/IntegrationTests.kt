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

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wultra.android.digitalonboarding.log.WDOLogger
import io.getlime.security.powerauth.core.ActivationStatus
import io.getlime.security.powerauth.core.Password
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// NOTE TO THE TESTS:
// These tests expect to run against enrollment-onboarding-server connected to
// mock providers for document scan and presence check.
// Real documents are not uploaded to the mock server. Instead, tests send JSON
// instructions describing the mocked document payload.

@RunWith(AndroidJUnit4::class)
class IntegrationTests {

    private val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val environments: List<ServerEnvironment> by lazy { IntegrationTestConfig.loadEnvironments() }

    @Before
    fun before() {
        WDOLogger.verboseLevel = WDOLogger.VerboseLevel.DEBUG
    }

    @Test
    fun testConfig() {
        runForAllEnvironments { _, helper ->
            val config = helper.getConfig()
            assertTrue("Configuration should include at least one document group", config.documents.groups.isNotEmpty())
        }
    }

    @Test
    fun testStatusBeforeStart() {
        runForAllEnvironments { _, helper ->
            val label = processLabel(helper)
            val result = helper.activation.awaitStatusResult()
            if (result.failure == null) {
                fail("${label} Expected status() to fail before start()")
            }
            val failure = result.failure!!
            assertTrue(
                "${label} Expected ActivationNotRunningException but got ${failure.cause.e}",
                failure.cause.e == ActivationService.ActivationNotRunningException,
            )
        }
    }

    @Test
    fun activationFail() {
        runForAllEnvironments { _, helper ->
            val label = processLabel(helper)
            val config = helper.getConfig()
            if (!config.otpForIdentification) {
                // There is a backend bug where OTP can be ignored when not required,
                // so this failure scenario cannot be simulated reliably.
                // https://github.com/wultra/powerauth-server/issues/2249
                Log.i("IntegrationTests", "${label} Skipping test as OTP is not required")
                return@runForAllEnvironments
            }

            helper.start()
            val result = helper.activation.awaitActivateResult(otp = null)
            if (result.failure == null) {
                fail("${label} Activation should fail when OTP is missing")
            }
            val failure = result.failure!!
            assertTrue(
                "${label} Expected PowerAuth-related error, got: ${failure.cause.e}",
                failure.cause.isPowerAuthError(),
            )
        }
    }

    @Test
    fun fullOnboardingFlow() {
        // We test as far as we can until we hit environment mocking limits.
        runForAllEnvironments { env, helper ->
            val label = processLabel(helper)

            Log.i("IntegrationTests", "${label} Starting test for environment '${env.name}'")

            // Expect default language.
            assertEquals("en", helper.verification.acceptLanguage)

            // Verify language change.
            helper.verification.acceptLanguage = "cs"
            assertEquals("cs", helper.verification.acceptLanguage)

            val started = helper.startAndActivate()
            val config = started.first
            val consentRequired = started.second

            if (consentRequired) {
                val consentContent = helper.verification.awaitConsent()
                assertTrue("Consent content should not be empty", consentContent.isNotBlank())
            }

            if (consentRequired) {
                // If consent is required, first reject it to validate intro fallback.
                val rejected = helper.verification.awaitStart(ConsentResponse.DECLINED)
                assertEquals(VerificationState.INTRO, rejected.state.state)
            }

            val startedVerification = helper.verification.awaitStart(
                if (consentRequired) ConsentResponse.APPROVED else ConsentResponse.NOT_REQUIRED,
            )
            assertEquals(VerificationState.DOCUMENTS_TO_SCAN_SELECT, startedVerification.state.state)
            helper.assertVerificationState(VerificationState.DOCUMENTS_TO_SCAN_SELECT)

            val documentsToScan = config.getDocumentsToScan()
            val selected = helper.verification.awaitDocumentsSetSelectedTypes(
                documentsToScan.map { it.patchedType() },
            )
            assertEquals(VerificationState.SCAN_DOCUMENT, selected.state.state)
            helper.assertVerificationState(VerificationState.SCAN_DOCUMENT)

            val selectedProcess = (selected.state as? VerificationStateScanDocumentData)?.scanDocumentProcess
                ?: throw AssertionError("Expected SCAN_DOCUMENT result with process payload")
            for (document in documentsToScan) {
                assertTrue(
                    "${label} Selected document ${document.patchedType()} must be present in scan process",
                    selectedProcess.documents.any { it.type == document.patchedType() },
                )
            }

            fun waitForNonProcessingStatus(): VerificationService.StatusResult {
                var statusResult = helper.verification.awaitStatus()
                var attempts = 0
                val sleepDuration = 3_000L
                val maxAttempts = 10
                while (statusResult.state.state == VerificationState.PROCESSING && attempts < maxAttempts) {
                    Thread.sleep(sleepDuration)
                    attempts += 1
                    statusResult = helper.verification.awaitStatus()
                }
                if (statusResult.state.state == VerificationState.PROCESSING) {
                    throw SimpleError(
                        "${label} Timed out waiting for non-processing verification state " +
                            "after ${maxAttempts * sleepDuration / 1000} seconds",
                    )
                }
                return statusResult
            }

            // App restart simulation: recreate helper/service with the same PowerAuth instance
            // and verify process continuity.
            val recreated = TestHelper(
                appContext = appContext,
                environment = env,
                processType = helper.processType,
                customPaInstance = helper.powerAuth,
            )
            val recreatedStatus = recreated.verification.awaitStatus()
            val recreatedProcess = (recreatedStatus.state as? VerificationStateScanDocumentData)?.scanDocumentProcess
                ?: throw AssertionError("${label} Expected SCAN_DOCUMENT after service recreation")
            for (document in documentsToScan) {
                assertTrue(
                    "${label} Recreated scan process should contain ${document.patchedType()}",
                    recreatedProcess.documents.any { it.type == document.patchedType() },
                )
            }

            if (!env.servicesMock) {
                // If services are not mocked, we cannot continue past document upload.
                // We still test re-upload and originalDocumentId auto-resolution.
                Log.i(
                    "IntegrationTests",
                    "${label} Skipping rest of onboarding flow — servicesMock is disabled for '${env.name}'",
                )
                for (document in documentsToScan) {
                    val files = document.uploadFiles()
                    helper.verification.awaitDocumentsSubmit(files)
                    waitForNonProcessingStatus()
                    helper.verification.awaitDocumentsSubmit(files)
                }
                return@runForAllEnvironments
            }

            var statusResult = waitForNonProcessingStatus()
            var state = statusResult.state

            // Handle document re-scan when documents are rejected.
            if (state.state == VerificationState.SCAN_DOCUMENT) {
                for (document in documentsToScan) {
                    helper.verification.awaitDocumentsSubmit(document.uploadFiles())
                    statusResult = waitForNonProcessingStatus()
                    state = statusResult.state
                }
            }

            // Presence check.
            if (state.state == VerificationState.PRESENCE_CHECK) {
                helper.verification.awaitPresenceCheckInit()
                helper.verification.awaitPresenceCheckSubmit()
                statusResult = waitForNonProcessingStatus()
                state = statusResult.state
            }

            // OTP verification.
            if (state.state == VerificationState.OTP) {
                val otp = helper.getVerificationOtp()
                val otpResult = helper.verification.awaitVerifyOtp(otp)
                state = otpResult.state
                if (state.state == VerificationState.PROCESSING) {
                    statusResult = waitForNonProcessingStatus()
                    state = statusResult.state
                }
            }

            // Finish activation (optional, depends on backend configuration).
            if (state.state == VerificationState.ACTIVATION_FINISH) {
                val newPowerAuth = helper.createNewPowerAuth()
                val finishResult = helper.verification.awaitFinishActivation(
                    newPowerAuthInstance = newPowerAuth,
                    newActivationName = "Android Integration Test",
                    newPassword = Password("1234"),
                    validatePassword = false,
                    userIdentification = null,
                )
                state = finishResult.state

                val originalStatus = helper.powerAuth.awaitActivationStatus(appContext)
                assertEquals(ActivationStatus.State_Removed, originalStatus.state)

                val newStatus = newPowerAuth.awaitActivationStatus(appContext)
                assertEquals(ActivationStatus.State_Active, newStatus.state)
                assertFalse(newStatus.needVerification())
            }

            // At this point we should be in success or explicit terminal failure state.
            when (state.state) {
                VerificationState.SUCCESS -> {
                    Log.i("IntegrationTests", "${label} Full onboarding flow completed successfully")
                }
                VerificationState.FAILED -> fail("${label} Verification ended in FAILED state")
                VerificationState.ENDSTATE -> fail("${label} Verification ended in ENDSTATE unexpectedly")
                else -> fail("${label} Unexpected final state: ${state.state}")
            }
        }
    }

    @Test
    fun cancelVerification() {
        runForAllEnvironments { _, helper ->
            // Start valid onboarding.
            val (_, consentRequired) = helper.startAndActivate()

            helper.verification.awaitStart(
                if (consentRequired) ConsentResponse.APPROVED else ConsentResponse.NOT_REQUIRED,
            )
            helper.assertVerificationState(VerificationState.DOCUMENTS_TO_SCAN_SELECT)

            // Restart and verify intro.
            val restarted = helper.verification.awaitRestartVerification()
            assertEquals(VerificationState.INTRO, restarted.state.state)
            helper.assertVerificationState(VerificationState.INTRO)

            // Start again and verify state.
            helper.verification.awaitStart(
                if (consentRequired) ConsentResponse.APPROVED else ConsentResponse.NOT_REQUIRED,
            )
            helper.assertVerificationState(VerificationState.DOCUMENTS_TO_SCAN_SELECT)

            // Cancel whole process.
            helper.verification.awaitCancelWholeProcess()

            // After cancellation, PowerAuth should be removed.
            val paStatus = helper.powerAuth.awaitActivationStatus(appContext)
            assertEquals(ActivationStatus.State_Removed, paStatus.state)
        }
    }

    @Test
    fun startReVerificationAfterOnboardingActivation() {
        runForAllEnvironments { env, helper ->
            val label = processLabel(helper)

            // Active and verified PowerAuth
            val pa = helper.startAndActivateAndVerify() ?: return@runForAllEnvironments

            val preReKycStatus = pa.awaitActivationStatus(appContext)
            assertFalse("Expected needVerification() == false before starting Re-KYC", preReKycStatus.needVerification())

            // Re-KYC operates on the now-active PowerAuth instance, which may differ from
            // helper.powerAuth if ACTIVATION_FINISH swapped to a new one.
            val reKycHelper = TestHelper(
                appContext = appContext,
                environment = env,
                processType = env.reKycProcessType,
                customPaInstance = pa,
            )

            val reKycResult = reKycHelper.verification.awaitStartReVerification(env.reKycProcessType)
            assertTrue(
                "Expected INTRO state after startReVerification, got: ${reKycResult.state.state}",
                reKycResult.state is VerificationStateIntroData,
            )

            // startReVerification alone must not flip needVerification() yet - only `/api/identity/init`
            // (triggered by the subsequent `VerificationService.start(...)` call) does that.
            val statusAfterReVerification = pa.awaitActivationStatus(appContext)
            assertFalse(
                "startReVerification alone must not flip needVerification() before identity/init",
                statusAfterReVerification.needVerification(),
            )

            val introState = reKycResult.state as VerificationStateIntroData
            reKycHelper.verification.awaitStart(
                if (introState.consentRequired) ConsentResponse.APPROVED else ConsentResponse.NOT_REQUIRED,
            )
            reKycHelper.assertVerificationState(VerificationState.DOCUMENTS_TO_SCAN_SELECT)

            // The reliable check regardless of which flag name the backend uses.
            val status = pa.awaitActivationStatus(appContext)
            assertTrue("Expected needVerification() after Re-KYC start()", status.needVerification())

            // Drive the re-verification flow to completion, same as a regular onboarding verification.
            val reKycConfig = reKycHelper.getConfig()
            val reKycActivePowerAuth = reKycHelper.driveVerificationToSuccess(reKycConfig) ?: return@runForAllEnvironments

            // Once re-verification succeeds, needVerification() should be cleared again.
            val finalStatus = reKycActivePowerAuth.awaitActivationStatus(appContext)
            assertFalse("${label} Expected needVerification() == false after Re-KYC success", finalStatus.needVerification())

            Log.i("IntegrationTests", "${label} Re-KYC test successful")
        }
    }

    @Test
    fun startReVerificationCalledTwiceInARow() {
        runForAllEnvironments { env, helper ->
            val label = processLabel(helper)
            val pa = helper.startAndActivateAndVerify() ?: return@runForAllEnvironments

            val reKycHelper = TestHelper(
                appContext = appContext,
                environment = env,
                processType = env.reKycProcessType,
                customPaInstance = pa,
            )

            val first = reKycHelper.verification.awaitStartReVerification(env.reKycProcessType)
            assertTrue(
                "${label} Expected INTRO state after first startReVerification, got: ${first.state.state}",
                first.state is VerificationStateIntroData,
            )

            val second = reKycHelper.verification.awaitStartReVerification(env.reKycProcessType)
            assertTrue(
                "${label} Expected INTRO state after second startReVerification, got: ${second.state.state}",
                second.state is VerificationStateIntroData,
            )
        }
    }

    @Test
    fun startReVerificationWithUnknownProcessTypeFails() {
        runForAllEnvironments { env, helper ->
            val label = processLabel(helper)
            val pa = helper.startAndActivateAndVerify() ?: return@runForAllEnvironments

            val reKycHelper = TestHelper(
                appContext = appContext,
                environment = env,
                processType = env.reKycProcessType,
                customPaInstance = pa,
            )
            val unknownProcessType = "unknown-re-kyc-${java.util.UUID.randomUUID()}"

            val result = reKycHelper.verification.awaitStartReVerificationResult(unknownProcessType)
            val failure = result.failure
            if (failure == null) {
                fail(
                    "${label} Expected startReVerification with unknown process type '$unknownProcessType' " +
                        "to fail, got state: ${result.success?.state?.state}",
                )
                return@runForAllEnvironments
            }
            assertFalse(
                "${label} Failure should not be a connectivity/offline error: ${failure.reason.e}",
                failure.reason.isOffline(),
            )
            Log.i("IntegrationTests", "${label} Expected failure for unknown re-KYC process type: ${failure.reason.e}")
        }
    }

    private fun runForAllEnvironments(block: (ServerEnvironment, TestHelper) -> Unit) {
        assumeTrue(
            "No androidTest/assets/config.json present or it does not contain environments. " +
                "Create config.json based on config.example.json before running integration tests.",
            environments.isNotEmpty(),
        )

        for (environment in environments) {
            for (processType in environment.processTypes) {
                val helper = TestHelper(
                    appContext = appContext,
                    environment = environment,
                    processType = processType,
                )
                block(environment, helper)
            }
        }
    }

    private fun processLabel(helper: TestHelper): String {
        return "[${helper.processType}]"
    }
}
