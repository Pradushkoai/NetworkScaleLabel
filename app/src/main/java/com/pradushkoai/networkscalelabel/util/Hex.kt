package com.pradushkoai.networkscalelabel.util

/** Шестнадцатеричное представление байтов — для логов и диагностики. */
fun ByteArray.toHexString(): String =
    joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

/** Безопасное чтение беззнакового байта. */
fun ByteArray.u8At(index: Int): Int = this[index].toInt() and 0xFF

/** Беззнаковое 16-битное little-endian. */
fun ByteArray.u16At(index: Int): Int =
    (this[index].toInt() and 0xFF) or ((this[index + 1].toInt() and 0xFF) shl 8)

/** Беззнаковое 24-битное little-endian. */
fun ByteArray.u24At(index: Int): Int =
    (this[index].toInt() and 0xFF) or
        ((this[index + 1].toInt() and 0xFF) shl 8) or
        ((this[index + 2].toInt() and 0xFF) shl 16)

/** Беззнаковое 32-битное little-endian (в Long, чтобы не терять старший бит). */
fun ByteArray.u32At(index: Int): Long =
    (this[index].toInt() and 0xFF).toLong() or
        ((this[index + 1].toInt() and 0xFF).toLong() shl 8) or
        ((this[index + 2].toInt() and 0xFF).toLong() shl 16) or
        ((this[index + 3].toInt() and 0xFF).toLong() shl 24)

/** Подмассив байтов [from, until). */
fun ByteArray.sliceOf(from: Int, until: Int): ByteArray =
    if (until <= from) ByteArray(0) else copyOfRange(from, until)
