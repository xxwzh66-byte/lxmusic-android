package com.lxmusic.android

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records the last uncaught exception so the next launch can show it.
 *
 * Two copies are written: filesDir/last_crash.txt (private) and
 * getExternalFilesDir(null)/last_crash.txt (pullable with adb or a file
 * manager). Nothing here may throw - every step is wrapped in runCatching.
 */
object CrashLogger {

    private const val FILE_NAME = "last_crash.txt"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(app, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val header = "time: " + stamp + "\n" +
            "thread: " + thread.name + "\n" +
            "device: " + Build.MANUFACTURER + " " + Build.MODEL +
            " / Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n" +
            "app: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n\n"
        val body = header + trace
        runCatching { File(context.filesDir, FILE_NAME).writeText(body) }
        runCatching {
            context.getExternalFilesDir(null)?.let { File(it, FILE_NAME).writeText(body) }
        }
    }

    fun lastCrash(context: Context): String? {
        val f = File(context.filesDir, FILE_NAME)
        if (!f.exists()) return null
        return runCatching { f.readText() }.getOrNull()
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
        runCatching { context.getExternalFilesDir(null)?.let { File(it, FILE_NAME).delete() } }
    }
}
