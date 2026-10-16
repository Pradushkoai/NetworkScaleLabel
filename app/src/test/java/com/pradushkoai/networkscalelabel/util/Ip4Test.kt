package com.pradushkoai.networkscalelabel.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ip4Test {

    @Test
    fun `разбор и форматирование ходят по кругу`() {
        val ip = "192.168.140.89"
        assertEquals(ip, Ip4.format(Ip4.parse(ip)!!))
    }

    @Test
    fun `разбор даёт старший байт первым`() {
        // 192.168.140.89 == 0xC0A88C59
        assertEquals(0xC0A88C59L, Ip4.parse("192.168.140.89"))
        assertEquals(0L, Ip4.parse("0.0.0.0"))
        assertEquals(0xFFFFFFFFL, Ip4.parse("255.255.255.255"))
    }

    @Test
    fun `некорректные адресы не разбираются`() {
        assertNull(Ip4.parse(""))
        assertNull(Ip4.parse("192.168.140"))
        assertNull(Ip4.parse("192.168.140.89.1"))
        assertNull(Ip4.parse("192.168.140.256"))
        assertNull(Ip4.parse("a.b.c.d"))
        assertNull(Ip4.parse("192.168.140.-1"))
    }

    @Test
    fun `байты адреса — сетевой порядок`() {
        assertArrayEquals(
            byteArrayOf(0xC0.toByte(), 0xA8.toByte(), 0x8C.toByte(), 0x59.toByte()),
            Ip4.bytes(Ip4.parse("192.168.140.89")!!),
        )
    }
}
