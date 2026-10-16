package com.pradushkoai.networkscalelabel.util

/**
 * Утилиты IPv4: адрес представлен Long, старший байт — первый
 * (192.168.140.89 == 0xC0A88C59).
 */
object Ip4 {

    /** Разбирает dotted-quad строку; некорректный формат -> null. */
    fun parse(text: String): Long? {
        val parts = text.trim().split('.')
        if (parts.size != 4) return null
        var result = 0L
        for (part in parts) {
            if (part.isEmpty() || part.length > 3) return null
            if (part.any { !it.isDigit() }) return null
            val value = part.toInt()
            if (value > 255) return null
            result = (result shl 8) or value.toLong()
        }
        return result
    }

    /** Форматирует адрес в dotted-quad. */
    fun format(address: Long): String = "%d.%d.%d.%d".format(
        (address shr 24) and 0xFF,
        (address shr 16) and 0xFF,
        (address shr 8) and 0xFF,
        address and 0xFF,
    )

    /** Адрес в 4 байтах (сетевой порядок) — для DatagramPacket. */
    fun bytes(address: Long): ByteArray = byteArrayOf(
        ((address shr 24) and 0xFF).toByte(),
        ((address shr 16) and 0xFF).toByte(),
        ((address shr 8) and 0xFF).toByte(),
        (address and 0xFF).toByte(),
    )
}
