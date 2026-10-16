package com.pradushkoai.networkscalelabel.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyFormatTest {

    @Test
    fun `42990 копеек форматируются как 429,90`() {
        assertEquals("429,90", CurrencyFormat.minorUnitsToRubles(42_990L))
    }

    @Test
    fun `нулевая цена`() {
        assertEquals("0,00", CurrencyFormat.minorUnitsToRubles(0L))
    }

    @Test
    fun `цена с символом рубля`() {
        assertEquals("1 234,56 ₽", CurrencyFormat.rublesWithSymbol(123_456L))
    }

    @Test
    fun `99 копеек`() {
        assertEquals("0,99", CurrencyFormat.minorUnitsToRubles(99L))
    }
}
