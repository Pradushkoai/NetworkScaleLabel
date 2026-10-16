package com.pradushkoai.networkscalelabel.domain.model

import com.pradushkoai.networkscalelabel.util.Ip4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ip4NetworkTest {

    private val scaleNet = Ip4Network.of(Ip4.of("192.168.140.23"), 24)

    @Test
    fun `подсеть на 24 бита считается от адреса`() {
        assertEquals("192.168.140.0/24", scaleNet.cidr)
        assertEquals("192.168.140.255", Ip4.format(scaleNet.broadcastAddress))
    }

    @Test
    fun `принадлежность хостов подсети`() {
        assertTrue(scaleNet.contains(Ip4.of("192.168.140.89")))
        assertTrue(scaleNet.contains(Ip4.of("192.168.140.1")))
        assertTrue(scaleNet.contains(Ip4.of("192.168.140.254")))
        assertFalse(scaleNet.contains(Ip4.of("192.168.1.89")))
        assertFalse(scaleNet.contains(Ip4.of("10.0.0.1")))
    }

    @Test
    fun `принадлежность по текстовому адресу`() {
        assertTrue(scaleNet.contains("192.168.140.89"))
        assertFalse(scaleNet.contains("192.168.1.89"))
        assertFalse(scaleNet.contains("не адрес"))
    }

    @Test
    fun `маска и broadcast для сетей на 16 и 30 бит`() {
        val net16 = Ip4Network.of(Ip4.of("10.20.30.40"), 16)
        assertEquals("10.20.0.0/16", net16.cidr)
        assertEquals("10.20.255.255", Ip4.format(net16.broadcastAddress))
        assertTrue(net16.contains(Ip4.of("10.20.99.99")))

        val net30 = Ip4Network.of(Ip4.of("192.168.1.5"), 30)
        assertEquals("192.168.1.4/30", net30.cidr)
        assertEquals("192.168.1.7", Ip4.format(net30.broadcastAddress))
        assertEquals(2L, net30.hostCount)
    }

    @Test
    fun `количество хостов в сети на 24 бита`() {
        assertEquals(254L, scaleNet.hostCount)
    }
}

/** Вспомогательный конвертер для читаемых тестов. */
private fun Ip4.of(text: String): Long = parse(text)!!
