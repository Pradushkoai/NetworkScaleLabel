package com.pradushkoai.networkscalelabel.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Cp1251Test {

    @Test
    fun `декодирует кириллицу Привет`() {
        // П=0xCF, р=0xF0, и=0xE8, в=0xE2, е=0xE5, т=0xF2
        val bytes = byteArrayOf(0xCF.toByte(), 0xF0.toByte(), 0xE8.toByte(), 0xE2.toByte(), 0xE5.toByte(), 0xF2.toByte())
        assertEquals("Привет", decodeCp1251(bytes))
    }

    @Test
    fun `неопределённый 0x98 даёт вопрос`() {
        assertEquals('?', decodeCp1251(byteArrayOf(0x98.toByte())).first())
    }

    @Test
    fun `Ё и ё на своих местах`() {
        // Ё = 0xA8, ё = 0xB8
        assertEquals("Ёё", decodeCp1251(byteArrayOf(0xA8.toByte(), 0xB8.toByte())))
    }

    @Test
    fun `trimFixedString обрезает нули и пробелы`() {
        assertEquals("Колбаса", trimFixedString("Колбаса\u0000еретика\u0000"))
        assertEquals("Сыр", trimFixedString("Сыр    "))
        assertTrue(trimFixedString("\u0000").isEmpty())
    }
}
