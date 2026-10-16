package com.pradushkoai.networkscalelabel.data.remote

import com.pradushkoai.networkscalelabel.domain.model.ScaleCommunicationException
import com.pradushkoai.networkscalelabel.domain.model.ScaleConnectionConfig
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Транспорт «запрос-ответ» поверх UDP.
 * Реализация блокирующая — вызывать только из Dispatchers.IO.
 */
interface ScaleTransport : Closeable {

    /**
     * Отправляет запрос, дожидается ответа.
     * При таймауте повторяет отправку до [retries] раз, затем бросает
     * [ScaleCommunicationException]. Служебные ответы (NAK/BUSY) возвращаются
     * как есть — их разбирает протокольный уровень.
     */
    fun transact(request: ByteArray): ByteArray
}

/** Фабрика транспортов по параметрам подключения. */
interface ScaleTransportFactory {
    fun create(config: ScaleConnectionConfig): ScaleTransport
}

/** Настройки UDP-обмена по умолчанию (могут быть переопределены фабрикой). */
object UdpTransportDefaults {
    const val TIMEOUT_MS: Int = 1000
    const val RETRIES: Int = 3
    const val MAX_DATAGRAM: Int = 64 * 1024
}

/**
 * UDP-транспорт к весам ШТРИХ-ПРИНТ.
 * Сокет «connected» — ядро отбрасывает датаграммы не от весов.
 */
class UdpScaleTransport(
    private val address: InetAddress,
    private val port: Int,
    private val timeoutMs: Int,
    private val retries: Int,
    private val maxDatagram: Int,
) : ScaleTransport {

    private val socket: DatagramSocket = DatagramSocket().apply {
        connect(address, port)
        soTimeout = timeoutMs
    }

    override fun transact(request: ByteArray): ByteArray {
        var attempt = 0
        val out = DatagramPacket(request, request.size, address, port)
        while (true) {
            try {
                socket.send(out)
                val buf = ByteArray(maxDatagram)
                val incoming = DatagramPacket(buf, buf.size)
                socket.receive(incoming)
                return buf.copyOf(incoming.length)
            } catch (e: SocketTimeoutException) {
                attempt++
                if (attempt > retries) {
                    throw ScaleCommunicationException(
                        "Весы $address не отвечают (таймаут ${timeoutMs} мс, " +
                            "повторов: $retries). Проверьте IP, UDP-порт и сеть Wi-Fi.",
                        e,
                    )
                }
            }
        }
    }

    override fun close() {
        if (!socket.isClosed) socket.close()
    }
}

/** Создаёт транспорт из конфигурации с настройками по умолчанию. */
class UdpScaleTransportFactory(
    private val timeoutMs: Int = UdpTransportDefaults.TIMEOUT_MS,
    private val retries: Int = UdpTransportDefaults.RETRIES,
) : ScaleTransportFactory {

    override fun create(config: ScaleConnectionConfig): ScaleTransport {
        val address = InetAddress.getByName(config.host)
        return UdpScaleTransport(
            address = address,
            port = config.udpPort,
            timeoutMs = timeoutMs,
            retries = retries,
            maxDatagram = UdpTransportDefaults.MAX_DATAGRAM,
        )
    }
}
