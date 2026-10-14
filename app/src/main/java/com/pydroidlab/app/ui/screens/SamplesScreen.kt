package com.pydroidlab.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pydroidlab.app.data.CodeSample

@Composable
fun SamplesScreen(
    samples: List<CodeSample>,
    onOpenSample: (CodeSample, Boolean) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(samples, key = { it.id }) { sample ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSample(sample, false) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = sample.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = sample.category,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = sample.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = { onOpenSample(sample, false) }) {
                    Text("编辑", fontSize = 13.sp)
                }
                TextButton(onClick = { onOpenSample(sample, true) }) {
                    Text("运行", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
        }
    }
}
