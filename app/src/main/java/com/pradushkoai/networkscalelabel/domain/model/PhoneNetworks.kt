package com.pradushkoai.networkscalelabel.domain.model

import com.pradushkoai.networkscalelabel.util.Ip4

/** Один IPv4-адрес телефона на конкретном интерфейсе. */
data class PhoneNetwork(
    /** Системное имя интерфейса: wlan0, eth0, rmnet_data0. */
    val interfaceName: String,
    /** Человекочитаемое имя: Wi-Fi, Ethernet, «Мобильный интернет». */
    val displayName: String,
    /** Адрес телефона в этой сети. */
    val address: Long,
    /** Подсеть, в которой находится адрес. */
    val network: Ip4Network,
) {
    val addressText: String get() = Ip4.format(address)

    /** Краткое описание: «Wi-Fi: 192.168.140.23/24». */
    val summary: String get() = "$displayName: $addressText/${network.prefixLength}"
}

/** Снимок всех IPv4-сетей телефона. */
data class PhoneNetworks(
    val networks: List<PhoneNetwork>,
) {
    val isOnline: Boolean get() = networks.isNotEmpty()

    /** Попадает ли хост в одну из подсетей телефона (прямая достижимость по L2/L3). */
    fun contains(host: String): Boolean = networks.any { it.network.contains(host) }

    /** Сводка сетей через «; » — для показа пользователю. */
    val summary: String
        get() = if (networks.isEmpty()) "нет сети" else networks.joinToString("; ") { it.summary }

    companion object {
        val EMPTY: PhoneNetworks = PhoneNetworks(emptyList())
    }
}
