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

import android.util.Log
import com.wultra.android.powerauth.networking.error.ApiError

class Logger {

    /** Defines verbose level for this simple debugging facility. */
    enum class VerboseLevel(val level: Int) {
        /** Silences all messages. */
        OFF(0),
        /** Only errors will be printed to the debug console. */
        ERRORS(1),
        /** Errors and warnings will be printed to the debug console */
        WARNINGS(2),
        /** Info messages, errors and warnings will be printed in the debug console. */
        INFO(3),
        /** All logs (including debug information) will be printed out in the console, we recommend turning this only during the development. */
        DEBUG(4)
    }

    companion object {

        /**
         * Configuration of the verbosity level for the whole library.
         * [VerboseLevel.INFO] by default.
         */
        @Suppress("MemberVisibilityCanBePrivate")
        var verboseLevel = VerboseLevel.INFO

        private const val TAG = "DigitalOnboarding"

        internal fun print(message: String?) {
            message?.let {
                if (verboseLevel.level >= VerboseLevel.INFO.level) {
                    Log.i(TAG, message)
                }
            }
        }

        internal fun warning(message: String?) {
            message?.let {
                if (verboseLevel.level >= VerboseLevel.WARNINGS.level) {
                    Log.w(TAG, message)
                }
            }
        }

        internal fun error(message: String?) {
            message?.let {
                if (verboseLevel.level >= VerboseLevel.ERRORS.level) {
                    Log.e(TAG, message)
                }
            }
        }

        internal fun error(error: ApiError) {
            error(error.toException())
        }

        internal fun error(throwable: Throwable) {
            error(throwable.message ?: throwable.toString())
        }

        internal fun debug(message: String?) {
            message?.let {
                if (verboseLevel == VerboseLevel.DEBUG) {
                    Log.d(TAG, message)
                }
            }
        }
    }

    private constructor() {}
}

internal typealias D = Logger
