package com.pradushkoai.networkscalelabel.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.pradushkoai.networkscalelabel.R
import com.pradushkoai.networkscalelabel.domain.model.PluRecord
import com.pradushkoai.networkscalelabel.presentation.scales.priceText
import com.pradushkoai.networkscalelabel.presentation.theme.NetworkScaleLabelTheme

/**
 * Диалог с полными данными ПЛУ.
 */
@Composable
fun PluDetailsDialog(
    record: PluRecord,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_action)) }
        },
        title = {
            Text(record.displayName.ifBlank { stringResource(R.string.plu_no_name) })
        },
        text = {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailRow(stringResource(R.string.detail_plu), record.number.toString())
                DetailRow(stringResource(R.string.detail_code), record.code.toString())
                DetailRow(stringResource(R.string.detail_price), record.priceText())
                record.isPiece?.let { piece ->
                    DetailRow(
                        stringResource(R.string.detail_type),
                        stringResource(if (piece) R.string.type_piece else R.string.type_weight),
                    )
                }
                if (record.name1.isNotBlank()) {
                    DetailRow(stringResource(R.string.detail_name1), record.name1)
                }
                if (record.name2.isNotBlank()) {
                    DetailRow(stringResource(R.string.detail_name2), record.name2)
                }
                DetailRow(
                    stringResource(R.string.detail_shelf_life),
                    if (record.shelfLifeDays > 0) {
                        stringResource(R.string.detail_days, record.shelfLifeDays)
                    } else {
                        stringResource(R.string.detail_not_set)
                    },
                )
                if (record.tareGrams > 0) {
                    DetailRow(stringResource(R.string.detail_tare), "${record.tareGrams} г")
                }
                DetailRow(stringResource(R.string.detail_group_code), record.groupCode.toString())
                record.manufactureDate?.let {
                    DetailRow(stringResource(R.string.detail_manufacture_date), it)
                }
                record.realizationDate?.let {
                    DetailRow(stringResource(R.string.detail_realization_date), it)
                }
                DetailRow(
                    stringResource(R.string.detail_message),
                    if (record.messageNumber > 0) {
                        stringResource(R.string.detail_message_no, record.messageNumber)
                    } else {
                        stringResource(R.string.detail_not_set)
                    },
                )
                if (record.images > 0) {
                    DetailRow(stringResource(R.string.detail_images), record.images.toString())
                }
                if (record.rostest.isNotBlank()) {
                    DetailRow(stringResource(R.string.detail_rostest), record.rostest)
                }
                if (record.extended) {
                    record.labelFormat?.let {
                        DetailRow(stringResource(R.string.detail_label_format), it.toString())
                    }
                    record.barcodeStructure?.let {
                        DetailRow(stringResource(R.string.detail_bc_structure), it.toString())
                    }
                    DetailRow(
                        stringResource(R.string.detail_read_format),
                        stringResource(R.string.detail_format_ext),
                    )
                } else {
                    DetailRow(
                        stringResource(R.string.detail_read_format),
                        stringResource(R.string.detail_format_base),
                    )
                }
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PluDetailsDialogPreview() {
    NetworkScaleLabelTheme {
        PluDetailsDialog(
            record = PluRecord(
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
            onDismiss = {},
        )
    }
}
