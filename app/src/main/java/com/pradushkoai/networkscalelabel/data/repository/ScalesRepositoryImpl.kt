package com.pradushkoai.networkscalelabel.data.repository

import com.pradushkoai.networkscalelabel.data.remote.ScaleSession
import com.pradushkoai.networkscalelabel.data.remote.ScaleSession.PluReadOutcome
import com.pradushkoai.networkscalelabel.data.remote.ScaleTransportFactory
import com.pradushkoai.networkscalelabel.data.remote.ShtrihPrintProtocol
import com.pradushkoai.networkscalelabel.data.remote.UdpScaleScanner
import com.pradushkoai.networkscalelabel.domain.model.PhoneNetwork
import com.pradushkoai.networkscalelabel.domain.model.ScaleCommunicationException
import com.pradushkoai.networkscalelabel.domain.model.ScaleConnectionConfig
import com.pradushkoai.networkscalelabel.domain.model.ScaleHost
import com.pradushkoai.networkscalelabel.domain.model.ScaleReadEvent
import com.pradushkoai.networkscalelabel.domain.model.ScaleStatus
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
 *  - сначала 0x58 (расширенный формат); если весы не знают команду (120) —
 *    один раз переключаемся на 0x51 (базовый формат) и дальше читаем им;
 *  - пустые ПЛУ (140) пропускаются, не считаясь ошибкой;
 *  - 128/139/156 — признак конца таблицы, чтение останавливается;
 *  - ошибки обмена, пережившие повторы транспорта, накапливаются:
 *    после 10 подряд считается, что связь потеряна (фатально);
 *  - отмена сбора потока останавливает чтение между командами.
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

    override fun readPluDatabase(config: ScaleConnectionConfig): Flow<ScaleReadEvent> = flow {
        val startedAt = System.nanoTime()
        val records = mutableListOf<com.pradushkoai.networkscalelabel.domain.model.PluRecord>()
        var failedCount = 0
        var consecutiveFailures = 0
        var extended = true

        createSession(config).use { session ->
            // Размер таблицы берём из состояния весов (0x11)
            val status = session.readStatus()
            val total = status.goodsTableSize
            emit(ScaleReadEvent.Started(total))

            if (total == 0) {
                emit(ScaleReadEvent.Completed(records.toList(), 0, elapsedMs(startedAt)))
                return@use
            }

            var processed = 0
            loop@ for (number in 1..total) {
                coroutineContext.ensureActive()

                // Чтение ПЛУ с переключением 0x58 -> 0x51 по ошибке 120
                var outcome: PluReadOutcome? = null
                try {
                    var o = session.readPlu(number, extended)
                    if (extended && o is PluReadOutcome.OtherError && o.code == 120) {
                        extended = false
                        o = session.readPlu(number, extended)
                    }
                    outcome = o
                } catch (e: ScaleCommunicationException) {
                    // Транспорт уже исчерпал повторы — считаем отказом ПЛУ
                    failedCount++
                    consecutiveFailures++
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) throw e
                }

                var stop = false
                when (val o = outcome) {
                    // null при отказе обмена — уже учтено в счётчиках выше
                    null -> Unit
                    is PluReadOutcome.Found -> {
                        records += o.record
                        consecutiveFailures = 0
                    }
                    PluReadOutcome.EmptyPlu -> consecutiveFailures = 0
                    PluReadOutcome.BadNumber -> stop = true
                    is PluReadOutcome.OtherError -> when (o.code) {
                        139, 156 -> stop = true // таблица пуста / ПЛУ не найдено
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
                emit(ScaleReadEvent.Progress(processed, total, records.size, failedCount))
                if (stop) break@loop
                delay(INTER_COMMAND_DELAY_MS)
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
        /** Пауза между командами, мс — чтобы не «заваливать» весы потоком запросов. */
        const val INTER_COMMAND_DELAY_MS: Long = 20

        /** Столько запросов подряд с ошибкой обмена считаем потерей связи. */
        const val MAX_CONSECUTIVE_FAILURES: Int = 10
    }
}
