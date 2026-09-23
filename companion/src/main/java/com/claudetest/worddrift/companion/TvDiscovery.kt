package com.claudetest.worddrift.companion

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/** Finds the WordDrift TV on the local network via NSD, so no IP has to be typed in. */
class TvDiscovery(private val context: Context) {
  private var nsdManager: NsdManager? = null
  private var discoveryListener: NsdManager.DiscoveryListener? = null
  private var resolving = false

  fun start(onFound: (host: String, port: Int) -> Unit, onError: (String) -> Unit) {
    val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    if (manager == null) {
      onError("Network service discovery isn't available on this device")
      return
    }
    nsdManager = manager

    val listener =
        object : NsdManager.DiscoveryListener {
          override fun onDiscoveryStarted(serviceType: String) {}

          override fun onServiceFound(service: NsdServiceInfo) {
            if (resolving) return
            if (normalize(service.serviceType) != normalize(SERVICE_TYPE)) return
            resolving = true
            manager.resolveService(
                service,
                object : NsdManager.ResolveListener {
                  override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    resolving = false
                  }

                  override fun onServiceResolved(info: NsdServiceInfo) {
                    resolving = false
                    val host = info.host?.hostAddress
                    if (host != null) {
                      stop()
                      onFound(host, info.port)
                    }
                  }
                })
          }

          override fun onServiceLost(service: NsdServiceInfo) {}

          override fun onDiscoveryStopped(serviceType: String) {}

          override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            onError("Discovery failed to start (error $errorCode)")
          }

          override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
    discoveryListener = listener

    try {
      manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    } catch (e: Exception) {
      onError("Could not start discovery: ${e.message}")
    }
  }

  fun stop() {
    val listener = discoveryListener ?: return
    discoveryListener = null
    try {
      nsdManager?.stopServiceDiscovery(listener)
    } catch (e: Exception) {
      // Already stopped or never fully started; nothing more to clean up.
    }
  }

  // Android's NSD stack is inconsistent across OEMs about a trailing dot on service type.
  private fun normalize(type: String) = type.trimEnd('.')

  companion object {
    const val SERVICE_TYPE = "_worddrift._tcp."
  }
}
