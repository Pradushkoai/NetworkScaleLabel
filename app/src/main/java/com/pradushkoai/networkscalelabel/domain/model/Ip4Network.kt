package com.pradushkoai.networkscalelabel.domain.model

import com.pradushkoai.networkscalelabel.util.Ip4

/**
 * IPv4-подсеть в форме CIDR, например 192.168.140.0/24.
 * Адреса — Long со старшим байтом первым (см. [Ip4]).
 */
data class Ip4Network(
    val networkAddress: Long,
    val prefixLength: Int,
) {
    init {
        require(prefixLength in 0..32) { "Некорректная длина префикса: $prefixLength" }
    }

    /** Маска подсети: 0xFFFFFF00 для /24, 0 для /0. */
    val mask: Long = ((-1L shl (32 - prefixLength)) and 0xFFFFFFFFL)

    /** Адрес широковещательной рассылки подсети. */
    val broadcastAddress: Long = (networkAddress or (mask.inv() and 0xFFFFFFFFL))

    /** Текст в форме CIDR: «192.168.140.0/24». */
    val cidr: String get() = "${Ip4.format(networkAddress)}/$prefixLength"

    /** Количество адресов хостов (без сетевого и широковещательного). */
    val hostCount: Long
        get() = if (prefixLength >= 31) 0 else (1L shl (32 - prefixLength)) - 2

    fun contains(address: Long): Boolean = (address and mask) == networkAddress

    fun contains(host: String): Boolean = Ip4.parse(host)?.let { contains(it) } ?: false

    companion object {
        /** Подсеть, в которую попадает адрес при заданной длине префикса. */
        fun of(address: Long, prefixLength: Int): Ip4Network {
            require(prefixLength in 0..32) { "Некорректная длина префикса: $prefixLength" }
            val mask = ((-1L shl (32 - prefixLength)) and 0xFFFFFFFFL)
            return Ip4Network(address and mask, prefixLength)
        }
    }
}
