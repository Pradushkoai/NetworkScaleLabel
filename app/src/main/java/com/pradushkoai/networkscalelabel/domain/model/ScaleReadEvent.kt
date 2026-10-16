package com.pradushkoai.networkscalelabel.domain.model

/**
 * События прогресса чтения базы товаров из весов.
 * Поток бросает исключение при фатальной ошибке (связь, пароль и т.п.).
 */
sealed interface ScaleReadEvent {

    /** Чтение начато; [total] — размер таблицы товаров. */
    data class Started(val total: Int) : ScaleReadEvent

    /** Прогресс: [processed] ПЛУ опрошено, [found] товаров найдено, [failed] ошибок обмена. */
    data class Progress(val processed: Int, val total: Int, val found: Int, val failed: Int) :
        ScaleReadEvent

    /**
     * Ранняя остановка: [emptyStreak] пустых ПЛУ подряд — хвост таблицы
     * признан пустым (быстрый режим). Чтение на этом завершается.
     */
    data class EarlyStopped(val processed: Int, val total: Int, val emptyStreak: Int) :
        ScaleReadEvent

    /** Чтение завершено успешно. */
    data class Completed(
        val records: List<PluRecord>,
        val failedCount: Int,
        val durationMs: Long,
    ) : ScaleReadEvent
}
