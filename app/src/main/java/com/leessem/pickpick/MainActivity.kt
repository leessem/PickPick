package com.leessem.pickpick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val historyStore = remember { LottoHistoryStore(context) }
    var history by remember { mutableStateOf(historyStore.getHistory()) }
    var currentBatch by remember { mutableStateOf(history.take(1)) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    fun generate(count: Int) {
        val base = System.currentTimeMillis()
        val batch = (0 until count).map { index ->
            LottoRecord(numbers = LottoNumberGenerator.generate(), timestamp = base - index)
        }
        historyStore.addRecords(batch)
        history = historyStore.getHistory()
        currentBatch = batch
    }

    fun deleteRecord(record: LottoRecord) {
        historyStore.deleteRecord(record.timestamp)
        history = historyStore.getHistory()
        currentBatch = currentBatch.filterNot { it.timestamp == record.timestamp }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "PickPick", style = MaterialTheme.typography.headlineLarge)
            Text(text = "PickPick에 오신 것을 환영합니다")

            Row(
                modifier = Modifier.padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { generate(1) }) {
                    Text(text = "1게임 생성")
                }
                Button(onClick = { generate(5) }) {
                    Text(text = "5게임 생성")
                }
            }

            if (currentBatch.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    currentBatch.forEach { record ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            record.numbers.forEach { number ->
                                LottoBall(number = number, size = 44.dp)
                            }
                        }
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "생성 기록", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showClearAllDialog = true }) {
                    Text(text = "전체 삭제")
                }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(history, key = { it.timestamp }) { record ->
                    HistoryRow(record = record, onDelete = { deleteRecord(record) })
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(text = "전체 삭제") },
            text = { Text(text = "생성 기록을 모두 삭제할까요? 이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    historyStore.clearAll()
                    history = emptyList()
                    currentBatch = emptyList()
                    showClearAllDialog = false
                }) {
                    Text(text = "삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(text = "취소")
                }
            }
        )
    }
}

@Composable
private fun HistoryRow(record: LottoRecord, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = dateFormat.format(Date(record.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            TextButton(onClick = onDelete) {
                Text(text = "삭제", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            record.numbers.forEach { number ->
                LottoBall(number = number, size = 26.dp)
            }
        }
    }
}

@Composable
private fun LottoBall(number: Int, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(lottoBallColor(number)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp
        )
    }
}

private fun lottoBallColor(number: Int): Color = when (number) {
    in 1..10 -> Color(0xFFFBC400)
    in 11..20 -> Color(0xFF69C8F2)
    in 21..30 -> Color(0xFFFF7272)
    in 31..40 -> Color(0xFFAAAAAA)
    else -> Color(0xFFB0D840)
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MaterialTheme {
        MainScreen()
    }
}
