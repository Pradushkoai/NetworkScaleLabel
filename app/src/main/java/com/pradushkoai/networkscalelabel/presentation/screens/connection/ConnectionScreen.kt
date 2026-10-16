package com.pradushkoai.networkscalelabel.presentation.screens.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pradushkoai.networkscalelabel.R
import com.pradushkoai.networkscalelabel.presentation.components.ConnectionCard
import com.pradushkoai.networkscalelabel.presentation.scales.ConnectionState
import com.pradushkoai.networkscalelabel.presentation.scales.ScalesUiState
import com.pradushkoai.networkscalelabel.presentation.scales.ScalesViewModel
import com.pradushkoai.networkscalelabel.presentation.theme.NetworkScaleLabelTheme

/**
 * Вкладка «Подключение»: параметры связи с весами, подключение,
 * сеть телефона и поиск весов в подсети.
 */
@Composable
fun ConnectionScreen(viewModel: ScalesViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConnectionContent(
        state = state,
        onHostChange = viewModel::onHostChange,
        onPortChange = viewModel::onPortChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConnect = viewModel::connect,
        onScan = viewModel::scan,
        onPickScannedHost = viewModel::onPickScannedHost,
    )
}

@Composable
fun ConnectionContent(
    state: ScalesUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    onScan: () -> Unit,
    onPickScannedHost: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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

        if (state.connection is ConnectionState.Connected) {
            item(key = "connected_hint") {
                Text(
                    text = stringResource(R.string.connection_hint_connected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConnectionContentPreview() {
    NetworkScaleLabelTheme {
        ConnectionContent(
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
        )
    }
}
