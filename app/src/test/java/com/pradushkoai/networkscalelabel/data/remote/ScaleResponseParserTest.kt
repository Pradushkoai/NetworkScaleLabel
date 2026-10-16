package com.pradushkoai.networkscalelabel.data.remote

import com.pradushkoai.networkscalelabel.domain.model.PluRecord
import com.pradushkoai.networkscalelabel.domain.model.ScaleStatus
import com.pradushkoai.networkscalelabel.util.CP1251_HIGH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Тесты разборщиков ответов 0x11/0x51/0x58 по спецификации протокола v1.6.
 * Строки — в CP1251, числа — little-endian.
 */
class ScaleResponseParserTest {

    // ---------------------------------------------------------------- helpers

    private fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    private fun le24(v: Long) = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
    )

    private fun le32(v: Long) = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
    )

    private fun cp1251(s: String, size: Int): ByteArray {
        // Кодируем через таблицу (аналогично продакшн-коду), добиваем нулями
        val raw = s.map { ch ->
            when (ch.code) {
                in 0x00..0x7F -> ch.code.toByte()
                else -> {
                    val idx = CP1251_HIGH.indexOf(ch.code)
                    require(idx >= 0) { "Нет символа в CP1251: $ch" }
                    (idx + 0x80).toByte()
                }
            }
        }.toMutableList()
        while (raw.size < size) raw.add(0)
        return raw.take(size).toByteArray()
    }

    // ---------------------------------------------------------------- 0x11

    @Test
    fun `разбор ответа состояния 0x11`() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf('4'.code.toByte(), '5'.code.toByte()))      // версия 4.5
        out.write(le16(2))                                                 // исполнение 2 (ПРИНТ М)
        out.write(byteArrayOf(7, 11, 25))                                  // дата ПО 07.11.25
        out.write(le16(3000))                                              // таблица товаров 3000
        out.write(le16(100))                                               // таблица сообщений 100
        out.write(4)                                                       // строк в сообщении
        out.write(15)                                                      // НПВ 15 кг
        out.write(0x02)                                                    // дискретность (бит1: 2 г)
        out.write(17)                                                      // логический № весов
        // 72 байта всего: добиваем нулями до конца (остальные поля не читаем)
        val data = out.toByteArray()
        val padded = data + ByteArray(72 - data.size)
        val status: ScaleStatus = ScaleResponseParser.parseStatus(padded)

        assertEquals("4.5", status.firmwareVersion)
        assertEquals(2, status.construction)
        assertEquals("07.11.25", status.firmwareDate)
        assertEquals(3000, status.goodsTableSize)
        assertEquals(100, status.messagesTableSize)
        assertEquals(4, status.linesPerMessage)
        assertEquals(15, status.maxWeightKg)
        assertEquals(17, status.scaleNumber)
    }

    // ---------------------------------------------------------------- 0x58

    @Test
    fun `разбор ПЛУ расширенного формата 0x58`() {
        val out = ByteArrayOutputStream()
        out.write(le32(200012))                                            // код товара
        out.write(cp1251("Колбаса Докторская", 28))                        // наименование 1
        out.write(cp1251("в/у, ГОСТ", 28))                                 // наименование 2
        out.write(le24(42_990))                                            // цена 429,90
        out.write(0x25)                                                    // формат этикетки 2, структура ШК 5
        out.write(le16(30))                                                // срок годности 30 дн
        out.write(le16(0))                                                 // тара 0
        out.write(le16(21))                                                // групповой код 21
        out.write(le16(1))                                                 // № сообщения 1
        out.write(0x82)                                                    // бит7 штучный + изображение 2
        out.write(cp1251("RST12345", 4))                                   // РОСТЕСТ
        out.write(byteArrayOf(1, 2, 26))                                   // дата реализации 01.02.26
        val data = out.toByteArray()
        assertEquals(80, data.size)

        val rec: PluRecord = ScaleResponseParser.parsePluExtended(12, data)
        assertEquals(12, rec.number)
        assertEquals(200012L, rec.code)
        assertEquals("Колбаса Докторская", rec.name1)
        assertEquals("в/у, ГОСТ", rec.name2)
        assertEquals(42_990L, rec.priceMinorUnits)
        assertEquals(30, rec.shelfLifeDays)
        assertEquals(21, rec.groupCode)
        assertEquals(1, rec.messageNumber)
        assertEquals("RST1", rec.rostest)
        assertTrue(rec.isPiece == true)
        assertEquals(2, rec.labelFormat)
        assertEquals(5, rec.barcodeStructure)
        assertEquals("01.02.26", rec.realizationDate)
        assertTrue(rec.extended)
        // Групповой код 21 не похож на дату (месяц 0) -> manufactureDate null
        assertNull(rec.manufactureDate)
        assertEquals("Колбаса Докторская в/у, ГОСТ", rec.displayName)
    }

    @Test
    fun `групповой код похожий на дату дает дату изготовления`() {
        // День=7 (биты 11..15), месяц=5 (биты 7..10), год=26 (биты 0..6):
        // value = 26 | (5 shl 7) | (7 shl 11) = 26 + 640 + 14336 = 15002
        val out = ByteArrayOutputStream()
        out.write(le32(1))
        out.write(cp1251("Сыр", 28))
        out.write(cp1251("", 28))
        out.write(le24(12_345))
        out.write(0x00)
        out.write(le16(0))
        out.write(le16(0))
        out.write(le16(15_002))                                            // упакованная дата изготовления
        out.write(le16(0))
        out.write(0x00)
        out.write(cp1251("", 4))
        out.write(byteArrayOf(0, 0, 0))
        val data = out.toByteArray()
        assertEquals(80, data.size)

        val rec = ScaleResponseParser.parsePluExtended(3, data)
        assertEquals(15_002, rec.groupCode)
        assertEquals("07.05.26", rec.manufactureDate)
        assertFalse(rec.isPiece == true)
    }

    // ---------------------------------------------------------------- 0x51

    @Test
    fun `разбор ПЛУ базового формата 0x51`() {
        val out = ByteArrayOutputStream()
        out.write(le32(99))
        out.write(cp1251("Молоко", 28))
        out.write(cp1251("3,2%", 28))
        out.write(le32(8_990))
        out.write(le16(7))
        out.write(le16(0))
        out.write(le16(0))
        out.write(le16(0))
        out.write(0)                                                       // № изображения
        out.write(cp1251("", 4))
        val data = out.toByteArray()
        assertEquals(77, data.size)

        val rec = ScaleResponseParser.parsePluBase(5, data)
        assertEquals(5, rec.number)
        assertEquals(99L, rec.code)
        assertEquals("Молоко", rec.name1)
        assertEquals("3,2%", rec.name2)
        assertEquals(8_990L, rec.priceMinorUnits)
        assertEquals(7, rec.shelfLifeDays)
        assertNull(rec.isPiece)
        assertFalse(rec.extended)
        assertEquals("Молоко 3,2%", rec.displayName)
    }
}
