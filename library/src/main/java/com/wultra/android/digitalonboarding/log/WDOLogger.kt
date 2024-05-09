package com.wultra.android.digitalonboarding.log

import android.util.Log
import com.wultra.android.powerauth.networking.error.ApiError

/**
 * Logger provides simple logging facility.
 *
 * Logs are written with "WDO" tag to standard [android.util.Log] logger.
 * You can set [logListener] to start listening on the
 */
class WDOLogger {

    /** Level of the log which should be effectively logged. */
    enum class VerboseLevel {
        /** Silences all messages. */
        OFF,
        /** Only errors will be printed into the log. */
        ERROR,
        /** Errors and warnings will be printed into the log. */
        WARNING,
        /** Info logs, errors and warnings will be printed into the log. */
        INFO,
        /** All messages will be printed into the log. */
        DEBUG
    }

    companion object {

        /** Current verbose level. */
        @JvmStatic var verboseLevel = VerboseLevel.WARNING

        /** Listener that can tap into the log stream and process it on it's own. */
        @JvmStatic var logListener: WDOLogListener? = null

        private val tag = "WDO"

        private fun log(valueFn: () -> String, allowedLevel: VerboseLevel, logFn: (String?, String) -> Unit, listenerFn: ((String) -> Unit)?) {
            val shouldProcess = verboseLevel.ordinal >= allowedLevel.ordinal
            val log = if (shouldProcess || logListener?.followVerboseLevel == false) valueFn() else return
            if (shouldProcess) {
                logFn(tag, log)
            }
            listenerFn?.invoke(log)
        }

        internal fun d(message: String) {
            d { message }
        }

        internal fun d(fn: () -> String) {
            log(fn, VerboseLevel.DEBUG, Log::d, logListener?.let { it::debug })
        }

        internal fun w(message: String) {
            w { message }
        }

        internal fun w(fn: () -> String) {
            log(fn, VerboseLevel.WARNING, Log::w, logListener?.let { it::warning })
        }

        internal fun i(message: String) {
            i { message }
        }

        internal fun i(fn: () -> String) {
            log(fn, VerboseLevel.INFO, Log::i, logListener?.let { it::info })
        }

        internal fun e(message: String) {
            e { message }
        }

        internal fun e(apiError: ApiError) {
            e { "${apiError.error?.message}: ${apiError.e}" }
        }

        internal fun e(fn: () -> String) {
            log(fn, VerboseLevel.ERROR, Log::e, logListener?.let { it::error })
        }
    }
}
