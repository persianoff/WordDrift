package com.claudetest.hello

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.claudetest.hello.theme.HelloOverlayTheme

class MainActivity : ComponentActivity() {
  // Set right before we send the user to a system Settings screen (e.g. to grant the
  // overlay permission) so onStop's auto-finish doesn't tear us down while they're gone;
  // cleared again once onStop has consumed it so a real backgrounding still finishes us.
  private var launchingSystemSettings = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    startForegroundService(Intent(this, OverlayService::class.java))

    enableEdgeToEdge()
    setContent {
      HelloOverlayTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(
              onLaunchSystemSettings = { intent ->
                launchingSystemSettings = true
                try {
                  startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                  launchingSystemSettings = false
                }
              })
        }
      }
    }
  }

  override fun onStop() {
    super.onStop()
    // The dictionary/settings screen is only needed while actively in use; tearing it
    // down when backgrounded keeps steady-state memory to just OverlayService instead
    // of also holding a second Compose window resident the whole time.
    if (!isChangingConfigurations && !launchingSystemSettings) {
      finish()
    }
    launchingSystemSettings = false
  }
}
