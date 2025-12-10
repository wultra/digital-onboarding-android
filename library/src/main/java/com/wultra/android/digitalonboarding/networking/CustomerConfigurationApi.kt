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

package com.wultra.android.digitalonboarding.networking

import android.content.Context
import com.wultra.android.digitalonboarding.Utils
import com.wultra.android.digitalonboarding.networking.model.ConfigurationRequest
import com.wultra.android.digitalonboarding.networking.model.ConfigurationResponse
import com.wultra.android.powerauth.networking.Api
import com.wultra.android.powerauth.networking.E2EEConfiguration
import com.wultra.android.powerauth.networking.EndpointBasic
import com.wultra.android.powerauth.networking.IApiCallResponseListener
import io.getlime.security.powerauth.sdk.PowerAuthSDK
import okhttp3.OkHttpClient

/**
 * Class that provides all necessary communication for Configuration.
 *
 * @property powerAuthSDK Properly configured PowerAuth SDK
 *
 * @param identityServerUrl URL address of the enrollment-onboarding-server
 * @param okHttpClient Configured okHttp object
 * @param appContext Application context
 */
internal class CustomerConfigurationApi(
    identityServerUrl: String,
    okHttpClient: OkHttpClient,
    private val powerAuthSDK: PowerAuthSDK,
    appContext: Context
) : Api(identityServerUrl, okHttpClient, powerAuthSDK, Utils.defaultGsonBuilder(), appContext) {

    companion object {
        private val configurationEndpoint = EndpointBasic<ConfigurationRequest, ConfigurationResponse>(
            "api/configuration",
            E2EEConfiguration.APPLICATION_SCOPE
        )
    }

    /**
     * Retrieves Configuration.
     *
     * @param processType Process type.
     * @param listener Result listener
     */
    fun getConfiguration(processType: String, listener: IApiCallResponseListener<ConfigurationResponse>) {
        post(
            ConfigurationRequest(processType),
            configurationEndpoint,
            null,
            null,
            listener
        )
    }
}
