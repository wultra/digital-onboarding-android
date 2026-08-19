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

import com.wultra.android.digitalonboarding.networking.model.ActivationType
import com.wultra.android.digitalonboarding.networking.model.ConfigurationResponseData
import com.wultra.android.digitalonboarding.networking.model.IdentityVerificationStatus
import com.wultra.android.digitalonboarding.networking.model.ProcessResponseData
import com.wultra.android.digitalonboarding.networking.model.VerificationPhase
import com.wultra.android.digitalonboarding.networking.model.VerificationStatusResponseData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkModelDecodingTest {

    private val gson = Utils.defaultGsonBuilder().create()

    @Test
    fun configurationResponseDecodesOtpResendPeriodWhenPresent() {
        val response = gson.fromJson(
            """
            {
              "enabled": true,
              "otpForIdentification": false,
              "otpForIdentityVerification": true,
              "useTemporaryActivation": true,
              "otpResendPeriodSeconds": 30,
              "documents": {
                "totalRequiredDocumentsCount": 1,
                "groups": [
                  {
                    "requiredDocumentsCount": 1,
                    "items": [
                      { "type": "ID_CARD", "sideCount": 2, "country": "CZE" }
                    ]
                  }
                ]
              }
            }
            """.trimIndent(),
            ConfigurationResponseData::class.java,
        )

        assertEquals(30, response.otpResendPeriodSeconds)
        assertEquals(1, response.documents.totalRequiredDocumentsCount)
    }

    @Test
    fun configurationResponseDecodesWithoutOtpResendPeriodForOlderBackends() {
        val response = gson.fromJson(
            """
            {
              "enabled": true,
              "otpForIdentification": false,
              "otpForIdentityVerification": true,
              "useTemporaryActivation": true,
              "documents": {
                "totalRequiredDocumentsCount": 1,
                "groups": [
                  {
                    "requiredDocumentsCount": 1,
                    "items": [
                      { "type": "ID_CARD", "sideCount": 2, "country": "CZE" }
                    ]
                  }
                ]
              }
            }
            """.trimIndent(),
            ConfigurationResponseData::class.java,
        )

        assertNull(response.otpResendPeriodSeconds)
        assertEquals(1, response.documents.totalRequiredDocumentsCount)
    }

    @Test
    fun verificationStatusResponseDecodesWithoutDeprecatedConfig() {
        val response = gson.fromJson(
            """
            {
              "processId": "abc-123",
              "processType": "onboarding",
              "identityVerificationStatus": "IN_PROGRESS",
              "identityVerificationPhase": "DOCUMENT_UPLOAD",
              "consentRequired": false
            }
            """.trimIndent(),
            VerificationStatusResponseData::class.java,
        )

        assertEquals("abc-123", response.processId)
        assertEquals("onboarding", response.processType)
        assertEquals(IdentityVerificationStatus.IN_PROGRESS, response.status)
        assertEquals(VerificationPhase.DOCUMENT_UPLOAD, response.phase)
        assertEquals(false, response.consentRequired)
        assertNull(response.rejectReason)
    }

    @Test
    fun verificationStatusResponseIgnoresDeprecatedConfigWhenPresent() {
        val response = gson.fromJson(
            """
            {
              "processId": "abc-123",
              "processType": "onboarding",
              "identityVerificationStatus": "IN_PROGRESS",
              "identityVerificationPhase": "DOCUMENT_UPLOAD",
              "consentRequired": false,
              "config": {
                "otpResendPeriodSeconds": 60
              }
            }
            """.trimIndent(),
            VerificationStatusResponseData::class.java,
        )

        assertEquals("abc-123", response.processId)
        assertEquals("onboarding", response.processType)
        assertEquals(IdentityVerificationStatus.IN_PROGRESS, response.status)
        assertEquals(VerificationPhase.DOCUMENT_UPLOAD, response.phase)
        assertEquals(false, response.consentRequired)
        assertNull(response.rejectReason)
    }

    @Test
    fun processResponseDataDecodesActivationTypeWhenPresent() {
        val response = gson.fromJson(
            """
            {
              "processId": "abc-123",
              "onboardingStatus": "VERIFICATION_IN_PROGRESS",
              "activationCode": null,
              "activationType": "ACTIVATION_ALREADY_EXISTS"
            }
            """.trimIndent(),
            ProcessResponseData::class.java,
        )

        assertEquals("abc-123", response.processId)
        assertEquals(ActivationType.ACTIVATION_ALREADY_EXISTS, response.activationType)
    }

    @Test
    fun processResponseDataDecodesWithoutActivationTypeForOlderBackends() {
        val response = gson.fromJson(
            """
            {
              "processId": "abc-123",
              "onboardingStatus": "VERIFICATION_IN_PROGRESS",
              "activationCode": null
            }
            """.trimIndent(),
            ProcessResponseData::class.java,
        )

        assertEquals("abc-123", response.processId)
        assertNull(response.activationType)
    }
}
