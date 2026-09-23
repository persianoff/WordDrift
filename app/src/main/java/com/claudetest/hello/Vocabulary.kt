package com.claudetest.hello

import android.content.Context
import java.io.File

/**
 * Prefers the external file (updated in place via `adb push`, no rebuild needed) and falls
 * back to the copy bundled in the APK so the app has vocabulary on a fresh install too.
 */
object Vocabulary {
  private const val FILE_NAME = "messages.txt"

  fun loadLines(context: Context): List<String> {
    val external = readLinesOrNull(File(context.getExternalFilesDir(null), FILE_NAME))
    if (!external.isNullOrEmpty()) return external

    val bundled = readAssetLinesOrNull(context)
    return bundled?.takeIf { it.isNotEmpty() } ?: listOf("Hello")
  }

  private fun readLinesOrNull(file: File): List<String>? {
    return try {
      file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
    } catch (e: Exception) {
      null
    }
  }

  private fun readAssetLinesOrNull(context: Context): List<String>? {
    return try {
      context.assets.open(FILE_NAME).bufferedReader(Charsets.UTF_8).useLines { lines ->
        lines.filter { it.isNotBlank() }.toList()
      }
    } catch (e: Exception) {
      null
    }
  }
}
