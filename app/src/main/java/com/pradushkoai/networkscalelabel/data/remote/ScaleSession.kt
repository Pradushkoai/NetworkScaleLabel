package com.pradushkoai.networkscalelabel.data.remote

import com.pradushkoai.networkscalelabel.domain.model.PluRecord
import com.pradushkoai.networkscalelabel.domain.model.ScaleErrorException
import com.pradushkoai.networkscalelabel.domain.model.ScaleStatus
import java.io.Closeable

/**
 * Сессия обмена с весами поверх открытого транспорта.
 * Вызовы блокирующие — только из Dispatchers.IO.
 */
class ScaleSession(
    private val transport: ScaleTransport,
    private val password: ByteArray,
) : Closeable {

    /** Результат чтения одного ПЛУ. */
    sealed interface PluReadOutcome {
        data class Found(val record: PluRecord) : PluReadOutcome

        /** 140 — ПЛУ с этим номером пустое (товар не записан в весы). */
        data object EmptyPlu : PluReadOutcome

        /** 128 — номер вне таблицы товаров (дальше читать бессмысленно). */
        data object BadNumber : PluReadOutcome

        /** Прочий код ошибки; фатальные (121, 122) бросаются исключением. */
        data class OtherError(val code: Int) : PluReadOutcome
    }

    /** Результат чтения одного ПЛУ в составе пачки. */
    data class BatchPlu(val number: Int, val outcome: PluReadOutcome)

    /** 0x11 «Запрос состояния весов» — пароль не требуется. */
    fun readStatus(): ScaleStatus {
        val resp = ShtrihPrintProtocol.parseResponse(
            transport.transact(ShtrihPrintProtocol.buildRequest(ShtrihPrintProtocol.CMD_STATUS)),
        )
        checkCommand(resp, ShtrihPrintProtocol.CMD_STATUS)
        // Примечание протокола: данные возвращаются при коде 0, 165 или 168
        if (resp.errorCode !in intArrayOf(0, 165, 168)) {
            throw ScaleErrorException(resp.errorCode)
        }
        return ScaleResponseParser.parseStatus(resp.data)
    }

    /**
     * Чтение одного ПЛУ.
     * @param extended true — 0x58 (расширенный формат), false — 0x51 (базовый).
     */
    fun readPlu(number: Int, extended: Boolean): PluReadOutcome {
        val command = if (extended) ShtrihPrintProtocol.CMD_GET_PLU_EX else ShtrihPrintProtocol.CMD_GET_PLU
        val resp = ShtrihPrintProtocol.parseResponse(
            transport.transact(ShtrihPrintProtocol.buildGetPluRequest(command, password, number)),
        )
        checkCommand(resp, command)
        return outcomeOf(resp, extended, number)
    }

    /**
     * Пачечное чтение ПЛУ [firstNumber .. firstNumber + count - 1].
     *
     * Все запросы отправляются одним «залпом» (весы и локальная сеть сохраняют
     * порядок датаграмм), ответы принимаются по порядку и сопоставляются
     * номерам ПЛУ слева направо — в ответе 0x58/0x51 номер ПЛУ отсутствует.
     *
     * При потере ответа (пришло меньше, чем отправлено) диапазон целиком
     * перечитывается поштучно через [readPlu] — это гарантирует корректное
     * сопоставление ценой скорости на потерянном участке.
     *
     * @throws ScaleErrorException при фатальных кодах (121, 122) — как readPlu.
     */
    fun readPluBatch(firstNumber: Int, count: Int, extended: Boolean): List<BatchPlu> {
        require(count > 0) { "Размер пачки должен быть положительным: $count" }
        val command = if (extended) ShtrihPrintProtocol.CMD_GET_PLU_EX else ShtrihPrintProtocol.CMD_GET_PLU

        // Залп запросов
        for (n in firstNumber until firstNumber + count) {
            transport.send(ShtrihPrintProtocol.buildGetPluRequest(command, password, n))
        }

        // Сбор ответов в порядке прибытия; первый же таймаут — потеря, дальше не ждём
        val replies = ArrayList<ByteArray>(count)
        var lost = false
        for (i in 0 until count) {
            val raw = transport.tryReceive()
            if (raw == null) {
                lost = true
                break
            }
            replies += raw
        }

        if (!lost && replies.size == count) {
            return replies.mapIndexed { i, raw ->
                val resp = ShtrihPrintProtocol.parseResponse(raw)
                checkCommand(resp, command)
                BatchPlu(firstNumber + i, outcomeOf(resp, extended, firstNumber + i))
            }
        }

        // Часть ответов потерялась — перечитать диапазон поштучно.
        // Сначала выбросить опоздавшие датаграммы, чтобы не сбить порядок.
        transport.drain()
        return (firstNumber until firstNumber + count).map { n -> BatchPlu(n, readPlu(n, extended)) }
    }

    override fun close() {
        transport.close()
    }

    // ------------------------------------------------------------- приватное

    private fun outcomeOf(resp: ShtrihPrintProtocol.Response, extended: Boolean, number: Int): PluReadOutcome {
        return when (val code = resp.errorCode) {
            ShtrihPrintProtocol.ERR_OK -> PluReadOutcome.Found(
                record = if (extended) {
                    ScaleResponseParser.parsePluExtended(number, resp.data)
                } else {
                    ScaleResponseParser.parsePluBase(number, resp.data)
                },
            )
            ShtrihPrintProtocol.ERR_EMPTY_PLU -> PluReadOutcome.EmptyPlu
            ShtrihPrintProtocol.ERR_BAD_PLU_NUMBER -> PluReadOutcome.BadNumber
            ShtrihPrintProtocol.ERR_WRONG_PASSWORD,
            121,
            -> throw ScaleErrorException(code)
            else -> PluReadOutcome.OtherError(code)
        }
    }

    private fun checkCommand(resp: ShtrihPrintProtocol.Response, expected: Int) {
        if (resp.command != expected) {
            throw com.pradushkoai.networkscalelabel.domain.model.ScaleProtocolException(
                "Код команды в ответе не совпал: 0x%02X (ожидался 0x%02X)".format(
                    resp.command,
                    expected,
                ),
            )
        }
    }
}
