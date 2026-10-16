package com.pradushkoai.networkscalelabel.presentation.scales

import com.pradushkoai.networkscalelabel.domain.model.PluRecord
import com.pradushkoai.networkscalelabel.domain.model.ScaleStatus
import com.pradushkoai.networkscalelabel.util.CurrencyFormat

/**
 * UI-состояние экрана весов.
 */
data class ScalesUiState(
    val host: String,
    val port: String,
    val password: String,
    val connection: ConnectionState,
    val readPhase: ReadPhase,
    val readProgress: ReadProgress?,
    val records: List<PluRecord>,
    val query: String,
    val selected: PluRecord?,
) {
    companion object {
        fun initial(): ScalesUiState = ScalesUiState(
            host = "",
            port = "",
            password = "",
            connection = ConnectionState.Disconnected,
            readPhase = ReadPhase.Idle,
            readProgress = null,
            records = emptyList(),
            query = "",
            selected = null,
        )
    }
}

/** Состояние подключения к весам. */
sealed interface ConnectionState {

    data object Disconnected : ConnectionState

    data object Connecting : ConnectionState

    data class Connected(val status: ScaleStatus) : ConnectionState

    data class Failed(val message: String) : ConnectionState
}

/** Фаза чтения базы товаров. */
sealed interface ReadPhase {

    data object Idle : ReadPhase

    data object Reading : ReadPhase

    data class Done(
        val count: Int,
        val failedCount: Int,
        val durationMs: Long,
    ) : ReadPhase

    data class Cancelled(val count: Int) : ReadPhase

    data class Failed(val message: String) : ReadPhase
}

/** Прогресс чтения: сколько ПЛУ опрошено / всего / найдено / ошибок. */
data class ReadProgress(
    val processed: Int,
    val total: Int,
    val found: Int,
    val failed: Int,
) {
    val fraction: Float
        get() = if (total > 0) processed.toFloat() / total else 0f
}

/** Отфильтрованный по поисковому запросу список товаров (вычисляется в UI-слое). */
fun filterRecords(records: List<PluRecord>, query: String): List<PluRecord> {
    val q = query.trim()
    if (q.isEmpty()) return records
    return records.filter { r ->
        r.displayName.contains(q, ignoreCase = true) ||
            r.code.toString().contains(q) ||
            r.number.toString() == q
    }
}

/** Строка цены для карточки товара. */
fun PluRecord.priceText(): String = CurrencyFormat.rublesWithSymbol(priceMinorUnits)
