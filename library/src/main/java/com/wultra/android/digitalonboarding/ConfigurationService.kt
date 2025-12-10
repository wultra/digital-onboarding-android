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

package com.wultra.android.digitalonboarding

import android.content.Context
import com.wultra.android.digitalonboarding.log.WDOLogger
import com.wultra.android.digitalonboarding.networking.CustomerConfigurationApi
import com.wultra.android.digitalonboarding.networking.model.ConfigurationResponse
import com.wultra.android.powerauth.networking.IApiCallResponseListener
import com.wultra.android.powerauth.networking.error.ApiError
import io.getlime.security.powerauth.sdk.PowerAuthSDK
import okhttp3.OkHttpClient

typealias ConfigurationResult<T> = WDOResult<T, ApiError>

/**
 * Digital Onboarding Configuration Service.
 *
 * Service can retrieve the configuration of the onboarding process from the server.
 * The configuration contains information about which steps are required to be performed during
 * the onboarding process and which document types are supported or required for scanning.
 *
 * @property powerAuthSDK Configured PowerAuthSDK instance. This instance needs to be without valid activation otherwise you'll get errors.
 *
 * @param identityServerUrl Base URL for service requests. Usually ending with `enrollment-onboarding-server`.
 * @param appContext Application context.
 * @param okHttpClient HTTP client for server communication.
 */
class ConfigurationService(
    identityServerUrl: String,
    appContext: Context,
    okHttpClient: OkHttpClient,
    private val powerAuthSDK: PowerAuthSDK,
) {
    /** PRIVATE PROPERTIES & CLASSES */
    private val api = CustomerConfigurationApi(identityServerUrl, okHttpClient, powerAuthSDK, appContext)

    /** PUBLIC API */

    /**
     * Fetch configuration.
     *
     * @param processType Process type.
     * @param callback Callback with the result.
     */
    fun getConfiguration(processType: String, callback: (ConfigurationResult<ConfigurationResponse>) -> Unit) {

        WDOLogger.d("Retrieving the WDO configuration")

        api.getConfiguration(
            processType,
            object: IApiCallResponseListener<ConfigurationResponse> {
                override fun onSuccess(result: ConfigurationResponse) {
                    callback(ConfigurationResult.success(result))
                }

                override fun onFailure(error: ApiError) {
                    WDOLogger.e(error)
                    callback(ConfigurationResult.failure(error))
                }
            }
        )
    }
}
