package com.pradushkoai.networkscalelabel.domain.model

/**
 * Ошибки обмена с весами. Иерархия намеренно плоская и небольшая —
 * по каждой легко построить пользовательское сообщение.
 */

/** Ошибка обмена: весы не отвечают (таймауты UDP после всех повторов). */
class ScaleCommunicationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Некорректный формат пакета (NAK от весов, мусор в ответе). */
class ScaleProtocolException(
    message: String,
) : Exception(message)

/** Весы заняты другим хостом (пакет BUSY, режим синхронизации). */
class ScaleBusyException(
    val holderHost: String,
    val holderPort: Int,
) : Exception("Весы захвачены другим хостом $holderHost:$holderPort (режим синхронизации)")

/** Весы вернули код ошибки протокола. */
class ScaleErrorException(
    val code: Int,
) : Exception("Код ошибки весов: $code")
