package com.claudetest.hello.ui.main

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.claudetest.hello.AppPrefs
import com.claudetest.hello.OverlayService
import com.claudetest.hello.data.DefaultDataRepository
import com.claudetest.hello.theme.HelloOverlayTheme
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  onLaunchSystemSettings: (Intent) -> Unit = {},
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = run {
    val appContext = LocalContext.current.applicationContext
    viewModel { MainScreenViewModel(DefaultDataRepository(appContext)) }
  },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  when (state) {
    MainScreenUiState.Loading -> {
      // Blank
    }
    is MainScreenUiState.Success -> {
      MainScreen(
          data = (state as MainScreenUiState.Success).data,
          onLaunchSystemSettings = onLaunchSystemSettings,
          modifier = modifier)
    }
    is MainScreenUiState.Error -> {
      Text("Error loading data: ${(state as MainScreenUiState.Error).throwable.message}")
    }
  }
}

private fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

private fun hasUsageAccessPermission(context: Context): Boolean {
  val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
  val mode =
      appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
  return mode == AppOpsManager.MODE_ALLOWED
}

@Composable
internal fun MainScreen(
    data: List<String>,
    onLaunchSystemSettings: (Intent) -> Unit = {},
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val prefs =
      remember { context.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE) }

  var overlayPermissionGranted by remember { mutableStateOf(hasOverlayPermission(context)) }
  var usageAccessGranted by remember { mutableStateOf(hasUsageAccessPermission(context)) }

  // Re-check permission state whenever we come back to the foreground, e.g. returning
  // from the system Settings screen after the user granted (or denied) a permission.
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        val overlayNowGranted = hasOverlayPermission(context)
        if (overlayNowGranted && !overlayPermissionGranted) {
          // The already-running service only builds its view once; nudge it to pick up
          // the permission it didn't have when it first started.
          context.startForegroundService(Intent(context, OverlayService::class.java))
        }
        overlayPermissionGranted = overlayNowGranted
        usageAccessGranted = hasUsageAccessPermission(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  var intervalSeconds by remember {
    mutableStateOf(
        prefs
            .getInt(AppPrefs.KEY_ROTATE_INTERVAL_SEC, AppPrefs.DEFAULT_ROTATE_INTERVAL_SEC)
            .toFloat())
  }
  var overlayEnabled by remember {
    mutableStateOf(prefs.getBoolean(AppPrefs.KEY_OVERLAY_ENABLED, true))
  }

  fun saveInterval(value: Float) {
    val clamped =
        value.coerceIn(
            AppPrefs.MIN_ROTATE_INTERVAL_SEC.toFloat(), AppPrefs.MAX_ROTATE_INTERVAL_SEC.toFloat())
    intervalSeconds = clamped
    prefs.edit().putInt(AppPrefs.KEY_ROTATE_INTERVAL_SEC, clamped.toInt()).apply()
  }

  fun saveEnabled(value: Boolean) {
    overlayEnabled = value
    prefs.edit().putBoolean(AppPrefs.KEY_OVERLAY_ENABLED, value).apply()
  }

  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val rootFocusRequester = remember { FocusRequester() }

  Column(
      modifier
          .fillMaxSize()
          .focusRequester(rootFocusRequester)
          .focusable()
          .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (event.key) {
              Key.DirectionDown -> {
                scope.launch { listState.scrollBy(SCROLL_STEP_PX) }
                true
              }
              Key.DirectionUp -> {
                scope.launch { listState.scrollBy(-SCROLL_STEP_PX) }
                true
              }
              Key.DirectionRight -> {
                saveInterval(intervalSeconds + 1f)
                true
              }
              Key.DirectionLeft -> {
                saveInterval(intervalSeconds - 1f)
                true
              }
              Key.DirectionCenter,
              Key.Enter,
              Key.NumPadEnter -> {
                // These buttons only exist to grant permissions and disappear once granted,
                // so OK targets them first; otherwise it falls back to toggling the switch.
                when {
                  !overlayPermissionGranted ->
                      onLaunchSystemSettings(
                          Intent(
                              Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                              Uri.parse("package:${context.packageName}")))
                  !usageAccessGranted ->
                      onLaunchSystemSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                  else -> saveEnabled(!overlayEnabled)
                }
                true
              }
              else -> false
            }
          }) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
      Text("Update interval: ${intervalSeconds.toInt()}s")
      Slider(
          value = intervalSeconds,
          onValueChange = { saveInterval(it) },
          valueRange =
              AppPrefs.MIN_ROTATE_INTERVAL_SEC.toFloat()..AppPrefs.MAX_ROTATE_INTERVAL_SEC
                      .toFloat(),
          steps = AppPrefs.MAX_ROTATE_INTERVAL_SEC - AppPrefs.MIN_ROTATE_INTERVAL_SEC - 1,
          modifier = Modifier.fillMaxWidth())

      Spacer(Modifier.height(16.dp))

      Row {
        Text("Show over screensaver / YouTube")
        Spacer(Modifier.width(12.dp))
        Switch(checked = overlayEnabled, onCheckedChange = { saveEnabled(it) })
      }

      if (!overlayPermissionGranted) {
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
              onLaunchSystemSettings(
                  Intent(
                      Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                      Uri.parse("package:${context.packageName}")))
            }) {
          Text("Enable \"display over other apps\"")
        }
      }

      if (!usageAccessGranted) {
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onLaunchSystemSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
          Text("Enable usage access")
        }
      }
    }

    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
      items(data) { Greeting(it) }
    }
  }

  LaunchedEffect(Unit) { rootFocusRequester.requestFocus() }
}

private const val SCROLL_STEP_PX = 300f

@Composable
fun Greeting(text: String, modifier: Modifier = Modifier) {
  Text(text = text, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
  HelloOverlayTheme { MainScreen(listOf("experience - die Erfahrung - досвід")) }
}

@Preview(showBackground = true, widthDp = 340)
@Composable
fun MainScreenPortraitPreview() {
  HelloOverlayTheme { MainScreen(listOf("experience - die Erfahrung - досвід")) }
}
