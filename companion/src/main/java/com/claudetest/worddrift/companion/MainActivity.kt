package com.claudetest.worddrift.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { MaterialTheme { Surface(modifier = Modifier.fillMaxSize()) { CompanionScreen() } } }
  }
}

private sealed interface UiState {
  data object Searching : UiState

  data class Error(val message: String) : UiState

  data class Editing(
      val host: String,
      val port: Int,
      val text: TextFieldValue,
      val newWord: String = "",
      val status: String? = null,
      val saving: Boolean = false,
  ) : UiState
}

/** Finds the first line containing [query] (case-insensitive) and returns its full text range. */
private fun findLineRange(text: String, query: String): TextRange? {
  if (query.isBlank()) return null
  var lineStart = 0
  for (line in text.split("\n")) {
    val lineEnd = lineStart + line.length
    if (line.contains(query, ignoreCase = true)) {
      return TextRange(lineStart, lineEnd)
    }
    lineStart = lineEnd + 1 // account for the newline separator
  }
  return null
}

@Composable
private fun CompanionScreen() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val discovery = remember { TvDiscovery(context) }
  var uiState by remember { mutableStateOf<UiState>(UiState.Searching) }

  fun startDiscoveryAndFetch() {
    uiState = UiState.Searching
    discovery.start(
        onFound = { host, port ->
          scope.launch {
            val result = TvApiClient.fetchMessages(host, port)
            uiState =
                result.fold(
                    onSuccess = { text -> UiState.Editing(host, port, TextFieldValue(text)) },
                    onFailure = { e ->
                      UiState.Error("Found the TV but couldn't load its vocabulary: ${e.message}")
                    })
          }
        },
        onError = { message -> uiState = UiState.Error(message) })
  }

  DisposableEffect(Unit) {
    startDiscoveryAndFetch()
    onDispose { discovery.stop() }
  }

  when (val state = uiState) {
    is UiState.Searching -> SearchingView()
    is UiState.Error -> ErrorView(state.message, onRetry = ::startDiscoveryAndFetch)
    is UiState.Editing ->
        EditingView(
            state = state,
            onTextChange = { newValue -> uiState = state.copy(text = newValue) },
            onNewWordChange = { query ->
              val match = findLineRange(state.text.text, query)
              uiState =
                  state.copy(
                      newWord = query,
                      text = if (match != null) state.text.copy(selection = match) else state.text)
            },
            onAdd = {
              val word = state.newWord.trim()
              if (word.isNotEmpty()) {
                val newFullText =
                    if (state.text.text.isEmpty()) word else state.text.text + "\n" + word
                uiState =
                    state.copy(
                        text = TextFieldValue(newFullText, selection = TextRange(newFullText.length)),
                        newWord = "")
              }
            },
            onSave = {
              uiState = state.copy(saving = true, status = null)
              scope.launch {
                val result = TvApiClient.saveMessages(state.host, state.port, state.text.text)
                uiState =
                    result.fold(
                        onSuccess = { msg -> state.copy(saving = false, status = "Saved: $msg") },
                        onFailure = { e -> state.copy(saving = false, status = "Failed: ${e.message}") })
              }
            })
  }
}

@Composable
private fun SearchingView() {
  Column(
      Modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Looking for WordDrift on your WiFi network…")
      }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
  Column(
      Modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
      }
}

@Composable
private fun EditingView(
    state: UiState.Editing,
    onTextChange: (TextFieldValue) -> Unit,
    onNewWordChange: (String) -> Unit,
    onAdd: () -> Unit,
    onSave: () -> Unit,
) {
  Column(Modifier.fillMaxSize().padding(16.dp)) {
    Text(
        "Connected to WordDrift TV at ${state.host}",
        style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(8.dp))

    Row {
      OutlinedTextField(
          value = state.newWord,
          onValueChange = onNewWordChange,
          singleLine = true,
          label = { Text("New word") },
          modifier = Modifier.weight(1f))
      Spacer(Modifier.width(8.dp))
      Button(onClick = onAdd, enabled = state.newWord.isNotBlank()) { Text("Add") }
    }
    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = state.text,
        onValueChange = onTextChange,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    state.status?.let {
      Text(it)
      Spacer(Modifier.height(8.dp))
    }
    Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
      Text(if (state.saving) "Saving…" else "Save to TV")
    }
  }
}
