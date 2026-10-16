package com.pradushkoai.networkscalelabel.domain.model

/**
 * Параметры подключения к сетевым весам.
 *
 * @param host     IP-адрес весов, например 192.168.140.89
 * @param udpPort  UDP-порт весов (по умолчанию для ШТРИХ-ПРИНТ — 1111)
 * @param password пароль администратора, 4 ASCII-цифры (по умолчанию 0030)
 */
data class ScaleConnectionConfig(
    val host: String,
    val udpPort: Int,
    val password: String,
) {
    companion object {
        const val DEFAULT_HOST: String = "192.168.140.89"
        const val DEFAULT_UDP_PORT: Int = 1111
        const val DEFAULT_PASSWORD: String = "0030"
        const val PASSWORD_LENGTH: Int = 4

        val DEFAULT: ScaleConnectionConfig = ScaleConnectionConfig(
            host = DEFAULT_HOST,
            udpPort = DEFAULT_UDP_PORT,
            password = DEFAULT_PASSWORD,
        )
    }
}

/** Проверка конфигурации. Возвращает текст ошибки или null, если всё корректно. */
fun ScaleConnectionConfig.validate(): String? = when {
    host.isBlank() -> "Не указан IP-адрес весов"
    udpPort !in 1..65535 -> "UDP-порт должен быть в диапазоне 1..65535"
    password.isBlank() -> "Не указан пароль"
    password.length > ScaleConnectionConfig.PASSWORD_LENGTH ->
        "Пароль — не более ${ScaleConnectionConfig.PASSWORD_LENGTH} цифр"
    else -> null
}
