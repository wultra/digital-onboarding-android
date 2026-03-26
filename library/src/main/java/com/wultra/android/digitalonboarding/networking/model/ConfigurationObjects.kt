/*
 * Copyright 2025 Wultra s.r.o.
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

package com.wultra.android.digitalonboarding.networking.model

import com.google.gson.annotations.SerializedName
import com.wultra.android.digitalonboarding.DocumentType
import com.wultra.android.powerauth.networking.data.ObjectRequest
import com.wultra.android.powerauth.networking.data.ObjectResponse

/**
 * REQUEST
 * */
internal class ConfigurationRequest(processType: String): ObjectRequest<ConfigurationRequestData>(
    ConfigurationRequestData(processType)
)

internal class ConfigurationRequestData(
    @SerializedName("processType") val processType: String
)

/**
 * RESPONSE
 * */

class ConfigurationResponse(
    responseObject: ConfigurationResponseData,
    status: Status
): ObjectResponse<ConfigurationResponseData>(responseObject, status)

/** Configuration response */
data class ConfigurationResponseData(
    /** Is the onboarding process enabled */
    @SerializedName("enabled") val enabled: Boolean,
    /** Is OTP required for the first part - identification/activation. */
    @SerializedName("otpForIdentification") val otpForIdentification: Boolean,
    /** Is OTP required for the second part - identity verification. */
    @SerializedName("otpForIdentityVerification") val otpForIdentityVerification: Boolean,
    /** Is the onboarding process configured with temporary activation that should be exchanged for the permanent one. */
    @SerializedName("useTemporaryActivation") val useTemporaryActivation: Boolean,
    /** Documents required for identity verification. */
    @SerializedName("documents") val documents: ConfigurationDocuments
)

/** Documents required for identity verification */
data class ConfigurationDocuments(
    /** Number of total required documents */
    @SerializedName("totalRequiredDocumentsCount") val totalRequiredDocumentsCount: Int,
    /** Groups of documents */
    @SerializedName("groups") val groups: List<ConfigurationDocumentGroup>
)

/** Configuration for a document */
class ConfigurationDocumentGroup(
    /** Number of required documents in the group */
    @SerializedName("requiredDocumentsCount") val requiredDocumentsCount: Int,
    /** Documents in the group */
    @SerializedName("items") val items: List<ConfigurationDocument>
)

/** Group of documents in the configuration */
data class ConfigurationDocument(
    /** Type of the document. */
    @SerializedName("type") val type: DocumentType,
    /** Number of sides the document has */
    @SerializedName("sideCount") val sideCount: Int,
    /** Country of origin of the document as ISO 3166-1 alpha-3 code */
    @SerializedName("country") val country: String?
)
