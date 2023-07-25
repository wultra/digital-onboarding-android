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

package com.wultra.android.digitalonboarding

import com.wultra.android.powerauth.networking.error.ApiError
import com.wultra.android.powerauth.networking.error.ApiErrorException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException

val delaysCoroutineName = CoroutineName("DelaysCoroutineDispatcher")

val delaysCoroutineScope = CoroutineScope(Dispatchers.Main + delaysCoroutineName)

fun ApiError.toException() = ApiErrorException(this)

fun ApiError.isOffline(): Boolean {
    fun Throwable.isOffline() = this is ConnectException || this is UnknownHostException || this is SocketException
    return e.isOffline() || e.cause?.isOffline() == true
}
