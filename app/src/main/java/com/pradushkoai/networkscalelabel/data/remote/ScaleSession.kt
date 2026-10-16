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

    override fun close() {
        transport.close()
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
