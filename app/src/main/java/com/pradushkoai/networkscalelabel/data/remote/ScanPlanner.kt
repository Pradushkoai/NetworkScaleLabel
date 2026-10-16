package com.pradushkoai.networkscalelabel.data.remote

import com.pradushkoai.networkscalelabel.domain.model.Ip4Network

/**
 * Планировщик адресов для поиска весов — чистая логика без I/O.
 *
 * Стратегия: небольшую подсеть (префикс >= 24 и не более [MAX_UNICAST_TARGETS]
 * хостов) опрашиваем юникастом целиком; большую (например, /16 в офисе) —
 * только в пределах /24 вокруг адреса телефона, остальное покрывает
 * широковещательная рассылка.
 */
object ScanPlanner {

    /** Ограничение на число юникаст-проб — не заваливаем сеть потоком пакетов. */
    const val MAX_UNICAST_TARGETS: Int = 512

    /** Ограниченный широковещательный адрес 255.255.255.255. */
    const val LIMITED_BROADCAST: Long = 0xFFFFFFFFL

    /** Юникаст-адреса для проб в подсети (адрес телефона исключается). */
    fun unicastTargets(network: Ip4Network, ownAddress: Long): List<Long> {
        val targets = if (network.hostCount in 1..MAX_UNICAST_TARGETS) {
            (1..network.hostCount).map { network.networkAddress + it }
        } else {
            // Большая подсеть: обходим /24 вокруг телефона
            val local24 = Ip4Network.of(ownAddress, 24)
            (1..254L).map { local24.networkAddress + it }
        }
        return targets.filterNot { it == ownAddress }.take(MAX_UNICAST_TARGETS)
    }

    /** Широковещательные адреса для подсети + ограниченный broadcast. */
    fun broadcastTargets(network: Ip4Network): List<Long> =
        listOf(network.broadcastAddress, LIMITED_BROADCAST).distinct()
}
