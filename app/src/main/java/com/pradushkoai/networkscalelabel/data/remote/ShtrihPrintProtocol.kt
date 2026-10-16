package com.pradushkoai.networkscalelabel.data.remote

import com.pradushkoai.networkscalelabel.domain.model.ScaleBusyException
import com.pradushkoai.networkscalelabel.domain.model.ScaleProtocolException
import com.pradushkoai.networkscalelabel.util.sliceOf
import com.pradushkoai.networkscalelabel.util.u8At

/**
 * Кодирование/декодирование пакетов протокола весов ШТРИХ-ПРИНТ v1.6 (Ethernet).
 *
 * Формат сообщения (интерфейс Ethernet, без LRC):
 *  - байт 0: признак начала STX (0x02) или STE (0x03, режим синхронизации);
 *  - байт 1: длина сообщения N — байт команды + параметры (без байтов 0, 1 и LRC);
 *  - байт 2: код команды/ответа;
 *  - байты 3..N+1: данные команды/ответа (первый байт ответа — код ошибки).
 *
 * Служебные однобайтовые сообщения: ENQ 0x05, ACK 0x06, NAK 0x15, BUSY 0x0B (7 байт).
 */
object ShtrihPrintProtocol {

    // Служебные символы
    const val STX: Int = 0x02
    const val STE: Int = 0x03
    const val ENQ: Int = 0x05
    const val ACK: Int = 0x06
    const val BUSY: Int = 0x0B
    const val NAK: Int = 0x15

    // Коды команд
    const val CMD_STATUS: Int = 0x11          // Запрос состояния весов (без пароля)
    const val CMD_GET_PLU: Int = 0x51         // Получить ПЛУ (базовый формат)
    const val CMD_GET_PLU_EX: Int = 0x58      // Получить ПЛУ расширенного формата
    const val CMD_MAX_PLU_COUNT: Int = 0xD0   // Запрос макс. количества ПЛУ

    // Коды ошибок, обрабатываемые особенным образом
    const val ERR_OK: Int = 0
    const val ERR_UNKNOWN_COMMAND: Int = 120
    const val ERR_WRONG_PASSWORD: Int = 122
    const val ERR_BAD_PLU_NUMBER: Int = 128
    const val ERR_EMPTY_TABLE: Int = 139
    const val ERR_EMPTY_PLU: Int = 140

    /** Максимальная длина параметров запроса при одно­байтовой длине (FFh — для блоков). */
    private const val MAX_PARAMS: Int = 254

    /** Разобранный ответ весов. */
    data class Response(
        val startByte: Int,
        val command: Int,
        val errorCode: Int,
        val data: ByteArray,
    )

    /** Пароль -> 4 байта ASCII-цифр (дополненный/обрезанный до 4). */
    fun passwordBytes(password: String): ByteArray {
        val digits = password.filter { it in '0'..'9' }.take(4).padEnd(4, '0')
        return ByteArray(4) { i -> digits[i].code.toByte() }
    }

    /** Сборка запроса: STX + LEN + CMD + параметры. */
    fun buildRequest(command: Int, params: ByteArray = ByteArray(0)): ByteArray {
        require(params.size <= MAX_PARAMS) {
            "Слишком длинные параметры запроса: ${params.size} (макс. $MAX_PARAMS)"
        }
        val len = 1 + params.size
        val out = ByteArray(3 + params.size)
        out[0] = STX.toByte()
        out[1] = len.toByte()
        out[2] = command.toByte()
        params.copyInto(out, 3)
        return out
    }

    /** Запрос «Получить ПЛУ» (0x51) / «Получить ПЛУ расширенного формата» (0x58). */
    fun buildGetPluRequest(command: Int, password: ByteArray, pluNumber: Int): ByteArray {
        require(command == CMD_GET_PLU || command == CMD_GET_PLU_EX) {
            "Не команда чтения ПЛУ: 0x%02X".format(command)
        }
        val params = ByteArray(6)
        password.copyInto(params, 0)
        params[4] = (pluNumber and 0xFF).toByte()
        params[5] = ((pluNumber shr 8) and 0xFF).toByte()
        return buildRequest(command, params)
    }

    /**
     * Разбор ответа весов. Бросает [ScaleProtocolException] для NAK/мусора
     * и [ScaleBusyException] для BUSY.
     */
    fun parseResponse(raw: ByteArray): Response {
        if (raw.isEmpty()) {
            throw ScaleProtocolException("Пустой ответ от весов")
        }
        return when (val start = raw.u8At(0)) {
            NAK -> throw ScaleProtocolException(
                "Весы ответили NAK: неверный формат сообщения",
            )
            BUSY -> {
                if (raw.size < 7) {
                    throw ScaleProtocolException("Пакет BUSY короче 7 байт")
                }
                // Байты 1..4 — IP захватившего хоста в порядке 2,1,4,3; байты 5..6 — порт
                val ip = "${raw.u8At(2)}.${raw.u8At(1)}.${raw.u8At(4)}.${raw.u8At(3)}"
                val port = raw.u8At(5) or (raw.u8At(6) shl 8)
                throw ScaleBusyException(ip, port)
            }
            STX, STE -> parseFramed(start, raw)
            else -> throw ScaleProtocolException(
                "Неожиданный первый байт ответа: 0x%02X".format(start),
            )
        }
    }

    private fun parseFramed(start: Int, raw: ByteArray): Response {
        val len = raw.u8At(1)
        if (len < 2) {
            throw ScaleProtocolException("Некорректная длина ответа: $len")
        }
        if (raw.size < 2 + len) {
            throw ScaleProtocolException(
                "Ответ короче, чем заявлено длиной: ${raw.size} < ${2 + len}",
            )
        }
        val command = raw.u8At(2)
        val errorCode = raw.u8At(3)
        val data = raw.sliceOf(4, 2 + len)
        if (data.size != len - 2) {
            throw ScaleProtocolException("Длина данных не сходится: ${data.size} != ${len - 2}")
        }
        return Response(start, command, errorCode, data)
    }
}
