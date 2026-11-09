package com.nexora.app

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Persists uncaught Java/Kotlin exceptions so the next launch can show
 * the diagnostic text in-app without requiring ADB or a file attachment.
 */
class NexoraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val trace = StringWriter()
                throwable.printStackTrace(PrintWriter(trace))
                val report = buildString {
                    appendLine("NEXORA crash report")
                    appendLine("Android SDK: ${android.os.Build.VERSION.SDK_INT}")
                    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(trace.toString())
                }
                File(filesDir, "last_crash.txt").writeText(report)
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
