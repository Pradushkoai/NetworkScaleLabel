package com.pradushkoai.networkscalelabel.domain.usecase

import com.pradushkoai.networkscalelabel.domain.model.ScaleConnectionConfig
import com.pradushkoai.networkscalelabel.domain.model.ScaleStatus
import com.pradushkoai.networkscalelabel.domain.repository.ScalesRepository
import javax.inject.Inject

/**
 * Подключение к весам: опрос состояния (0x11) — без пароля,
 * заодно проверяет достижимость весов по сети.
 */
class ConnectScalesUseCase @Inject constructor(
    private val repository: ScalesRepository,
) {
    suspend operator fun invoke(config: ScaleConnectionConfig): ScaleStatus =
        repository.requestStatus(config)
}
