package com.claudetest.hello

object AppPrefs {
  const val PREFS_NAME = "hello_overlay_prefs"
  const val KEY_ROTATE_INTERVAL_SEC = "rotate_interval_sec"
  const val KEY_OVERLAY_ENABLED = "overlay_enabled"
  const val DEFAULT_ROTATE_INTERVAL_SEC = 15
  const val MIN_ROTATE_INTERVAL_SEC = 5
  const val MAX_ROTATE_INTERVAL_SEC = 60
}
