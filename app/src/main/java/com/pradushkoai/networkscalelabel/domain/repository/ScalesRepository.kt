package com.pradushkoai.networkscalelabel.domain.repository

import com.pradushkoai.networkscalelabel.domain.model.ScaleConnectionConfig
import com.pradushkoai.networkscalelabel.domain.model.ScaleReadEvent
import com.pradushkoai.networkscalelabel.domain.model.ScaleStatus
import kotlinx.coroutines.flow.Flow

/**
 * Единственный источник истины по обмену с сетевыми весами ШТРИХ-ПРИНТ.
 */
interface ScalesRepository {

    /** Опрос состояния весов (команда 0x11, пароль не требуется). */
    suspend fun requestStatus(config: ScaleConnectionConfig): ScaleStatus

    /**
     * Полное чтение базы товаров из весов.
     * Эмитит [ScaleReadEvent]; при фатальной ошибке поток бросает исключение.
     * Сборка потока может быть отменена — чтение остановится между командами.
     */
    fun readPluDatabase(config: ScaleConnectionConfig): Flow<ScaleReadEvent>
}

/** Локально сохранённые параметры подключения. */
interface ConnectionSettingsRepository {

    fun load(): ScaleConnectionConfig

    fun save(config: ScaleConnectionConfig)
}
