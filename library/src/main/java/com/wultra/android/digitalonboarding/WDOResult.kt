/*
 * Copyright (c) 2024, Wultra s.r.o. (www.wultra.com).
 *
 * All rights reserved. This source code can be used only for purposes specified
 * by the given license contract signed by the rightful deputy of Wultra s.r.o.
 * This source code can be used only by the owner of the license.
 *
 * Any disputes arising in respect of this agreement (license) shall be brought
 * before the Municipal Court of Prague.
 */

package com.wultra.android.digitalonboarding

class WDOResult<S, F> {

    companion object {
        fun <S, F>success(value: S) = WDOResult<S, F>(value, null)
        fun <S, F>failure(value: F) = WDOResult<S, F>(null, value)
    }

    val isSuccess: Boolean
        get() = success != null

    val isFailure: Boolean
        get() = !isSuccess

    val success: S?
    val failure: F?

    internal constructor(success: S?, failure: F?) {
        this.success = success
        this.failure = failure
    }

    fun onSuccess(block: (S) -> Unit): WDOResult<S, F> {
        success?.let(block)
        return this
    }

    fun onFailure(block: (F) -> Unit): WDOResult<S, F> {
        failure?.let(block)
        return this
    }
}
