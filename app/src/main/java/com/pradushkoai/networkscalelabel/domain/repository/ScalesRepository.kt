package com.pradushkoai.networkscalelabel.domain.repository

import com.pradushkoai.networkscalelabel.domain.model.PhoneNetwork
import com.pradushkoai.networkscalelabel.domain.model.ScaleConnectionConfig
import com.pradushkoai.networkscalelabel.domain.model.ScaleHost
import com.pradushkoai.networkscalelabel.domain.model.ScaleReadEvent
import com.pradushkoai.networkscalelabel.domain.model.ScaleStatus
import kotlinx.coroutines.flow.Flow

/**
 * Параметры чтения базы товаров.
 *
 * @param batchSize               сколько ПЛУ запрашивается одним «залпом» UDP.
 * @param interBatchDelayMs       пауза между пачками, мс — чтобы весы продышались.
 * @param earlyStopEmptyStreak    ранняя остановка: сколько ПОДРЯД пустых ПЛУ
 *                                достаточно, чтобы считать хвост таблицы пустым
 *                                (по умолчанию 2 — товары выгружаются с ПЛУ 1
 *                                подряд, два пустых подряд = конец выгрузки;
 *                                0 — полный проход без ранней остановки).
 */
data class PluReadOptions(
    val batchSize: Int = 6,
    val interBatchDelayMs: Long = 5,
    val earlyStopEmptyStreak: Int = 2,
)

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
    fun readPluDatabase(
        config: ScaleConnectionConfig,
        options: PluReadOptions = PluReadOptions(),
    ): Flow<ScaleReadEvent>
}

/** Локально сохранённые параметры подключения. */
interface ConnectionSettingsRepository {

    fun load(): ScaleConnectionConfig

    fun save(config: ScaleConnectionConfig)
}
