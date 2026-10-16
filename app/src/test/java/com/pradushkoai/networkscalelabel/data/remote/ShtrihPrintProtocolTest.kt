package com.pradushkoai.networkscalelabel.data.remote

import com.pradushkoai.networkscalelabel.domain.model.ScaleBusyException
import com.pradushkoai.networkscalelabel.domain.model.ScaleProtocolException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ShtrihPrintProtocolTest {

    @Test
    fun `пароль 0030 кодируется в 4 ASCII-байта`() {
        // "0030" -> 0x30 0x30 0x33 0x30
        assertArrayEquals(
            byteArrayOf(0x30, 0x30, 0x33, 0x30),
            ShtrihPrintProtocol.passwordBytes("0030"),
        )
    }

    @Test
    fun `пароль добивается нулями до 4 цифр`() {
        assertArrayEquals(
            byteArrayOf(0x31, 0x32, 0x30, 0x30),
            ShtrihPrintProtocol.passwordBytes("12"),
        )
    }

    @Test
    fun `нецифровые символы отбрасываются`() {
        assertArrayEquals(
            byteArrayOf(0x30, 0x30, 0x33, 0x30),
            ShtrihPrintProtocol.passwordBytes("0-0+3o0"),
        )
    }

    @Test
    fun `запрос 0x51 для ПЛУ 12 собирается верно`() {
        val password = ShtrihPrintProtocol.passwordBytes("0030")
        val frame = ShtrihPrintProtocol.buildGetPluRequest(ShtrihPrintProtocol.CMD_GET_PLU, password, 12)
        // STX, LEN=7, 0x51, пароль, номер ПЛУ little-endian
        assertArrayEquals(
            byteArrayOf(
                0x02, 0x07, 0x51,
                0x30, 0x30, 0x33, 0x30,
                0x0C, 0x00,
            ),
            frame,
        )
    }

    @Test
    fun `запрос состояния 0x11 не содержит параметров`() {
        val frame = ShtrihPrintProtocol.buildRequest(ShtrihPrintProtocol.CMD_STATUS)
        assertArrayEquals(byteArrayOf(0x02, 0x01, 0x11), frame)
    }

    @Test
    fun `ответ с кодом ошибки разбирается корректно`() {
        // STX, LEN=2, CMD=0x51, ERR=0x8C(140 — пустое ПЛУ)
        val raw = byteArrayOf(0x02, 0x02, 0x51, 0x8C.toByte())
        val resp = ShtrihPrintProtocol.parseResponse(raw)
        assertEquals(ShtrihPrintProtocol.CMD_GET_PLU, resp.command)
        assertEquals(140, resp.errorCode)
        assertEquals(0, resp.data.size)
    }

    @Test
    fun `NAK разбирается в исключение протокола`() {
        try {
            ShtrihPrintProtocol.parseResponse(byteArrayOf(0x15))
            fail("Ожидалось ScaleProtocolException")
        } catch (expected: ScaleProtocolException) {
            assertEquals("Весы ответили NAK: неверный формат сообщения", expected.message)
        }
    }

    @Test
    fun `BUSY разбирается в исключение с адресом захватившего хоста`() {
        // IP 192.168.140.89; по протоколу байты 1..4 = {2-й, 1-й, 4-й, 3-й} октеты,
        // порт 1111 = 0x0457 little-endian
        val busy = byteArrayOf(
            0x0B,
            0xA8.toByte(), 0xC0.toByte(), 0x59, 0x8C.toByte(),
            0x57, 0x04,
        )
        try {
            ShtrihPrintProtocol.parseResponse(busy)
            fail("Ожидалось ScaleBusyException")
        } catch (expected: ScaleBusyException) {
            assertEquals("192.168.140.89", expected.holderHost)
            assertEquals(1111, expected.holderPort)
        }
    }

    @Test
    fun `мусорный первый байт отвергается`() {
        try {
            ShtrihPrintProtocol.parseResponse(byteArrayOf(0x7F, 0x02, 0x11, 0x00))
            fail("Ожидалось ScaleProtocolException")
        } catch (expected: ScaleProtocolException) {
            // ok
        }
    }
}
