package com.claudetest.hello

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
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
import java.io.IOException

class OverlayService : Service() {
  private lateinit var windowManager: WindowManager
  private var overlayView: TextView? = null
  private lateinit var usageStatsManager: UsageStatsManager
  private lateinit var prefs: SharedPreferences
  private lateinit var uploadServer: MessagesUploadServer
  private val nsdRegistrar = NsdRegistrar(this)
  private val handler = Handler(Looper.getMainLooper())

  private var isDreamActive = false
  private var isYoutubeActive = false

  // Tracks how far the event log has been read (see updateYoutubeActiveFromEvents()),
  // set once at service start-up and advanced on every poll after that.
  private var lastEventPollTime = System.currentTimeMillis()

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
          updateYoutubeActiveFromEvents()
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

    // Seed initial state from a point-in-time snapshot in case YouTube was already
    // foreground when this service started (e.g. after a reboot) -- the event log used
    // from here on only sees transitions that happen *after* lastEventPollTime, so
    // without this the overlay would stay hidden until the next actual switch.
    isYoutubeActive = snapshotYoutubeActive()

    ensureOverlayViewCreated()

    registerReceiver(
        dreamReceiver,
        IntentFilter().apply {
          addAction(ACTION_DREAMING_STARTED)
          addAction(ACTION_DREAMING_STOPPED)
        })

    handler.post(youtubePollTask)

    uploadServer = MessagesUploadServer(this)
    try {
      uploadServer.start()
      nsdRegistrar.register(MessagesUploadServer.DEFAULT_PORT)
    } catch (e: IOException) {
      // Port already taken (e.g. a previous instance still shutting down); the overlay
      // still works fine without the upload server, just not reachable from the phone.
    }
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

  /**
   * One-shot snapshot used only to seed [isYoutubeActive] at service start-up (see
   * [onCreate]). Compares YouTube's `lastTimeUsed` against the max across all packages in
   * a short recent window -- this was previously used as the *ongoing* detection
   * mechanism, but `lastTimeUsed` only updates on a foreground transition, not
   * continuously, so during a long uninterrupted YouTube session it goes stale and any
   * other package with a fresher blip flips this false even though YouTube never left.
   * [updateYoutubeActiveFromEvents] replaces it for everything after start-up, since it
   * doesn't depend on anything staying "freshest" over time.
   */
  private fun snapshotYoutubeActive(): Boolean {
    val end = System.currentTimeMillis()
    val stats =
        usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST, end - USAGE_QUERY_WINDOW_MS, end)
    if (stats.isNullOrEmpty()) return false
    val youtubeLastUsed = stats.firstOrNull { it.packageName == YOUTUBE_PACKAGE }?.lastTimeUsed ?: 0L
    val maxLastUsed = stats.maxOf { it.lastTimeUsed }
    return youtubeLastUsed > 0L && youtubeLastUsed >= maxLastUsed
  }

  /**
   * Reads usage events since the last poll and updates [isYoutubeActive] on an explicit
   * MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND for YouTube specifically -- confirmed on-device
   * (including after a real 5-minute continuous session) to fire promptly and reliably,
   * unlike `lastTimeUsed`. Any other event, or no YouTube event at all this poll, leaves
   * the current value unchanged (a latch, not a snapshot).
   */
  @Suppress("DEPRECATION") // MOVE_TO_FOREGROUND/BACKGROUND are the minSdk-26-safe names;
  // ACTIVITY_RESUMED/PAUSED (API 29+) are the same values under a newer name.
  private fun updateYoutubeActiveFromEvents() {
    val now = System.currentTimeMillis()
    val events = usageStatsManager.queryEvents(lastEventPollTime, now)
    val event = UsageEvents.Event()
    while (events.hasNextEvent()) {
      events.getNextEvent(event)
      if (event.packageName != YOUTUBE_PACKAGE) continue
      when (event.eventType) {
        UsageEvents.Event.MOVE_TO_FOREGROUND -> isYoutubeActive = true
        UsageEvents.Event.MOVE_TO_BACKGROUND -> isYoutubeActive = false
      }
    }
    lastEventPollTime = now
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
    if (::uploadServer.isInitialized) uploadServer.stop()
    nsdRegistrar.unregister()
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
