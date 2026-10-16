package com.pradushkoai.networkscalelabel.presentation.screens.products

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pradushkoai.networkscalelabel.R
import com.pradushkoai.networkscalelabel.domain.model.PluRecord
import com.pradushkoai.networkscalelabel.presentation.components.PluCard
import com.pradushkoai.networkscalelabel.presentation.components.PluDetailsDialog
import com.pradushkoai.networkscalelabel.presentation.scales.ConnectionState
import com.pradushkoai.networkscalelabel.presentation.scales.ReadPhase
import com.pradushkoai.networkscalelabel.presentation.scales.ScalesUiState
import com.pradushkoai.networkscalelabel.presentation.scales.ScalesViewModel
import com.pradushkoai.networkscalelabel.presentation.scales.filterRecords
import com.pradushkoai.networkscalelabel.presentation.theme.NetworkScaleLabelTheme

/**
 * Вкладка «Товары»: чтение базы ПЛУ из весов, поиск по списку,
 * карточки и детали товаров. Подключение выполняется на вкладке
 * «Подключение» — здесь только выгрузка и просмотр.
 */
@Composable
fun ProductsScreen(viewModel: ScalesViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProductsContent(
        state = state,
        onRead = viewModel::startRead,
        onCancelRead = viewModel::cancelRead,
        onQueryChange = viewModel::onQueryChange,
        onSelectRecord = viewModel::onSelectRecord,
        onFastReadChange = viewModel::onFastReadChange,
    )
}

@Composable
fun ProductsContent(
    state: ScalesUiState,
    onRead: () -> Unit,
    onCancelRead: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectRecord: (PluRecord?) -> Unit,
    onFastReadChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    state.selected?.let { record ->
        PluDetailsDialog(record = record, onDismiss = { onSelectRecord(null) })
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "read_action") {
            ReadActionBlock(
                state = state,
                onRead = onRead,
                onCancelRead = onCancelRead,
                onFastReadChange = onFastReadChange,
            )
        }

        if (state.connection !is ConnectionState.Connected &&
            state.readPhase is ReadPhase.Idle
        ) {
            item(key = "not_connected_hint") {
                Card(colors = CardDefaults.cardColors()) {
                    Text(
                        text = stringResource(R.string.products_not_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
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
fun ProductsContentPreview() {
    NetworkScaleLabelTheme {
        ProductsContent(
            state = ScalesUiState.initial().copy(
                host = "192.168.0.52",
                port = "1111",
                password = "0030",
                connection = ConnectionState.Connected(
                    com.pradushkoai.networkscalelabel.domain.model.ScaleStatus(
                        firmwareVersion = "4.5",
                        construction = 1,
                        firmwareDate = "01.01.26",
                        goodsTableSize = 13_700,
                        messagesTableSize = 10,
                        linesPerMessage = 4,
                        maxWeightKg = 15,
                        scaleNumber = 1,
                        currentDate = "16.10.26",
                        currentTime = "12.00.00",
                        mode = 0,
                        isQuickLoadMode = false,
                    ),
                ),
                records = listOf(
                    PluRecord(
                        number = 12,
                        code = 200012L,
                        name1 = "Колбаса «Докторская»",
                        name2 = "в/у, ГОСТ",
                        priceMinorUnits = 42_990L,
                        shelfLifeDays = 30,
                        tareGrams = 0,
                        groupCode = 21,
                        messageNumber = 1,
                        images = 0,
                        rostest = "",
                        isPiece = false,
                        labelFormat = 2,
                        barcodeStructure = 5,
                        realizationDate = "00.00.00",
                        manufactureDate = null,
                        extended = true,
                    ),
                ),
            ),
            onRead = {},
            onCancelRead = {},
            onQueryChange = {},
            onSelectRecord = {},
            onFastReadChange = {},
        )
    }
}
