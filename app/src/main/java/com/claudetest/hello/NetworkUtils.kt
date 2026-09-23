package com.claudetest.hello

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
  /** The TV's own local-network IPv4 address, for display so a phone knows where to upload to. */
  fun localIpAddress(): String? {
    return try {
      Collections.list(NetworkInterface.getNetworkInterfaces())
          .asSequence()
          .filter { it.isUp && !it.isLoopback }
          .flatMap { Collections.list(it.inetAddresses).asSequence() }
          .filterIsInstance<Inet4Address>()
          .firstOrNull()
          ?.hostAddress
    } catch (e: Exception) {
      null
    }
  }
}
