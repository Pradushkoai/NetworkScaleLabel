package com.pradushkoai.networkscalelabel.data.local

import com.pradushkoai.networkscalelabel.domain.model.Ip4Network
import com.pradushkoai.networkscalelabel.domain.model.PhoneNetwork
import com.pradushkoai.networkscalelabel.domain.model.PhoneNetworks
import com.pradushkoai.networkscalelabel.domain.repository.NetworkRepository
import com.pradushkoai.networkscalelabel.util.Ip4
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Определение IPv4-сетей телефона через NetworkInterface.
 * Перечисление интерфейсов — системные вызовы, вызывать из Dispatchers.IO.
 */
@Singleton
class AndroidNetworkInfoProvider @Inject constructor() : NetworkRepository {

    @Volatile
    private var cached: PhoneNetworks = PhoneNetworks.EMPTY

    override fun refresh(): PhoneNetworks {
        val snapshot = collect()
        cached = snapshot
        return snapshot
    }

    override fun current(): PhoneNetworks = cached

    // ------------------------------------------------------------------ private

    private fun collect(): PhoneNetworks {
        val networks = mutableListOf<PhoneNetwork>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return PhoneNetworks.EMPTY
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback || iface.isPointToPoint) continue
                for (address in iface.interfaceAddresses) {
                    val inet = address.address as? Inet4Address ?: continue
                    val prefix = address.networkPrefixLength.toInt()
                    if (prefix !in 0..32) continue
                    val ip = Ip4.parse(inet.hostAddress ?: continue) ?: continue
                    networks += PhoneNetwork(
                        interfaceName = iface.name ?: "?",
                        displayName = displayName(iface),
                        address = ip,
                        network = Ip4Network.of(ip, prefix),
                    )
                }
            }
        } catch (_: SocketException) {
            // Сеть недоступна — вернём то, что успели собрать
        }
        networks.sortWith(
            compareByDescending<PhoneNetwork> { it.interfaceName.startsWith("wlan") }
                .thenBy { it.address },
        )
        return PhoneNetworks(networks)
    }

    private fun displayName(iface: NetworkInterface): String {
        val name = iface.name.orEmpty()
        return when {
            name.startsWith("wlan") || name.contains("wifi", ignoreCase = true) -> "Wi-Fi"
            name.startsWith("eth") -> "Ethernet"
            name.startsWith("rmnet") || name.startsWith("ccmni") -> "Мобильный интернет"
            name.startsWith("usb") -> "USB"
            else -> iface.displayName?.takeIf { it.isNotBlank() } ?: name.ifBlank { "интерфейс" }
        }
    }
}
