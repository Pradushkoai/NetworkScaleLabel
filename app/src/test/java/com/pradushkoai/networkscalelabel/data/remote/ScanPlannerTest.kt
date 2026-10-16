package com.pradushkoai.networkscalelabel.data.remote

import com.pradushkoai.networkscalelabel.domain.model.Ip4Network
import com.pradushkoai.networkscalelabel.util.Ip4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPlannerTest {

    private fun net(address: String, prefix: Int) =
        Ip4Network.of(Ip4.parse(address)!!, prefix)

    @Test
    fun `в сети на 24 бита опрашиваются все хосты кроме собственного адреса`() {
        val network = net("192.168.140.23", 24)
        val targets = ScanPlanner.unicastTargets(network, Ip4.parse("192.168.140.23")!!)

        assertEquals(253, targets.size)
        assertTrue(Ip4.parse("192.168.140.1")!! in targets)
        assertTrue(Ip4.parse("192.168.140.254")!! in targets)
        assertTrue(Ip4.parse("192.168.140.89")!! in targets) // адрес весов
        assertFalse(Ip4.parse("192.168.140.23")!! in targets) // сам телефон
        assertFalse(Ip4.parse("192.168.140.0")!! in targets) // сетевой адрес
        assertFalse(Ip4.parse("192.168.140.255")!! in targets) // broadcast
    }

    @Test
    fun `большая подсеть на 16 бит ограничивается 24 битами вокруг телефона`() {
        val network = net("10.20.30.40", 16)
        val targets = ScanPlanner.unicastTargets(network, Ip4.parse("10.20.30.40")!!)

        assertEquals(253, targets.size)
        // все адреса внутри 10.20.30.x — 24 бита вокруг телефона
        assertTrue(targets.all { ((it shr 16) and 0xFFFF) == ((Ip4.parse("10.20.30.40")!! shr 16) and 0xFFFF) })
        assertTrue(targets.all { ((it shr 8) and 0xFF) == 30L })
    }

    @Test
    fun `широковещательные адреса содержат broadcast подсети и 255 255 255 255`() {
        val targets = ScanPlanner.broadcastTargets(net("192.168.140.23", 24))
        assertEquals(2, targets.size)
        assertTrue(Ip4.parse("192.168.140.255")!! in targets)
        assertTrue(ScanPlanner.LIMITED_BROADCAST in targets)
    }
}
