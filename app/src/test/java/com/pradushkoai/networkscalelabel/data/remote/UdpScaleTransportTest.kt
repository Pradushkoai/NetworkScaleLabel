package com.pradushkoai.networkscalelabel.data.remote

import com.pradushkoai.networkscalelabel.domain.model.ScaleCommunicationException
import com.pradushkoai.networkscalelabel.domain.model.ScaleErrorKind
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.PortUnreachableException
import java.net.SocketException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Тесты UDP-транспорта на настоящих loopback-сокетах.
 *
 * Главный сценарий — прошивка телефона, у которой DatagramSocket.connect()
 * всегда бросает IllegalArgumentException("connect: -1") при полностью
 * рабочем обмене: транспорт обязан уйти в режим фолбэка (send/receive
 * с явным адресом + ручная проверка источника) и продолжить работу.
 */
class UdpScaleTransportTest {

    /** Мини-«весы» на loopback: на каждый пакет отвечают «ECHO:» + запрос. */
    private class EchoScale : AutoCloseable {
        private val socket = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val port: Int
            get() = socket.localPort

        init {
            Thread({
                val buf = ByteArray(64 * 1024)
                while (!socket.isClosed) {
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        socket.receive(p)
                        val reply = "ECHO:".toByteArray() + buf.copyOf(p.length)
                        socket.send(DatagramPacket(reply, reply.size, p.address, p.port))
                    } catch (_: Exception) {
                        return@Thread
                    }
                }
            }, "echo-scale").apply { isDaemon = true }.start()
        }

        override fun close() {
            socket.close()
        }
    }

    /** Транспорт с connect, который падает ровно как на прошивке телефона. */
    private fun firmwareQuirkTransport(scale: EchoScale): UdpScaleTransport =
        UdpScaleTransport(
            address = InetAddress.getByName("127.0.0.1"),
            port = scale.port,
            timeoutMs = 3000,
            retries = 1,
            maxDatagram = 64 * 1024,
            socketConnect = { throw IllegalArgumentException("connect: -1") },
        )

    @Test
    fun `connect падает с IllegalArgumentException — транспорт жив и отвечает`() {
        EchoScale().use { scale ->
            firmwareQuirkTransport(scale).use { transport ->
                val reply = transport.transact(byteArrayOf(0x02, 0x01, 0x11))
                assertArrayEquals("ECHO:".toByteArray() + byteArrayOf(0x02, 0x01, 0x11), reply)
            }
        }
    }

    @Test
    fun `фолбэк — датаграммы не от весов игнорируются`() {
        EchoScale().use { scale ->
            firmwareQuirkTransport(scale).use { transport ->
                // «Чужой» шлёт мусор на локальный порт транспорта: пакет встанет
                // в очередь приёма первым и должен быть отброшен проверкой источника.
                DatagramSocket().use { rogue ->
                    val junk = "JUNK-FROM-ROGUE-HOST".toByteArray()
                    rogue.send(
                        DatagramPacket(
                            junk,
                            junk.size,
                            InetAddress.getByName("127.0.0.1"),
                            transport.localPort,
                        ),
                    )
                }
                val reply = transport.transact(byteArrayOf(0x11))
                assertArrayEquals("ECHO:".toByteArray() + byteArrayOf(0x11), reply)
            }
        }
    }

    @Test
    fun `фолбэк — пачечный режим send + tryReceive работает`() {
        EchoScale().use { scale ->
            firmwareQuirkTransport(scale).use { transport ->
                transport.send(byteArrayOf(1))
                transport.send(byteArrayOf(2))
                assertArrayEquals("ECHO:".toByteArray() + byteArrayOf(1), transport.tryReceive())
                assertArrayEquals("ECHO:".toByteArray() + byteArrayOf(2), transport.tryReceive())
            }
        }
    }

    @Test
    fun `настоящий сбой маршрута — понятная ошибка NO_ROUTE`() {
        EchoScale().use { scale ->
            try {
                UdpScaleTransport(
                    address = InetAddress.getByName("127.0.0.1"),
                    port = scale.port,
                    timeoutMs = 3000,
                    retries = 1,
                    maxDatagram = 64 * 1024,
                    socketConnect = { throw SocketException("connect failed: EHOSTUNREACH") },
                )
                fail("Ожидалась ScaleCommunicationException")
            } catch (e: ScaleCommunicationException) {
                assertEquals(ScaleErrorKind.NO_ROUTE, e.kind)
            }
        }
    }

    @Test
    fun `порт недоступен — понятная ошибка PORT_UNREACHABLE`() {
        EchoScale().use { scale ->
            try {
                UdpScaleTransport(
                    address = InetAddress.getByName("127.0.0.1"),
                    port = scale.port,
                    timeoutMs = 3000,
                    retries = 1,
                    maxDatagram = 64 * 1024,
                    socketConnect = { throw PortUnreachableException("ICMP port unreachable") },
                )
                fail("Ожидалась ScaleCommunicationException")
            } catch (e: ScaleCommunicationException) {
                assertEquals(ScaleErrorKind.PORT_UNREACHABLE, e.kind)
            }
        }
    }
}
