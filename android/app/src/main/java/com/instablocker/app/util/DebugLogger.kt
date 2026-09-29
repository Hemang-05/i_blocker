package com.instablocker.app.util

import com.instablocker.app.BuildConfig

/**
 * Concise, integration-oriented logger that operates exclusively in DEBUG builds.
 * Stripped out in Release builds to protect user privacy and eliminate overhead.
 */
object DebugLogger {
    const val TAG = "InstaBlocker"

    fun d(message: String) {
        if (BuildConfig.DEBUG) {
            try {
                android.util.Log.d(TAG, message)
            } catch (_: RuntimeException) {
                // In JVM unit tests where android.util.Log is not mocked
                println("[$TAG] $message")
            }
        }
    }

    fun i(message: String) {
        if (BuildConfig.DEBUG) {
            try {
                android.util.Log.i(TAG, message)
            } catch (_: RuntimeException) {
                println("[$TAG] $message")
            }
        }
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            try {
                android.util.Log.w(TAG, message, throwable)
            } catch (_: RuntimeException) {
                println("[$TAG][WARN] $message ${throwable?.message ?: ""}")
            }
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            try {
                android.util.Log.e(TAG, message, throwable)
            } catch (_: RuntimeException) {
                println("[$TAG][ERROR] $message ${throwable?.message ?: ""}")
            }
        }
    }
}
