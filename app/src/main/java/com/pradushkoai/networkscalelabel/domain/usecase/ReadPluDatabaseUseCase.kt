package com.pradushkoai.networkscalelabel.domain.usecase

import com.pradushkoai.networkscalelabel.domain.model.ScaleConnectionConfig
import com.pradushkoai.networkscalelabel.domain.model.ScaleReadEvent
import com.pradushkoai.networkscalelabel.domain.repository.ScalesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Чтение всей базы товаров (ПЛУ) из весов с прогрессом.
 */
class ReadPluDatabaseUseCase @Inject constructor(
    private val repository: ScalesRepository,
) {
    operator fun invoke(config: ScaleConnectionConfig): Flow<ScaleReadEvent> =
        repository.readPluDatabase(config)
}
