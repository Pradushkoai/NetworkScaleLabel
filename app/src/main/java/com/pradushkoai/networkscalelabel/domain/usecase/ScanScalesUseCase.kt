package com.pradushkoai.networkscalelabel.domain.usecase

import com.pradushkoai.networkscalelabel.domain.model.ScanResult
import com.pradushkoai.networkscalelabel.domain.repository.NetworkRepository
import com.pradushkoai.networkscalelabel.domain.repository.ScalesRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Поиск весов в подсетях телефона UDP-пробами команды 0x11.
 * Заодно обновляет снимок сетей телефона — он показывается в UI.
 */
class ScanScalesUseCase @Inject constructor(
    private val repository: ScalesRepository,
    private val networkRepository: NetworkRepository,
) {
    suspend operator fun invoke(udpPort: Int): ScanResult {
        val startedAt = System.nanoTime()
        val networks = withContext(Dispatchers.IO) { networkRepository.refresh() }
        val hosts = repository.scanForScales(networks.networks, udpPort)
        return ScanResult(
            phoneNetworks = networks,
            hosts = hosts,
            durationMs = (System.nanoTime() - startedAt) / 1_000_000,
        )
    }
}
