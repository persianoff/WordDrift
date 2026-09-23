package com.claudetest.hello

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {
  private lateinit var windowManager: WindowManager
  private var overlayView: TextView? = null
  private lateinit var usageStatsManager: UsageStatsManager
  private lateinit var prefs: SharedPreferences
  private val handler = Handler(Looper.getMainLooper())

  private var isDreamActive = false
  private var isYoutubeActive = false

  private val rotateTask =
      object : Runnable {
        override fun run() {
          overlayView?.text = loadRandomMessage()
          handler.postDelayed(this, currentRotateIntervalMs())
        }
      }

  private val youtubePollTask =
      object : Runnable {
        override fun run() {
          isYoutubeActive = checkYoutubeActive()
          updateVisibility()
          handler.postDelayed(this, YOUTUBE_POLL_INTERVAL_MS)
        }
      }

  private val dreamReceiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          isDreamActive = intent.action == ACTION_DREAMING_STARTED
          updateVisibility()
        }
      }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()

    startForeground(NOTIFICATION_ID, buildNotification())

    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
    prefs = getSharedPreferences(AppPrefs.PREFS_NAME, MODE_PRIVATE)

    ensureOverlayViewCreated()

    registerReceiver(
        dreamReceiver,
        IntentFilter().apply {
          addAction(ACTION_DREAMING_STARTED)
          addAction(ACTION_DREAMING_STOPPED)
        })

    handler.post(youtubePollTask)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // The service may already be running from a previous launch that had no overlay
    // permission yet; each restart request (e.g. after the user grants it and returns
    // to the app) is a chance to pick the view up without needing a full service restart.
    ensureOverlayViewCreated()
    return START_STICKY
  }

  private fun ensureOverlayViewCreated() {
    // Without this permission, addView() throws BadTokenException and crashes the whole
    // process. It's normal to be missing it (fresh install, or the user hasn't granted it
    // yet from the settings screen) so the service must keep running without a view rather
    // than let the crash tear down MainActivity too.
    if (overlayView != null || !Settings.canDrawOverlays(this)) return

    val view =
        TextView(this).apply {
          text = loadRandomMessage()
          setTextColor(Color.WHITE)
          setShadowLayer(12f, 0f, 0f, Color.BLACK)
          gravity = Gravity.CENTER
          maxLines = 2
          ellipsize = TextUtils.TruncateAt.END
          setAutoSizeTextTypeUniformWithConfiguration(
              MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP, TEXT_SIZE_STEP_SP, TypedValue.COMPLEX_UNIT_SP)
          visibility = View.GONE
        }

    val overlayType =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
          @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }

    val params =
        WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT)
            .apply {
              gravity = Gravity.TOP
              y = 24
            }

    windowManager.addView(view, params)
    overlayView = view
    updateVisibility()
  }

  private fun checkYoutubeActive(): Boolean {
    val end = System.currentTimeMillis()
    val stats =
        usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST, end - USAGE_QUERY_WINDOW_MS, end)
    if (stats.isNullOrEmpty()) return false
    val youtubeLastUsed = stats.firstOrNull { it.packageName == YOUTUBE_PACKAGE }?.lastTimeUsed ?: 0L
    val maxLastUsed = stats.maxOf { it.lastTimeUsed }
    return youtubeLastUsed > 0L && youtubeLastUsed >= maxLastUsed
  }

  private fun currentRotateIntervalMs(): Long {
    return prefs
        .getInt(AppPrefs.KEY_ROTATE_INTERVAL_SEC, AppPrefs.DEFAULT_ROTATE_INTERVAL_SEC)
        .coerceIn(AppPrefs.MIN_ROTATE_INTERVAL_SEC, AppPrefs.MAX_ROTATE_INTERVAL_SEC) * 1000L
  }

  private fun updateVisibility() {
    val view = overlayView ?: return
    val isEnabled = prefs.getBoolean(AppPrefs.KEY_OVERLAY_ENABLED, true)
    val shouldShow = isEnabled && (isDreamActive || isYoutubeActive)
    val isShowing = view.visibility == View.VISIBLE
    if (shouldShow && !isShowing) {
      view.text = loadRandomMessage()
      view.visibility = View.VISIBLE
      handler.removeCallbacks(rotateTask)
      handler.postDelayed(rotateTask, currentRotateIntervalMs())
    } else if (!shouldShow && isShowing) {
      view.visibility = View.GONE
      handler.removeCallbacks(rotateTask)
    }
  }

  private fun loadRandomMessage(): String {
    return Vocabulary.loadLines(this).random()
  }

  override fun onDestroy() {
    super.onDestroy()
    handler.removeCallbacks(rotateTask)
    handler.removeCallbacks(youtubePollTask)
    unregisterReceiver(dreamReceiver)
    overlayView?.let { windowManager.removeView(it) }
  }

  private fun buildNotification(): android.app.Notification {
    val channelId = "overlay_channel"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
          NotificationChannel(channelId, "Overlay", NotificationManager.IMPORTANCE_MIN)
      (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
          channel)
    }
    return NotificationCompat.Builder(this, channelId)
        .setContentTitle("WordDrift active")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()
  }

  companion object {
    private const val NOTIFICATION_ID = 1
    private const val ACTION_DREAMING_STARTED = "android.intent.action.DREAMING_STARTED"
    private const val ACTION_DREAMING_STOPPED = "android.intent.action.DREAMING_STOPPED"
    private const val MIN_TEXT_SIZE_SP = 42
    private const val MAX_TEXT_SIZE_SP = 168
    private const val TEXT_SIZE_STEP_SP = 1
    private const val YOUTUBE_PACKAGE = "com.google.android.youtube.tv"
    private const val YOUTUBE_POLL_INTERVAL_MS = 3_000L
    private const val USAGE_QUERY_WINDOW_MS = 60_000L
  }
}
