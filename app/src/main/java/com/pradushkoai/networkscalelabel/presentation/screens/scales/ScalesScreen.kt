package com.pradushkoai.networkscalelabel.presentation.scales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pradushkoai.networkscalelabel.R
import com.pradushkoai.networkscalelabel.domain.model.PluRecord
import com.pradushkoai.networkscalelabel.presentation.components.ConnectionCard
import com.pradushkoai.networkscalelabel.presentation.components.PluCard
import com.pradushkoai.networkscalelabel.presentation.components.PluDetailsDialog
import com.pradushkoai.networkscalelabel.presentation.theme.NetworkScaleLabelTheme
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Экран весов: подключение, чтение базы товаров, список и детали.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScalesScreen(
    viewModel: ScalesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScalesContent(
        state = state,
        onHostChange = viewModel::onHostChange,
        onPortChange = viewModel::onPortChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConnect = viewModel::connect,
        onScan = viewModel::scan,
        onPickScannedHost = viewModel::onPickScannedHost,
        onRead = viewModel::startRead,
        onCancelRead = viewModel::cancelRead,
        onQueryChange = viewModel::onQueryChange,
        onSelectRecord = viewModel::onSelectRecord,
        onFastReadChange = viewModel::onFastReadChange,
        events = viewModel.events,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScalesContent(
    state: ScalesUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    onScan: () -> Unit,
    onPickScannedHost: (String) -> Unit,
    onRead: () -> Unit,
    onCancelRead: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectRecord: (PluRecord?) -> Unit,
    onFastReadChange: (Boolean) -> Unit = {},
    events: kotlinx.coroutines.flow.SharedFlow<String>,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(events) {
        events.collect { snackbarHostState.showSnackbar(it) }
    }

    state.selected?.let { record ->
        PluDetailsDialog(record = record, onDismiss = { onSelectRecord(null) })
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "connection") {
                ConnectionCard(
                    host = state.host,
                    port = state.port,
                    password = state.password,
                    connection = state.connection,
                    phoneNetworks = state.phoneNetworks,
                    hostOutsideNetworks = state.hostOutsideNetworks,
                    scanPhase = state.scanPhase,
                    onHostChange = onHostChange,
                    onPortChange = onPortChange,
                    onPasswordChange = onPasswordChange,
                    onConnect = onConnect,
                    onScan = onScan,
                    onPickHost = onPickScannedHost,
                )
            }

            item(key = "read_action") {
                ReadActionBlock(
                    state = state,
                    onRead = onRead,
                    onCancelRead = onCancelRead,
                    onFastReadChange = onFastReadChange,
                )
            }

            if (state.records.isNotEmpty() || state.readPhase is ReadPhase.Reading) {
                item(key = "search") {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        label = { Text(stringResource(R.string.search_label)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            val visible = filterRecords(state.records, state.query)
            if (visible.isNotEmpty()) {
                item(key = "list_header") {
                    Text(
                        text = stringResource(R.string.list_header, visible.size, state.records.size),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(visible, key = { it.number }) { record ->
                    PluCard(record = record, onClick = { onSelectRecord(record) })
                }
            } else if (state.readPhase is ReadPhase.Done && state.query.isEmpty()) {
                item(key = "empty_done") {
                    Text(
                        text = stringResource(R.string.empty_after_read),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadActionBlock(
    state: ScalesUiState,
    onRead: () -> Unit,
    onCancelRead: () -> Unit,
    onFastReadChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRead,
                enabled = state.readPhase !is ReadPhase.Reading &&
                    state.connection is ConnectionState.Connected,
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Text(
                    text = stringResource(R.string.read_action),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (state.readPhase is ReadPhase.Reading) {
                OutlinedButton(onClick = onCancelRead) {
                    Icon(Icons.Filled.Cancel, contentDescription = null)
                    Text(
                        text = stringResource(R.string.cancel_action),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Switch(
                checked = state.fastRead,
                onCheckedChange = onFastReadChange,
                enabled = state.readPhase !is ReadPhase.Reading,
            )
            Text(
                text = stringResource(R.string.fast_read_label),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        when (val phase = state.readPhase) {
            is ReadPhase.Reading -> {
                val progress = state.readProgress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(
                            R.string.read_progress,
                            progress.processed,
                            progress.total,
                            progress.found,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
            is ReadPhase.Done -> {
                val seconds = phase.durationMs / 1000
                val speed = if (seconds > 0) state.earlyStoppedAt / seconds else 0
                Text(
                    text = if (phase.earlyStoppedAt > 0) {
                        stringResource(
                            R.string.read_done_early,
                            phase.count,
                            phase.failedCount,
                            seconds,
                            phase.earlyStoppedAt,
                        )
                    } else {
                        stringResource(
                            R.string.read_done,
                            phase.count,
                            phase.failedCount,
                            seconds,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (phase.earlyStoppedAt > 0 && seconds > 0) {
                    Text(
                        text = stringResource(R.string.read_speed, speed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            is ReadPhase.Cancelled -> Text(
                text = stringResource(R.string.read_cancelled, phase.count),
                style = MaterialTheme.typography.bodySmall,
            )
            is ReadPhase.Failed -> Text(
                text = stringResource(R.string.read_failed, phase.message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            ReadPhase.Idle -> Unit
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ScalesContentPreview() {
    // Заглушка потока событий только для превью — наружу не экспонируется
    val previewEvents = androidx.compose.runtime.remember { kotlinx.coroutines.flow.MutableSharedFlow<String>() }
    NetworkScaleLabelTheme {
        ScalesContent(
            state = ScalesUiState.initial().copy(
                host = "192.168.0.52",
                port = "1111",
                password = "0030",
            ),
            onHostChange = {},
            onPortChange = {},
            onPasswordChange = {},
            onConnect = {},
            onScan = {},
            onPickScannedHost = {},
            onRead = {},
            onCancelRead = {},
            onQueryChange = {},
            onSelectRecord = {},
            events = previewEvents,
        )
    }
}
