package com.pradushkoai.networkscalelabel.data.repository

import com.pradushkoai.networkscalelabel.data.remote.ScaleSession
import com.pradushkoai.networkscalelabel.data.remote.ScaleSession.PluReadOutcome
import com.pradushkoai.networkscalelabel.data.remote.ScaleTransportFactory
import com.pradushkoai.networkscalelabel.data.remote.ShtrihPrintProtocol
import com.pradushkoai.networkscalelabel.data.remote.UdpScaleScanner
import com.pradushkoai.networkscalelabel.domain.model.PhoneNetwork
import com.pradushkoai.networkscalelabel.domain.model.PluRecord
import com.pradushkoai.networkscalelabel.domain.model.ScaleCommunicationException
import com.pradushkoai.networkscalelabel.domain.model.ScaleConnectionConfig
import com.pradushkoai.networkscalelabel.domain.model.ScaleHost
import com.pradushkoai.networkscalelabel.domain.model.ScaleReadEvent
import com.pradushkoai.networkscalelabel.domain.model.ScaleStatus
import com.pradushkoai.networkscalelabel.domain.repository.PluReadOptions
import com.pradushkoai.networkscalelabel.domain.repository.ScalesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Реализация обмена с весами ШТРИХ-ПРИНТ по UDP.
 *
 * Политика чтения базы товаров:
 *  - размер таблицы берётся из состояния весов (0x11);
 *  - ПЛУ читаются ПАЧКАМИ ([PluReadOptions.batchSize] запросов одним залпом,
 *    ответы сопоставляются по порядку — протокол не возвращает номер ПЛУ);
 *    при потере ответа пачка перечитывается поштучно;
 *  - сначала 0x58 (расширенный формат); если весы не знают команду (120) —
 *    пачка перечитывается базовым 0x51, дальше читаем им;
 *  - ранняя остановка: после [PluReadOptions.earlyStopEmptyStreak] пустых ПЛУ
 *    подряд хвост таблицы считается пустым (1С выгружает товары с ПЛУ 1
 *    подряд, поэтому 13700-я таблица не обходится целиком);
 *  - пустые ПЛУ (140) пропускаются, не считаясь ошибкой;
 *  - 128/139/156 — признак конца таблицы, чтение останавливается;
 *  - ошибки обмена, пережившие повторы транспорта, накапливаются:
 *    после 10 подряд считается, что связь потеряна (фатально);
 *  - отмена сбора потока останавливает чтение между пачками.
 */
@Singleton
class ScalesRepositoryImpl @Inject constructor(
    private val transportFactory: ScaleTransportFactory,
    private val scanner: UdpScaleScanner,
) : ScalesRepository {

    override suspend fun requestStatus(config: ScaleConnectionConfig): ScaleStatus =
        withContext(Dispatchers.IO) {
            createSession(config).use { session -> session.readStatus() }
        }

    override suspend fun scanForScales(
        networks: List<PhoneNetwork>,
        udpPort: Int,
    ): List<ScaleHost> = withContext(Dispatchers.IO) { scanner.scan(networks, udpPort) }

    override fun readPluDatabase(
        config: ScaleConnectionConfig,
        options: PluReadOptions,
    ): Flow<ScaleReadEvent> = flow {
        val startedAt = System.nanoTime()
        val records = mutableListOf<PluRecord>()
        var failedCount = 0
        var consecutiveFailures = 0
        var extended = true
        var emptyStreak = 0

        createSession(config).use { session ->
            // Размер таблицы берём из состояния весов (0x11)
            val status = session.readStatus()
            val total = status.goodsTableSize
            val earlyStop = options.earlyStopEmptyStreak > 0
            emit(ScaleReadEvent.Started(total))

            if (total == 0) {
                emit(ScaleReadEvent.Completed(records.toList(), 0, elapsedMs(startedAt)))
                return@use
            }

            var processed = 0
            var number = 1
            var stopped = false
            loop@ while (number <= total && !stopped) {
                coroutineContext.ensureActive()

                val size = minOf(options.batchSize, total - number + 1)
                var items = session.readPluBatch(number, size, extended)

                // Фолбэк 0x58 -> 0x51 по ошибке 120: перечитать ту же пачку
                if (extended && items.any { it.outcome is PluReadOutcome.OtherError && it.outcome.code == 120 }) {
                    extended = false
                    items = session.readPluBatch(number, size, extended)
                }

                for (item in items) {
                    when (val o = item.outcome) {
                        is PluReadOutcome.Found -> {
                            records += o.record
                            emptyStreak = 0
                            consecutiveFailures = 0
                        }
                        PluReadOutcome.EmptyPlu -> {
                            emptyStreak++
                            consecutiveFailures = 0
                        }
                        PluReadOutcome.BadNumber -> {
                            processed++
                            stopped = true
                            break
                        }
                        is PluReadOutcome.OtherError -> when (o.code) {
                            139, 156 -> {
                                processed++
                                stopped = true
                                break
                            }
                            else -> {
                                failedCount++
                                consecutiveFailures++
                                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                                    throw ScaleCommunicationException(
                                        "Связь с весами потеряна: $consecutiveFailures " +
                                            "запросов подряд завершились ошибкой (код ${o.code})",
                                    )
                                }
                            }
                        }
                    }
                    processed++
                }

                number += size
                emit(ScaleReadEvent.Progress(processed, total, records.size, failedCount))

                if (stopped) break@loop

                // Ранняя остановка: хвост таблицы пуст (товары выгружаются с ПЛУ 1 подряд)
                if (earlyStop && emptyStreak >= options.earlyStopEmptyStreak) {
                    emit(
                        ScaleReadEvent.EarlyStopped(
                            processed = processed,
                            total = total,
                            emptyStreak = emptyStreak,
                        ),
                    )
                    stopped = true
                    break@loop
                }

                delay(options.interBatchDelayMs)
            }

            emit(
                ScaleReadEvent.Completed(
                    records = records.toList(),
                    failedCount = failedCount,
                    durationMs = elapsedMs(startedAt),
                ),
            )
        }
    }.flowOn(Dispatchers.IO)

    // ------------------------------------------------------------------ private

    private fun createSession(config: ScaleConnectionConfig): ScaleSession {
        val transport = transportFactory.create(config)
        return ScaleSession(transport, ShtrihPrintProtocol.passwordBytes(config.password))
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    private companion object {
        /** Столько запросов подряд с ошибкой обмена считаем потерей связи. */
        const val MAX_CONSECUTIVE_FAILURES: Int = 10
    }
}
