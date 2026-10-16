package com.pradushkoai.networkscalelabel.domain.usecase

import com.pradushkoai.networkscalelabel.domain.model.ScaleConnectionConfig
import com.pradushkoai.networkscalelabel.domain.model.ScaleReadEvent
import com.pradushkoai.networkscalelabel.domain.repository.PluReadOptions
import com.pradushkoai.networkscalelabel.domain.repository.ScalesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Чтение всей базы товаров (ПЛУ) из весов с прогрессом.
 *
 * @param fastRead true — ранняя остановка после серии пустых ПЛУ подряд
 * (товары выгружаются в весы с ПЛУ 1 подряд, хвост таблицы пуст);
 * false — полный проход по всей таблице.
 */
class ReadPluDatabaseUseCase @Inject constructor(
    private val repository: ScalesRepository,
) {
    operator fun invoke(
        config: ScaleConnectionConfig,
        fastRead: Boolean = true,
    ): Flow<ScaleReadEvent> = repository.readPluDatabase(
        config,
        PluReadOptions(earlyStopEmptyStreak = if (fastRead) DEFAULT_EMPTY_STREAK else 0),
    )

    private companion object {
        /** Два пустых ПЛУ подряд = конец выгрузки 1С (товары идут с ПЛУ 1 подряд). */
        const val DEFAULT_EMPTY_STREAK: Int = 2
    }
}
