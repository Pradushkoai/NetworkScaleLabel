package com.pradushkoai.networkscalelabel.data.remote

import com.pradushkoai.networkscalelabel.domain.model.PhoneNetwork
import com.pradushkoai.networkscalelabel.domain.model.ScaleHost
import com.pradushkoai.networkscalelabel.util.Ip4
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Поиск весов ШТРИХ-ПРИНТ в подсетях телефона.
 *
 * Каждой сети отправляем запрос состояния 0x11 (пароль не нужен):
 *  - на широковещательные адреса (подсеть + 255.255.255.255);
 *  - юникастом на каждый адрес небольшого диапазона (см. [ScanPlanner]).
 * Ответы собираем одним сокетом, источники различаем по IP отправителя.
 * Приём прекращается после «периода тишины» — так весы, отвечающие
 * с задержкой, тоже попадают в результат.
 *
 * Реализация блокирующая — вызывать только из Dispatchers.IO.
 */
@Singleton
class UdpScaleScanner @Inject constructor() {

    /** Сколько мс ждать «тишины» после последнего пакета, мс. */
    private val quietPeriodMs: Long = DEFAULT_QUIET_PERIOD_MS

    /** Таймаут одного receive — задаёт темп цикла приёма, мс. */
    private val receiveTimeoutMs: Int = DEFAULT_RECEIVE_TIMEOUT_MS

    /** Жёсткий лимит времени на одну подсеть, мс. */
    private val deadlineMs: Long = DEFAULT_DEADLINE_MS

    /** Ищет весы во всех сетях; результат отсортирован по IP. */
    fun scan(networks: List<PhoneNetwork>, udpPort: Int): List<ScaleHost> {
        if (networks.isEmpty()) return emptyList()
        val probe = ShtrihPrintProtocol.buildRequest(ShtrihPrintProtocol.CMD_STATUS)
        val responses = LinkedHashMap<String, ByteArray>()
        for (phone in networks) {
            scanOneNetwork(phone, udpPort, probe, responses)
        }
        return responses.entries
            .mapNotNull { (ip, payload) -> toScaleHost(ip, payload) }
            .sortedBy { Ip4.parse(it.host) ?: 0L }
    }

    // ------------------------------------------------------------------ private

    private fun scanOneNetwork(
        phone: PhoneNetwork,
        udpPort: Int,
        probe: ByteArray,
        responses: MutableMap<String, ByteArray>,
    ) {
        val targets = ScanPlanner.broadcastTargets(phone.network) +
            ScanPlanner.unicastTargets(phone.network, phone.address)
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = receiveTimeoutMs

            // Рассылка проб: сначала broadcast, затем юникаст
            for (target in targets) {
                try {
                    val packet = DatagramPacket(
                        probe,
                        probe.size,
                        InetAddress.getByAddress(Ip4.bytes(target)),
                        udpPort,
                    )
                    socket.send(packet)
                } catch (_: IOException) {
                    // Нет маршрута до адреса (например, чужая подсеть) — тишина
                }
            }

            // Приём ответов до наступления тишины
            val startedAt = System.nanoTime()
            var lastPacketAt = startedAt
            val buf = ByteArray(MAX_RESPONSE)
            while (true) {
                val now = System.nanoTime()
                if (elapsedMs(now - startedAt) > deadlineMs) break
                if (elapsedMs(now - lastPacketAt) > quietPeriodMs) break
                val incoming = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(incoming)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: IOException) {
                    break
                }
                lastPacketAt = System.nanoTime()
                val ip = incoming.address?.hostAddress ?: continue
                if (ip !in responses) {
                    responses[ip] = buf.copyOf(incoming.length)
                }
            }
        }
    }

    /** Ответ 0x11 без ошибки -> найденные весы; прочее (мусор, NAK, BUSY) -> null. */
    private fun toScaleHost(ip: String, payload: ByteArray): ScaleHost? {
        return try {
            val resp = ShtrihPrintProtocol.parseResponse(payload)
            if (resp.command != ShtrihPrintProtocol.CMD_STATUS) return null
            if (resp.errorCode !in STATUS_OK_CODES) return null
            ScaleHost(ip, ScaleResponseParser.parseStatus(resp.data))
        } catch (_: Exception) {
            null
        }
    }

    private fun elapsedMs(nanos: Long): Long = nanos / 1_000_000

    private companion object {
        const val DEFAULT_QUIET_PERIOD_MS: Long = 1_500
        const val DEFAULT_RECEIVE_TIMEOUT_MS: Int = 300
        const val DEFAULT_DEADLINE_MS: Long = 20_000
        const val MAX_RESPONSE: Int = 2_048

        /** Коды ошибок 0x11, при которых данные состояния валидны (см. протокол). */
        val STATUS_OK_CODES: IntArray = intArrayOf(0, 165, 168)
    }
}
