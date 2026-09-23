package com.claudetest.hello

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Lets a phone app on the same WiFi network replace the vocabulary file with a PUT request,
 * e.g. `curl -X PUT --data-binary @messages.txt http://<tv-ip>:8765/messages.txt`.
 *
 * No authentication: reachability on the local network is the same trust boundary this app
 * already relies on for ADB access.
 */
class MessagesUploadServer(private val context: Context, port: Int = DEFAULT_PORT) :
    NanoHTTPD(port) {

  override fun serve(session: IHTTPSession): Response {
    if (session.method != Method.PUT || session.uri != UPLOAD_PATH) {
      return newFixedLengthResponse(
          Response.Status.NOT_FOUND, MIME_PLAINTEXT, "PUT a text file to $UPLOAD_PATH")
    }

    val contentLength = session.headers["content-length"]?.toIntOrNull() ?: -1
    if (contentLength <= 0 || contentLength > MAX_BODY_BYTES) {
      return newFixedLengthResponse(
          Response.Status.BAD_REQUEST,
          MIME_PLAINTEXT,
          "Content-Length must be between 1 and $MAX_BODY_BYTES bytes")
    }

    val bytes = ByteArray(contentLength)
    var totalRead = 0
    while (totalRead < contentLength) {
      val n = session.inputStream.read(bytes, totalRead, contentLength - totalRead)
      if (n < 0) break
      totalRead += n
    }
    if (totalRead != contentLength) {
      return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Incomplete upload")
    }

    val text =
        try {
          strictUtf8Decode(bytes)
        } catch (e: CharacterCodingException) {
          return newFixedLengthResponse(
              Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Body must be valid UTF-8 text")
        }

    val lineCount = text.lineSequence().count { it.isNotBlank() }
    if (lineCount == 0) {
      return newFixedLengthResponse(
          Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "File has no non-blank lines")
    }

    return try {
      Vocabulary.externalFile(context).writeText(text, Charsets.UTF_8)
      newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK: saved $lineCount lines")
    } catch (e: Exception) {
      newFixedLengthResponse(
          Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Failed to save: ${e.message}")
    }
  }

  private fun strictUtf8Decode(bytes: ByteArray): String {
    val decoder =
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
    return decoder.decode(ByteBuffer.wrap(bytes)).toString()
  }

  companion object {
    const val DEFAULT_PORT = 8765
    const val UPLOAD_PATH = "/messages.txt"
    private const val MAX_BODY_BYTES = 2 * 1024 * 1024 // 2 MB is generous for a text dictionary
  }
}
