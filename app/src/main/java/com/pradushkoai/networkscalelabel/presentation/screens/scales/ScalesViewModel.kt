package com.pradushkoai.networkscalelabel.presentation.scales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pradushkoai.networkscalelabel.domain.model.PluRecord
import com.pradushkoai.networkscalelabel.domain.model.ScaleBusyException
import com.pradushkoai.networkscalelabel.domain.model.ScaleCommunicationException
import com.pradushkoai.networkscalelabel.domain.model.ScaleConnectionConfig
import com.pradushkoai.networkscalelabel.domain.model.ScaleErrorException
import com.pradushkoai.networkscalelabel.domain.model.ScaleReadEvent
import com.pradushkoai.networkscalelabel.domain.model.validate
import com.pradushkoai.networkscalelabel.domain.repository.ConnectionSettingsRepository
import com.pradushkoai.networkscalelabel.domain.usecase.ConnectScalesUseCase
import com.pradushkoai.networkscalelabel.domain.usecase.ReadPluDatabaseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel экрана весов: подключение, чтение базы товаров, поиск.
 */
@HiltViewModel
class ScalesViewModel @Inject constructor(
    private val connectScales: ConnectScalesUseCase,
    private val readPluDatabase: ReadPluDatabaseUseCase,
    private val settings: ConnectionSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScalesUiState.initial())
    val uiState: StateFlow<ScalesUiState> = _uiState.asStateFlow()

    /** Одноразовые события (закуски/snackbar). */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var readJob: Job? = null

    init {
        val saved = settings.load()
        _uiState.value = ScalesUiState.initial().copy(
            host = saved.host,
            port = saved.udpPort.toString(),
            password = saved.password,
        )
    }

    // ------------------------------------------------------------- ввод

    fun onHostChange(value: String) = update { it.copy(host = value.trim(), connection = ConnectionState.Disconnected) }

    fun onPortChange(value: String) = update { it.copy(port = value.filter(Char::isDigit).take(5), connection = ConnectionState.Disconnected) }

    fun onPasswordChange(value: String) = update {
        it.copy(password = value.filter(Char::isDigit).take(4), connection = ConnectionState.Disconnected)
    }

    fun onQueryChange(value: String) = update { it.copy(query = value) }

    fun onSelectRecord(record: PluRecord?) = update { it.copy(selected = record) }

    // ------------------------------------------------------------- действия

    fun connect() {
        val config = buildConfig() ?: return
        viewModelScope.launch {
            update { it.copy(connection = ConnectionState.Connecting) }
            try {
                val status = connectScales(config)
                settings.save(config)
                update { it.copy(connection = ConnectionState.Connected(status)) }
            } catch (e: Exception) {
                update { it.copy(connection = ConnectionState.Failed(describeError(e))) }
            }
        }
    }

    fun startRead() {
        val config = buildConfig() ?: return
        if (readJob?.isActive == true) return
        readJob = viewModelScope.launch {
            update {
                it.copy(
                    readPhase = ReadPhase.Reading,
                    readProgress = null,
                    records = emptyList(),
                )
            }
            try {
                readPluDatabase(config)
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
                            is ScaleReadEvent.Completed -> update { st ->
                                st.copy(
                                    records = event.records,
                                    readPhase = ReadPhase.Done(
                                        count = event.records.size,
                                        failedCount = event.failedCount,
                                        durationMs = event.durationMs,
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

    fun clearRecords() = update { it.copy(records = emptyList(), readPhase = ReadPhase.Idle, readProgress = null) }

    // ------------------------------------------------------------- приватное

    private inline fun update(transform: (ScalesUiState) -> ScalesUiState) {
        _uiState.value = transform(_uiState.value)
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

    private fun describeError(e: Throwable): String = when (e) {
        is ScaleCommunicationException -> e.message ?: "Нет связи с весами"
        is ScaleBusyException -> e.message ?: "Весы заняты другим хостом"
        is ScaleErrorException -> "Ошибка обмена: ${e.message}"
        else -> e.message ?: "Неизвестная ошибка"
    }
}
