package com.pradushkoai.networkscalelabel.data.repository

import com.pradushkoai.networkscalelabel.data.remote.ScaleTransport
import com.pradushkoai.networkscalelabel.data.remote.ScaleTransportFactory
import com.pradushkoai.networkscalelabel.domain.model.ScaleConnectionConfig
import com.pradushkoai.networkscalelabel.domain.model.ScaleErrorException
import com.pradushkoai.networkscalelabel.domain.model.ScaleReadEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Тесты цикла чтения базы товаров: фолбэк 0x58 -> 0x51,
 * пропуск пустых ПЛУ, остановка по 128, фатальность по 122.
 */
class ScalesRepositoryImplTest {

    private val config = ScaleConnectionConfig("192.168.140.89", 1111, "0030")

    /** Транспорт, отвечающий по правилу; запоминает все запросы. */
    private class FakeTransport(
        private val responder: (ByteArray) -> ByteArray,
    ) : ScaleTransport {
        val requests = mutableListOf<ByteArray>()
        override fun transact(request: ByteArray): ByteArray {
            requests += request
            return responder(request)
        }
        override fun close() {}
    }

    /** Фабрика, сохраняющая созданные транспорты для проверок. */
    private class RecordingFactory(
        private val responder: (ByteArray) -> ByteArray,
    ) : ScaleTransportFactory {
        val transports = mutableListOf<FakeTransport>()
        override fun create(config: ScaleConnectionConfig): ScaleTransport =
            FakeTransport(responder).also { transports += it }
    }

    // -------------------------------------------------------------- frames

    private fun frame(cmd: Int, err: Int, data: ByteArray = ByteArray(0)): ByteArray {
        val len = 2 + data.size
        return byteArrayOf(0x02, len.toByte(), cmd.toByte(), err.toByte()) + data
    }

    private fun statusData(tableSize: Int): ByteArray {
        val out = byteArrayOf(
            '4'.code.toByte(), '5'.code.toByte(),
        ) + le16(2) + byteArrayOf(7, 11, 25) + le16(tableSize) + le16(100) + 4 + 15 + 2 + 17
        return out + ByteArray(72 - out.size)
    }

    private fun pluDataBase(): ByteArray {
        val names = ByteArray(56) // 28 + 28 нулей
        val price = le32(42_990)
        val tail = le16(30) + le16(0) + le16(21) + le16(1) + byteArrayOf(0) + ByteArray(4)
        return le32(200_012L) + names + price + tail
    }

    private fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    private fun le32(v: Long): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
    )

    private fun cmdOf(request: ByteArray): Int = request[2].toInt() and 0xFF

    private fun pluOf(request: ByteArray): Int =
        (request[3 + 4].toInt() and 0xFF) or ((request[3 + 5].toInt() and 0xFF) shl 8)

    // -------------------------------------------------------------- tests

    @Test
    fun `фолбэк на базовый формат при ошибке 120`() = runTest {
        var sawBaseForPlu1 = false
        val responder: (ByteArray) -> ByteArray = { req ->
            when (cmdOf(req)) {
                0x11 -> frame(0x11, 0, statusData(tableSize = 2))
                0x58 -> frame(0x58, 120) // неизвестная команда
                0x51 -> when (pluOf(req)) {
                    1 -> { sawBaseForPlu1 = true; frame(0x51, 0, pluDataBase()) }
                    2 -> frame(0x51, 140) // пустое ПЛУ
                    else -> frame(0x51, 128)
                }
                else -> frame(0x00, 121)
            }
        }
        val factory = RecordingFactory(responder)
        val repo = ScalesRepositoryImpl(factory)

        val events = repo.readPluDatabase(config).toList()

        assertTrue(sawBaseForPlu1)
        val completed = events.last() as ScaleReadEvent.Completed
        assertEquals(1, completed.records.size)
        assertEquals(200_012L, completed.records.first().code)
        assertEquals(42_990L, completed.records.first().priceMinorUnits)
        assertEquals(0, completed.failedCount)
    }

    @Test
    fun `остановка по ошибке 128`() = runTest {
        val responder: (ByteArray) -> ByteArray = { req ->
            when (cmdOf(req)) {
                0x11 -> frame(0x11, 0, statusData(tableSize = 5))
                else -> frame(cmdOf(req), 128)
            }
        }
        val factory = RecordingFactory(responder)
        val repo = ScalesRepositoryImpl(factory)

        val events = repo.readPluDatabase(config).toList()

        val last = events.last()
        assertTrue(last is ScaleReadEvent.Completed)
        assertEquals(0, (last as ScaleReadEvent.Completed).records.size)
        // после первого 128 чтение останавливается: запрос ПЛУ был только один
        val transport = factory.transports.single()
        assertEquals(2, transport.requests.size) // 0x11 + один запрос ПЛУ
    }

    @Test
    fun `неверный пароль фатален`() = runTest {
        val responder: (ByteArray) -> ByteArray = { req ->
            when (cmdOf(req)) {
                0x11 -> frame(0x11, 0, statusData(tableSize = 3))
                else -> frame(cmdOf(req), 122) // неверный пароль
            }
        }
        val repo = ScalesRepositoryImpl(RecordingFactory(responder))

        try {
            repo.readPluDatabase(config).toList()
            fail("Ожидалось исключение ScaleErrorException")
        } catch (expected: ScaleErrorException) {
            assertEquals(122, expected.code)
        }
    }

    @Test
    fun `requestStatus возвращает состояние весов`() = runTest {
        val responder: (ByteArray) -> ByteArray = { req ->
            when (cmdOf(req)) {
                0x11 -> frame(0x11, 0, statusData(tableSize = 3000))
                else -> frame(0x00, 121)
            }
        }
        val repo = ScalesRepositoryImpl(RecordingFactory(responder))

        val status = repo.requestStatus(config)
        assertEquals("4.5", status.firmwareVersion)
        assertEquals(3000, status.goodsTableSize)
    }
}
