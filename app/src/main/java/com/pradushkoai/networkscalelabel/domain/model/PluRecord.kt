package com.pradushkoai.networkscalelabel.domain.model

/**
 * Запись о товаре (ПЛУ), прочитанная из весов.
 *
 * Поля заполнены в зависимости от способа чтения:
 *  - базовый формат (0x51 «Получить ПЛУ»);
 *  - расширенный формат (0x58 «Получить ПЛУ расширенного формата»)
 *    — тогда доступны [isPiece], [labelFormat], [barcodeStructure], [realizationDate].
 */
data class PluRecord(
    /** Номер ПЛУ в таблице товаров весов (1..[goodsTableSize]). */
    val number: Int,
    /** Код товара (1..999999). */
    val code: Long,
    /** Наименование, строка 1. */
    val name1: String,
    /** Наименование, строка 2. */
    val name2: String,
    /** Цена в МДЕ (копейках). */
    val priceMinorUnits: Long,
    /** Срок годности, дней (0 — не задан). */
    val shelfLifeDays: Int,
    /** Тара, г. */
    val tareGrams: Int,
    /** Групповой код (в расширенном формате может быть датой изготовления). */
    val groupCode: Int,
    /** Номер сообщения (0 — нет). */
    val messageNumber: Int,
    /** Изображения: в базовом формате — номер, в расширенном — битовая маска. */
    val images: Int,
    /** Код РОСТЕСТ (ASCII). */
    val rostest: String,
    /** true — штучный товар, false — весовой; null — неизвестно (базовый формат). */
    val isPiece: Boolean?,
    /** Приоритетный формат этикетки (0 — нет, 1..15); null — базовый формат. */
    val labelFormat: Int?,
    /** Приоритетная структура ШК (0 — нет, 1..14); null — базовый формат. */
    val barcodeStructure: Int?,
    /** Дата реализации, дд.мм.гг (00.00.00 — не задана); null — базовый формат. */
    val realizationDate: String?,
    /** Дата изготовления (упакованный формат даты); null — базовый формат. */
    val manufactureDate: String?,
    /** Прочитано расширенным форматом (0x58). */
    val extended: Boolean,
) {
    /** Полное наименование: обе строки, склеенные пробелом. */
    val displayName: String
        get() = listOf(name1, name2).filter { it.isNotBlank() }.joinToString(" ").trim()
}
