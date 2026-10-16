package com.pradushkoai.networkscalelabel.data.remote

import com.pradushkoai.networkscalelabel.domain.model.ScaleCommunicationException
import com.pradushkoai.networkscalelabel.domain.model.ScaleConnectionConfig
import com.pradushkoai.networkscalelabel.domain.model.ScaleErrorKind
import java.io.Closeable
import java.io.IOException
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

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

    /**
     * Отправляет запрос, НЕ ожидая ответа (для пачечного режима: несколько
     * запросов «в полёте», ответы собираются [tryReceive] в порядке FIFO).
     */
    fun send(request: ByteArray)

    /**
     * Пытается принять один ответ. Возвращает null по таймауту soTimeout.
     * Прочие сетевые сбои переводятся в [ScaleCommunicationException].
     */
    fun tryReceive(): ByteArray?

    /**
     * Выбрасывает «зависшие» в буфере датаграммы (опоздавшие ответы прошлых
     * пачек), чтобы они не сбивали следующий обмен. Ждёт до [timeoutMs].
     */
    fun drain(timeoutMs: Int = DRAIN_TIMEOUT_MS)

    companion object {
        const val DRAIN_TIMEOUT_MS: Int = 150
    }
}

/** Фабрика транспортов по параметрам подключения. */
interface ScaleTransportFactory {
    fun create(config: ScaleConnectionConfig): ScaleTransport
}

/** Настройки UDP-обмена по умолчанию (могут быть переопределены фабрикой). */
object UdpTransportDefaults {
    /**
     * Таймаут ожидания ответа весов на один запрос, мс.
     * 3000 мс: UDP-датаграммы теряются, а весы отвечают не мгновенно —
     * короткий таймаут давал ложные срабатывания «не отвечают».
     */
    const val TIMEOUT_MS: Int = 3000
    const val RETRIES: Int = 3
    const val MAX_DATAGRAM: Int = 64 * 1024
}

/**
 * UDP-транспорт к весам ШТРИХ-ПРИНТ.
 * Сокет «connected» — ядро отбрасывает датаграммы не от весов.
 *
 * Сетевые сбои переводятся в [ScaleCommunicationException] с понятным
 * описанием: сырые платформенные сообщения («connect -1» и т.п.) наружу
 * не проходят.
 */
class UdpScaleTransport(
    private val address: InetAddress,
    private val port: Int,
    private val timeoutMs: Int,
    private val retries: Int,
    private val maxDatagram: Int,
) : ScaleTransport {

    private val socket: DatagramSocket = try {
        DatagramSocket().apply {
            connect(address, port)
            soTimeout = timeoutMs
        }
    } catch (e: PortUnreachableException) {
        throw ScaleCommunicationException(
            "Устройство $address доступно, но UDP-порт $port закрыт. " +
                "Проверьте номер порта в настройках весов.",
            e,
            ScaleErrorKind.PORT_UNREACHABLE,
        )
    } catch (e: IOException) {
        // UDP connect падает только когда ядро не может выбрать маршрут —
        // телефон и весы в разных подсетях (ENETUNREACH/EHOSTUNREACH).
        throw ScaleCommunicationException(
            "Нет сетевого маршрута до весов $address:$port — телефон и весы, " +
                "вероятно, находятся в разных подсетях. Подключитесь к Wi-Fi-сети, " +
                "в которой стоят весы, либо настройте маршрутизацию между сетями.",
            e,
            ScaleErrorKind.NO_ROUTE,
        )
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
                        "Весы $address:$port не отвечают (таймаут ${timeoutMs} мс, " +
                            "повторов: $retries). Проверьте, что телефон и весы в одной " +
                            "подсети, UDP-порт совпадает, а на точке доступа отключена " +
                            "изоляция клиентов (AP/Client Isolation).",
                        e,
                        ScaleErrorKind.TIMEOUT,
                    )
                }
            } catch (e: PortUnreachableException) {
                throw ScaleCommunicationException(
                    "Хост $address доступен, но UDP-порт $port закрыт " +
                        "(получен ICMP «порт недоступен»). Проверьте порт.",
                    e,
                    ScaleErrorKind.PORT_UNREACHABLE,
                )
            } catch (e: IOException) {
                throw ScaleCommunicationException(
                    "Сетевая ошибка при обмене с весами $address:$port: ${e.message ?: e.javaClass.simpleName}",
                    e,
                    ScaleErrorKind.NETWORK_ERROR,
                )
            }
        }
    }

    override fun send(request: ByteArray) {
        try {
            socket.send(DatagramPacket(request, request.size, address, port))
        } catch (e: IOException) {
            throw ScaleCommunicationException(
                "Сетевая ошибка при отправке запроса весам $address:$port: " +
                    "${e.message ?: e.javaClass.simpleName}",
                e,
                ScaleErrorKind.NETWORK_ERROR,
            )
        }
    }

    override fun tryReceive(): ByteArray? {
        val buf = ByteArray(maxDatagram)
        val incoming = DatagramPacket(buf, buf.size)
        return try {
            socket.receive(incoming)
            buf.copyOf(incoming.length)
        } catch (_: SocketTimeoutException) {
            null
        } catch (e: PortUnreachableException) {
            throw ScaleCommunicationException(
                "Хост $address доступен, но UDP-порт $port закрыт " +
                    "(получен ICMP «порт недоступен»). Проверьте порт.",
                e,
                ScaleErrorKind.PORT_UNREACHABLE,
            )
        } catch (e: IOException) {
            throw ScaleCommunicationException(
                "Сетевая ошибка при приёме ответа весов $address:$port: " +
                    "${e.message ?: e.javaClass.simpleName}",
                e,
                ScaleErrorKind.NETWORK_ERROR,
            )
        }
    }

    override fun drain(timeoutMs: Int) {
        val oldTimeout = socket.soTimeout
        try {
            socket.soTimeout = timeoutMs
            val buf = ByteArray(maxDatagram)
            while (true) {
                val incoming = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(incoming)
                } catch (_: SocketTimeoutException) {
                    return
                } catch (_: IOException) {
                    return
                }
            }
        } finally {
            try {
                socket.soTimeout = oldTimeout
            } catch (_: IOException) {
                // сокет закрыт — не важно
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
        val address = try {
            InetAddress.getByName(config.host)
        } catch (e: UnknownHostException) {
            throw ScaleCommunicationException(
                "Не удалось определить адрес «${config.host}» — проверьте IP-адрес весов.",
                e,
                ScaleErrorKind.NO_ROUTE,
            )
        }
        return UdpScaleTransport(
            address = address,
            port = config.udpPort,
            timeoutMs = timeoutMs,
            retries = retries,
            maxDatagram = UdpTransportDefaults.MAX_DATAGRAM,
        )
    }
}
