package com.claudetest.worddrift.companion

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Talks to the WordDrift TV's embedded upload server: GET/PUT of the vocabulary file. */
object TvApiClient {
  suspend fun fetchMessages(host: String, port: Int): Result<String> =
      withContext(Dispatchers.IO) {
        try {
          val connection = openConnection(host, port, "GET")
          val code = connection.responseCode
          if (code == HttpURLConnection.HTTP_OK) {
            Result.success(connection.inputStream.bufferedReader(StandardCharsets.UTF_8).readText())
          } else {
            Result.failure(Exception("Server returned $code"))
          }
        } catch (e: Exception) {
          Result.failure(e)
        }
      }

  suspend fun saveMessages(host: String, port: Int, content: String): Result<String> =
      withContext(Dispatchers.IO) {
        try {
          val connection = openConnection(host, port, "PUT")
          connection.doOutput = true
          val bytes = content.toByteArray(StandardCharsets.UTF_8)
          connection.setFixedLengthStreamingMode(bytes.size)
          connection.outputStream.use { it.write(bytes) }

          val code = connection.responseCode
          val body =
              (if (code in 200..299) connection.inputStream else connection.errorStream)
                  ?.bufferedReader(StandardCharsets.UTF_8)
                  ?.readText()
                  .orEmpty()
          if (code in 200..299) {
            Result.success(body)
          } else {
            Result.failure(Exception(body.ifBlank { "Server returned $code" }))
          }
        } catch (e: Exception) {
          Result.failure(e)
        }
      }

  private fun openConnection(host: String, port: Int, method: String): HttpURLConnection {
    val connection = URL("http://$host:$port/messages.txt").openConnection() as HttpURLConnection
    connection.requestMethod = method
    connection.connectTimeout = TIMEOUT_MS
    connection.readTimeout = TIMEOUT_MS
    return connection
  }

  private const val TIMEOUT_MS = 10_000
}
