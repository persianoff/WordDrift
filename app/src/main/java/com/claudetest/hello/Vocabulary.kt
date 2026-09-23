package com.claudetest.hello

import android.content.Context
import java.io.File

/**
 * Prefers the external file (updated in place via `adb push`, no rebuild needed) and falls
 * back to the copy bundled in the APK so the app has vocabulary on a fresh install too.
 *
 * Caches the parsed result so repeated calls (e.g. every rotation) don't re-read and
 * re-parse the file each time; the external file is only re-read when its mtime changes,
 * so an `adb push` update still takes effect without restarting the app.
 */
object Vocabulary {
  private const val FILE_NAME = "messages.txt"

  private var cachedLines: List<String>? = null
  private var cachedExternalMtime: Long = -1
  private var cachedFromBundled = false

  @Synchronized
  fun loadLines(context: Context): List<String> {
    val externalFile = File(context.getExternalFilesDir(null), FILE_NAME)
    val externalMtime = if (externalFile.isFile) externalFile.lastModified() else -1L

    if (externalMtime >= 0) {
      if (!cachedFromBundled && cachedLines != null && cachedExternalMtime == externalMtime) {
        return cachedLines!!
      }
      val external = readLinesOrNull(externalFile)
      if (!external.isNullOrEmpty()) {
        cachedLines = external
        cachedExternalMtime = externalMtime
        cachedFromBundled = false
        return external
      }
    }

    if (cachedFromBundled && cachedLines != null) {
      return cachedLines!!
    }

    val bundled = readAssetLinesOrNull(context)?.takeIf { it.isNotEmpty() } ?: listOf("Hello")
    cachedLines = bundled
    cachedFromBundled = true
    return bundled
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
