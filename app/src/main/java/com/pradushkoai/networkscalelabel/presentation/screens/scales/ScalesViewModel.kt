package com.pradushkoai.networkscalelabel.presentation.scales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pradushkoai.networkscalelabel.domain.model.PluRecord
import com.pradushkoai.networkscalelabel.domain.model.ScaleBusyException
import com.pradushkoai.networkscalelabel.domain.model.ScaleCommunicationException
import com.pradushkoai.networkscalelabel.domain.model.ScaleConnectionConfig
import com.pradushkoai.networkscalelabel.domain.model.ScaleErrorKind
import com.pradushkoai.networkscalelabel.domain.model.ScaleErrorException
import com.pradushkoai.networkscalelabel.domain.model.ScaleReadEvent
import com.pradushkoai.networkscalelabel.domain.model.validate
import com.pradushkoai.networkscalelabel.domain.repository.ConnectionSettingsRepository
import com.pradushkoai.networkscalelabel.domain.repository.NetworkRepository
import com.pradushkoai.networkscalelabel.domain.usecase.ConnectScalesUseCase
import com.pradushkoai.networkscalelabel.domain.usecase.ReadPluDatabaseUseCase
import com.pradushkoai.networkscalelabel.domain.usecase.ScanScalesUseCase
import com.pradushkoai.networkscalelabel.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel экрана весов: подключение, поиск весов в сети,
 * чтение базы товаров, поиск по списку.
 */
@HiltViewModel
class ScalesViewModel @Inject constructor(
    private val connectScales: ConnectScalesUseCase,
    private val readPluDatabase: ReadPluDatabaseUseCase,
    private val scanScales: ScanScalesUseCase,
    private val settings: ConnectionSettingsRepository,
    private val networkRepository: NetworkRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScalesUiState.initial())
    val uiState: StateFlow<ScalesUiState> = _uiState.asStateFlow()

    /** Одноразовые события (закуски/snackbar). */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var readJob: Job? = null
    private var scanJob: Job? = null

    init {
        val saved = settings.load()
        _uiState.value = ScalesUiState.initial().copy(
            host = saved.host,
            port = saved.udpPort.toString(),
            password = saved.password,
        )
        viewModelScope.launch { refreshNetworks() }
    }

    // ------------------------------------------------------------- ввод

    fun onHostChange(value: String) = update { state ->
        val next = state.copy(host = value.trim(), connection = ConnectionState.Disconnected)
        next.copy(hostOutsideNetworks = hostOutside(next))
    }

    fun onPortChange(value: String) = update { state ->
        state.copy(port = value.filter(Char::isDigit).take(5), connection = ConnectionState.Disconnected)
    }

    fun onPasswordChange(value: String) = update { state ->
        state.copy(password = value.filter(Char::isDigit).take(4), connection = ConnectionState.Disconnected)
    }

    fun onQueryChange(value: String) = update { it.copy(query = value) }

    fun onSelectRecord(record: PluRecord?) = update { it.copy(selected = record) }

    /** Тумблер «быстрое чтение» (ранняя остановка по серии пустых ПЛУ). */
    fun onFastReadChange(enabled: Boolean) = update { it.copy(fastRead = enabled) }

    // ------------------------------------------------------------- действия

    fun connect() {
        val config = buildConfig() ?: return
        viewModelScope.launch {
            update { it.copy(connection = ConnectionState.Connecting) }
            try {
                refreshNetworks()
                val status = connectScales(config)
                settings.save(config)
                update { it.copy(connection = ConnectionState.Connected(status)) }
            } catch (e: Exception) {
                update { it.copy(connection = ConnectionState.Failed(describeConnectError(e, config))) }
            }
        }
    }

    /** Поиск весов UDP-пробами в подсетях телефона. */
    fun scan() {
        if (scanJob?.isActive == true) return
        val port = _uiState.value.port.toIntOrNull() ?: ScaleConnectionConfig.DEFAULT_UDP_PORT
        scanJob = viewModelScope.launch {
            update { it.copy(scanPhase = ScanPhase.Scanning) }
            try {
                val result = scanScales(port)
                update { state ->
                    val next = state.copy(
                        phoneNetworks = result.phoneNetworks,
                        scanPhase = ScanPhase.Done(
                            hosts = result.hosts,
                            durationMs = result.durationMs,
                            wasOnline = result.phoneNetworks.isOnline,
                        ),
                    )
                    next.copy(hostOutsideNetworks = hostOutside(next))
                }
                val found = result.hosts
                when {
                    found.isEmpty() -> _events.tryEmit("Весы в подсети телефона не найдены")
                    found.size == 1 && _uiState.value.hostOutsideNetworks -> {
                        val ip = found.first().host
                        onHostChange(ip)
                        _events.tryEmit("Найдены весы $ip — IP подставлен, нажмите «Подключить»")
                    }
                    else -> _events.tryEmit("Найдено весов: ${found.size}")
                }
            } catch (e: Exception) {
                // Отмена не является ошибкой
                if (e !is kotlinx.coroutines.CancellationException) {
                    update { it.copy(scanPhase = ScanPhase.Idle) }
                    _events.tryEmit("Поиск не удался: ${describeError(e)}")
                }
            }
        }
    }

    /** Подставляет IP найденных весов в поле адреса. */
    fun onPickScannedHost(host: String) = onHostChange(host)

    fun startRead() {
        val config = buildConfig() ?: return
        if (readJob?.isActive == true) return
        val fastRead = _uiState.value.fastRead
        readJob = viewModelScope.launch {
            update {
                it.copy(
                    readPhase = ReadPhase.Reading,
                    readProgress = null,
                    records = emptyList(),
                    earlyStoppedAt = 0,
                )
            }
            try {
                readPluDatabase(config, fastRead)
                    .collect { event ->
                        when (event) {
                            is ScaleReadEvent.Started -> update { st ->
                                st.copy(readProgress = ReadProgress(0, event.total, 0, 0))
                            }
                            is ScaleReadEvent.Progress -> update { st ->
                                st.copy(
                                    readProgress = ReadProgress(
                                        event.processed,
                                        event.total,
                                        event.found,
                                        event.failed,
                                    ),
                                )
                            }
                            is ScaleReadEvent.EarlyStopped -> update { st ->
                                st.copy(earlyStoppedAt = event.processed)
                            }
                            is ScaleReadEvent.Completed -> update { st ->
                                st.copy(
                                    records = event.records,
                                    readPhase = ReadPhase.Done(
                                        count = event.records.size,
                                        failedCount = event.failedCount,
                                        durationMs = event.durationMs,
                                        earlyStoppedAt = st.earlyStoppedAt,
                                    ),
                                    readProgress = null,
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                // Отмена не является ошибкой — её обрабатывает cancelRead()
                if (e !is kotlinx.coroutines.CancellationException) {
                    update { st ->
                        st.copy(
                            readPhase = ReadPhase.Failed(describeError(e)),
                            readProgress = null,
                            earlyStoppedAt = 0,
                        )
                    }
                }
            }
        }
    }

    fun cancelRead() {
        readJob?.cancel()
        readJob = null
        update {
            it.copy(
                readPhase = ReadPhase.Cancelled(count = it.records.size),
                readProgress = null,
            )
        }
    }

    fun clearRecords() = update {
        it.copy(records = emptyList(), readPhase = ReadPhase.Idle, readProgress = null, earlyStoppedAt = 0)
    }

    // ------------------------------------------------------------- приватное

    private inline fun update(transform: (ScalesUiState) -> ScalesUiState) {
        _uiState.value = transform(_uiState.value)
    }

    /** Перечитывает сети телефона (в IO) и пересчитывает признак «весы в чужой подсети». */
    private suspend fun refreshNetworks() {
        val snapshot = withContext(ioDispatcher) { networkRepository.refresh() }
        update { state ->
            val next = state.copy(phoneNetworks = snapshot)
            next.copy(hostOutsideNetworks = hostOutside(next))
        }
    }

    /** Весы вне всех подсетей телефона? Считаем только когда сети известны. */
    private fun hostOutside(state: ScalesUiState): Boolean {
        val networks = state.phoneNetworks ?: return false
        if (!networks.isOnline || state.host.isBlank()) return false
        return !networks.contains(state.host)
    }

    /** Собирает конфиг из полей; при ошибке показывает событие и возвращает null. */
    private fun buildConfig(): ScaleConnectionConfig? {
        val port = _uiState.value.port.toIntOrNull() ?: 0
        val config = ScaleConnectionConfig(
            host = _uiState.value.host.trim(),
            udpPort = port,
            password = _uiState.value.password,
        )
        val error = config.validate()
        if (error != null) {
            _events.tryEmit(error)
            return null
        }
        return config
    }

    /** Ошибка подключения + сетевая подсказка (подсети телефона). */
    private fun describeConnectError(e: Throwable, config: ScaleConnectionConfig): String {
        val base = describeError(e)
        val networks = _uiState.value.phoneNetworks
        val outside = _uiState.value.hostOutsideNetworks
        val subnetHint = if (outside && networks != null) {
            "\n\nСеть телефона: ${networks.summary}. Весы ${config.host} — в другой подсети, " +
                "прямой обмен невозможен. Подключитесь к Wi-Fi-сети, где стоят весы " +
                "(или нажмите «Найти весы», чтобы проверить их наличие)."
        } else if (e is ScaleCommunicationException && e.kind == ScaleErrorKind.TIMEOUT &&
            networks?.isOnline == true
        ) {
            "\n\nВесы в подсети телефона, но не отвечают: проверьте, что UDP-порт " +
                "${config.udpPort} совпадает с настройками весов, а на точке доступа " +
                "отключена изоляция клиентов (AP/Client Isolation)."
        } else {
            ""
        }
        return base + subnetHint
    }

    private fun describeError(e: Throwable): String = when (e) {
        is ScaleCommunicationException -> e.message ?: "Нет связи с весами"
        is ScaleBusyException -> e.message ?: "Весы заняты другим хостом"
        is ScaleErrorException -> "Ошибка обмена: ${e.message}"
        else -> e.message ?: "Неизвестная ошибка"
    }
}
