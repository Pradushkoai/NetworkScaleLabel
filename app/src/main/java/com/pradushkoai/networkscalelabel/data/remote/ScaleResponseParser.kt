package com.pradushkoai.networkscalelabel.data.remote

import com.pradushkoai.networkscalelabel.domain.model.PluRecord
import com.pradushkoai.networkscalelabel.domain.model.ScaleStatus
import com.pradushkoai.networkscalelabel.util.decodeCp1251
import com.pradushkoai.networkscalelabel.util.sliceOf
import com.pradushkoai.networkscalelabel.util.trimFixedString
import com.pradushkoai.networkscalelabel.util.u16At
import com.pradushkoai.networkscalelabel.util.u24At
import com.pradushkoai.networkscalelabel.util.u32At
import com.pradushkoai.networkscalelabel.util.u8At

/**
 * Разбор данных ответов (после кода ошибки) в доменные модели.
 * Все смещения — по «Протоколу обмена весов ШТРИХ-ПРИНТ v1.6».
 */
object ScaleResponseParser {

    private const val STATUS_DATA_LEN = 72
    private const val PLU_BASE_DATA_LEN = 77
    private const val PLU_EXT_DATA_LEN = 80

    /** Ответ 0x11 «Запрос состояния весов». */
    fun parseStatus(data: ByteArray): ScaleStatus {
        require(data.size == STATUS_DATA_LEN) {
            "Ожидалось $STATUS_DATA_LEN байт данных состояния, пришло ${data.size}"
        }
        val version = "${data.u8At(0).toChar()}.${data.u8At(1).toChar()}"
        val construction = data.u16At(2)
        val firmwareDate = dayMonthYear(data.u8At(4), data.u8At(5), data.u8At(6))
        val goodsTableSize = data.u16At(7)
        val messagesTableSize = data.u16At(9)
        val linesPerMessage = data.u8At(11)
        val maxWeightKg = data.u8At(12)
        // Дискретность — байт 13, логический номер — байт 14
        val scaleNumber = data.u8At(14)
        val mode = data.u16At(17)
        val currentDate = dayMonthYear(data.u8At(21), data.u8At(22), data.u8At(23))
        val currentTime = "%02d.%02d.%02d".format(data.u8At(24), data.u8At(25), data.u8At(26))
        return ScaleStatus(
            firmwareVersion = version,
            construction = construction,
            firmwareDate = firmwareDate,
            goodsTableSize = goodsTableSize,
            messagesTableSize = messagesTableSize,
            linesPerMessage = linesPerMessage,
            maxWeightKg = maxWeightKg,
            scaleNumber = scaleNumber,
            currentDate = currentDate,
            currentTime = currentTime,
            mode = mode,
            isQuickLoadMode = (mode and (1 shl 14)) != 0,
        )
    }

    /** Ответ 0x51 «Получить ПЛУ» (базовый формат), 77 байт данных. */
    fun parsePluBase(number: Int, data: ByteArray): PluRecord {
        require(data.size == PLU_BASE_DATA_LEN) {
            "Ожидалось $PLU_BASE_DATA_LEN байт данных ПЛУ (0x51), пришло ${data.size}"
        }
        val code = data.u32At(0)
        val name1 = trimFixedString(decodeCp1251(data.sliceOf(4, 32)))
        val name2 = trimFixedString(decodeCp1251(data.sliceOf(32, 60)))
        val price = data.u32At(60)
        val shelf = data.u16At(64)
        val tare = data.u16At(66)
        val group = data.u16At(68)
        val message = data.u16At(70)
        val images = data.u8At(72)
        val rostest = trimFixedString(decodeCp1251(data.sliceOf(73, 77)))
        return PluRecord(
            number = number,
            code = code,
            name1 = name1,
            name2 = name2,
            priceMinorUnits = price,
            shelfLifeDays = shelf,
            tareGrams = tare,
            groupCode = group,
            messageNumber = message,
            images = images,
            rostest = rostest,
            isPiece = null,
            labelFormat = null,
            barcodeStructure = null,
            realizationDate = null,
            manufactureDate = null,
            extended = false,
        )
    }

    /** Ответ 0x58 «Получить ПЛУ расширенного формата», 80 байт данных. */
    fun parsePluExtended(number: Int, data: ByteArray): PluRecord {
        require(data.size == PLU_EXT_DATA_LEN) {
            "Ожидалось $PLU_EXT_DATA_LEN байт данных ПЛУ (0x58), пришло ${data.size}"
        }
        val code = data.u32At(0)
        val name1 = trimFixedString(decodeCp1251(data.sliceOf(4, 32)))
        val name2 = trimFixedString(decodeCp1251(data.sliceOf(32, 60)))
        val price = data.u24At(60).toLong()
        // Байт 63: старший полубайт — приоритетный формат этикетки, младший — структура ШК
        val formatByte = data.u8At(63)
        val labelFormat = (formatByte shr 4) and 0x0F
        val barcodeStructure = formatByte and 0x0F
        val shelf = data.u16At(64)
        val tare = data.u16At(66)
        val group = data.u16At(68)
        val message = data.u16At(70)
        val imagesAndType = data.u8At(72)
        val isPiece = (imagesAndType and 0x80) != 0
        val images = imagesAndType and 0x0F
        val rostest = trimFixedString(decodeCp1251(data.sliceOf(73, 77)))
        val realizationDate = dayMonthYear(data.u8At(77), data.u8At(78), data.u8At(79))
        val groupCodeOrDate = group
        return PluRecord(
            number = number,
            code = code,
            name1 = name1,
            name2 = name2,
            priceMinorUnits = price,
            shelfLifeDays = shelf,
            tareGrams = tare,
            groupCode = groupCodeOrDate,
            messageNumber = message,
            images = images,
            rostest = rostest,
            isPiece = isPiece,
            labelFormat = labelFormat,
            barcodeStructure = barcodeStructure,
            realizationDate = realizationDate,
            manufactureDate = unpackManufactureDate(groupCodeOrDate),
            extended = true,
        )
    }

    /** Дата дд.мм.гг; 00.00.00 — «не задана». */
    private fun dayMonthYear(dd: Int, mm: Int, yy: Int): String =
        "%02d.%02d.%02d".format(dd, mm, yy)

    /**
     * Групповой код в расширенном формате может быть упакованной датой изготовления:
     * биты 0..6 — год, 7..10 — месяц, 11..15 — день.
     * Если похоже на дату (месяц 1..12, день 1..31) — возвращаем её, иначе null.
     */
    private fun unpackManufactureDate(value: Int): String? {
        val year = value and 0x7F
        val month = (value shr 7) and 0x0F
        val day = (value shr 11) and 0x1F
        val looksLikeDate = year in 0..99 && month in 1..12 && day in 1..31
        return if (looksLikeDate) "%02d.%02d.%02d".format(day, month, year) else null
    }
}
