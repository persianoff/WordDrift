package com.claudetest.hello

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/**
 * Advertises the upload server on the local network so the companion phone app can find this
 * TV without the user typing an IP address.
 */
class NsdRegistrar(private val context: Context) {
  private var nsdManager: NsdManager? = null
  private var registrationListener: NsdManager.RegistrationListener? = null

  fun register(port: Int) {
    val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return

    val serviceInfo =
        NsdServiceInfo().apply {
          serviceName = SERVICE_NAME
          serviceType = SERVICE_TYPE
          setPort(port)
        }

    val listener =
        object : NsdManager.RegistrationListener {
          override fun onServiceRegistered(info: NsdServiceInfo) {}

          override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}

          override fun onServiceUnregistered(info: NsdServiceInfo) {}

          override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }

    try {
      manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
      nsdManager = manager
      registrationListener = listener
    } catch (e: Exception) {
      // NSD unavailable on this firmware; the upload server is still reachable by IP.
    }
  }

  fun unregister() {
    val listener = registrationListener ?: return
    try {
      nsdManager?.unregisterService(listener)
    } catch (e: Exception) {
      // Best-effort; the service will time out on its own if this fails.
    }
    registrationListener = null
    nsdManager = null
  }

  companion object {
    const val SERVICE_TYPE = "_worddrift._tcp."
    const val SERVICE_NAME = "WordDrift"
  }
}
