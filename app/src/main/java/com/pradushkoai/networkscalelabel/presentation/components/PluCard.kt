package com.pradushkoai.networkscalelabel.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pradushkoai.networkscalelabel.R
import com.pradushkoai.networkscalelabel.domain.model.PluRecord
import com.pradushkoai.networkscalelabel.presentation.scales.priceText
import com.pradushkoai.networkscalelabel.presentation.theme.NetworkScaleLabelTheme

/**
 * Карточка товара в списке: наименование, № ПЛУ/код, цена, тип.
 */
@Composable
fun PluCard(
    record: PluRecord,
    onClick: (PluRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(record) },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = record.displayName.ifBlank { stringResource(R.string.plu_no_name) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = record.priceText(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.plu_header, record.number, record.code),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (record.isPiece) {
                    true -> AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.type_piece)) },
                    )
                    false -> AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.type_weight)) },
                    )
                    null -> Unit
                }
                if (record.shelfLifeDays > 0) {
                    Text(
                        text = stringResource(R.string.shelf_life_short, record.shelfLifeDays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PluCardPreview() {
    NetworkScaleLabelTheme {
        PluCard(
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
            onClick = {},
        )
    }
}
