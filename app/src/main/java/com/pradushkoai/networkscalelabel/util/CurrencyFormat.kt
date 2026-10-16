package com.pradushkoai.networkscalelabel.util

/**
 * Форматирование денежных величин из МДЕ (минимальной денежной единицы).
 * По протоколу ШТРИХ-ПРИНТ все цены передаются целыми в МДЕ
 * (для BYN — копейки белорусского рубля).
 *
 * Формат детерминирован и не зависит от локали JVM:
 * всегда два знака после запятой, разделитель — запятая, разряды — обычный пробел.
 */
object CurrencyFormat {

    /** Символ валюты отображения — белорусский рубль (BYN). */
    const val CURRENCY_SYMBOL: String = "Br"

    /** 12345 МДЕ -> "123,45"; 0 МДЕ -> "0,00". */
    fun minorUnitsToRubles(minorUnits: Long): String {
        val negative = minorUnits < 0
        val v = if (negative) -minorUnits else minorUnits
        val whole = v / 100
        val fraction = (v % 100).toString().padStart(2, '0')
        val sign = if (negative) "-" else ""
        return "$sign${groupThousands(whole)},$fraction"
    }

    /** 12345 МДЕ -> "123,45 Br" (белорусский рубль). */
    fun rublesWithSymbol(minorUnits: Long): String =
        "${minorUnitsToRubles(minorUnits)} $CURRENCY_SYMBOL"

    /** 1234567 -> "1 234 567". */
    private fun groupThousands(value: Long): String {
        val s = value.toString()
        if (s.length <= 3) return s
        val sb = StringBuilder()
        var pos = 0
        val first = s.length % 3
        if (first > 0) {
            sb.append(s, 0, first)
            pos = first
        }
        while (pos < s.length) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(s, pos, pos + 3)
            pos += 3
        }
        return sb.toString()
    }
}
