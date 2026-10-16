package com.pradushkoai.networkscalelabel.domain.model

/** Весы, найденные поиском в подсети телефона. */
data class ScaleHost(
    val host: String,
    val status: ScaleStatus,
)

/** Результат поиска весов в сети телефона. */
data class ScanResult(
    /** Снимок сетей телефона на момент поиска. */
    val phoneNetworks: PhoneNetworks,
    /** Найденные весы, отсортированы по IP. */
    val hosts: List<ScaleHost>,
    val durationMs: Long,
)
