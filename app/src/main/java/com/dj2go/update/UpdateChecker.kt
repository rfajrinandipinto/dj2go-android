package com.dj2go.update

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-hosted OTA: fetch a small JSON manifest, download the APK it points at
 * and hand it to the system installer.
 *
 * The manifest looks like:
 * {
 *   "versionCode": 2,
 *   "versionName": "0.2",
 *   "apkUrl": "https://example.com/dj2go/dj2go-debug.apk",
 *   "notes": "what changed"
 * }
 */
object UpdateChecker {

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val notes: String
    )

    fun fetch(manifestUrl: String): UpdateInfo {
        val connection = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
        }
        try {
            val code = connection.responseCode
            require(code in 200..299) { "server returned $code" }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            return UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.optString("versionName", ""),
                apkUrl = json.getString("apkUrl"),
                notes = json.optString("notes", "")
            )
        } finally {
            connection.disconnect()
        }
    }

    fun download(apkUrl: String, destination: File): File {
        destination.parentFile?.mkdirs()
        val connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            requestMethod = "GET"
        }
        try {
            val code = connection.responseCode
            require(code in 200..299) { "download failed ($code)" }
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        return destination
    }

    /** True when the downloaded APK is signed with the same key as the installed app. */
    fun hasMatchingSignature(context: Context, apkFile: File): Boolean {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val installed = runCatching {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }.getOrNull() ?: return false
        @Suppress("DEPRECATION")
        val candidate = runCatching {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
        }.getOrNull() ?: return false
        @Suppress("DEPRECATION")
        val installedSignatures = installed.signatures ?: return false
        @Suppress("DEPRECATION")
        val candidateSignatures = candidate.signatures ?: return false
        if (installedSignatures.isEmpty() || candidateSignatures.isEmpty()) return false
        return installedSignatures[0] == candidateSignatures[0]
    }
}
