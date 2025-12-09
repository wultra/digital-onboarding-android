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
     * Retrieves configuration
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
