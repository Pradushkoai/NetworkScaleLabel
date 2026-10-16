package com.pradushkoai.networkscalelabel.domain.model

/**
 * Ошибки обмена с весами. Иерархия намеренно плоская и небольшая —
 * по каждой легко построить пользовательское сообщение.
 */

/** Тип ошибки связи — определяет подсказку для пользователя. */
enum class ScaleErrorKind {
    /** Весы не отвечают (таймауты UDP после всех повторов). */
    TIMEOUT,

    /** Нет сетевого маршрута до весов (устройство в другой подсети). */
    NO_ROUTE,

    /** Хост отвечает, но UDP-порт закрыт (ICMP «порт недоступен»). */
    PORT_UNREACHABLE,

    /** Прочая сетевая ошибка. */
    NETWORK_ERROR,
}

/** Ошибка обмена: проблема на транспортном уровне. */
class ScaleCommunicationException(
    message: String,
    cause: Throwable? = null,
    val kind: ScaleErrorKind = ScaleErrorKind.TIMEOUT,
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
