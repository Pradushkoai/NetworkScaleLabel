package com.pradushkoai.networkscalelabel.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pradushkoai.networkscalelabel.R
import com.pradushkoai.networkscalelabel.domain.model.ScaleStatus
import com.pradushkoai.networkscalelabel.presentation.scales.ConnectionState
import com.pradushkoai.networkscalelabel.presentation.theme.NetworkScaleLabelTheme

/**
 * Карточка параметров подключения + кнопка «Подключить» + статус.
 */
@Composable
fun ConnectionCard(
    host: String,
    port: String,
    password: String,
    connection: ConnectionState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.connection_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = onHostChange,
                    label = { Text(stringResource(R.string.host_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(3f),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = { Text(stringResource(R.string.port_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.password_label)) },
                supportingText = { Text(stringResource(R.string.password_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onConnect,
                    enabled = connection !is ConnectionState.Connecting,
                ) {
                    Text(stringResource(R.string.connect_action))
                }
                when (connection) {
                    is ConnectionState.Connecting -> CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(4.dp),
                    )
                    else -> Unit
                }
            }
            ConnectionStatus(connection = connection)
        }
    }
}

@Composable
private fun ConnectionStatus(connection: ConnectionState) {
    when (connection) {
        is ConnectionState.Disconnected -> StatusText(
            text = stringResource(R.string.status_disconnected),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is ConnectionState.Connecting -> StatusText(
            text = stringResource(R.string.status_connecting),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is ConnectionState.Connected -> ConnectedSummary(connection.status)
        is ConnectionState.Failed -> StatusText(
            text = stringResource(R.string.status_failed, connection.message),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ConnectedSummary(status: ScaleStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatusText(
            text = stringResource(R.string.status_connected),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        StatusText(
            text = stringResource(
                R.string.status_scale_info,
                status.firmwareVersion,
                status.construction,
                status.scaleNumber,
                status.maxWeightKg,
            ),
        )
        StatusText(
            text = stringResource(
                R.string.status_table_info,
                status.goodsTableSize,
                status.messagesTableSize,
            ),
        )
    }
}

@Composable
private fun StatusText(
    text: String,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontWeight = fontWeight,
    )
}

@Preview(showBackground = true)
@Composable
fun ConnectionCardPreview() {
    NetworkScaleLabelTheme {
        ConnectionCard(
            host = "192.168.140.89",
            port = "1111",
            password = "0030",
            connection = ConnectionState.Disconnected,
            onHostChange = {},
            onPortChange = {},
            onPasswordChange = {},
            onConnect = {},
        )
    }
}
