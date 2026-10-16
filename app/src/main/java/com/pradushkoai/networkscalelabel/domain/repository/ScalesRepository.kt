package com.pradushkoai.networkscalelabel.domain.repository

import com.pradushkoai.networkscalelabel.domain.model.PhoneNetwork
import com.pradushkoai.networkscalelabel.domain.model.ScaleConnectionConfig
import com.pradushkoai.networkscalelabel.domain.model.ScaleHost
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
     * Поиск весов в подсетях телефона UDP-пробами 0x11
     * (широковещательные + юникаст, пароль не нужен).
     */
    suspend fun scanForScales(networks: List<PhoneNetwork>, udpPort: Int): List<ScaleHost>

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
