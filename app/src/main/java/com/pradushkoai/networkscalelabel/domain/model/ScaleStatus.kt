package com.pradushkoai.networkscalelabel.domain.model

/**
 * Состояние весов по команде 0x11 «Запрос состояния весов»
 * (подмножество полей, нужное приложению).
 */
data class ScaleStatus(
    /** Версия ПО весов, например "4.5". */
    val firmwareVersion: String,
    /** Номер конструктивного исполнения (Приложение 8 протокола). */
    val construction: Int,
    /** Дата ПО весов, дд.мм.гг. */
    val firmwareDate: String,
    /** Размер таблицы товаров (= максимальный номер ПЛУ). */
    val goodsTableSize: Int,
    /** Размер таблицы сообщений. */
    val messagesTableSize: Int,
    /** Строк в сообщении. */
    val linesPerMessage: Int,
    /** Наибольший предел взвешивания, кг. */
    val maxWeightKg: Int,
    /** Логический номер весов (1..99). */
    val scaleNumber: Int,
    /** Текущая дата на весах, дд.мм.гг. */
    val currentDate: String,
    /** Текущее время на весах, чч.мм.сс. */
    val currentTime: String,
    /** Режим весов (битовая маска, см. протокол). */
    val mode: Int,
    /** Признак режима быстрой загрузки (бит 14 режима). */
    val isQuickLoadMode: Boolean,
)
