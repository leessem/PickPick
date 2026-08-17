package com.leessem.pickpick

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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

private enum class Stage1Mode { AUTO, MANUAL }

private val FULL_RANGE = (1..45).toList()
private const val STAGE1_GAME_COUNT = 5
private const val STAGE2_REGULAR_GAME_COUNT = 4

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val generationSetStore = remember { GenerationSetStore(context) }

    var stage1Mode by remember { mutableStateOf<Stage1Mode?>(null) }
    var stage1Games by remember { mutableStateOf<List<LottoGame>>(emptyList()) }
    var manualSelection by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var stage2Games by remember { mutableStateOf<List<LottoGame>>(emptyList()) }
    var neverAppearedPool by remember { mutableStateOf<List<Int>?>(null) }
    var finalGameResult by remember { mutableStateOf<GenerationResult?>(null) }
    var savedGenerationSets by remember { mutableStateOf(generationSetStore.getAll()) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var showSaveRoundDialog by remember { mutableStateOf(false) }
    var editingRoundSetId by remember { mutableStateOf<String?>(null) }

    fun resetFromStage1() {
        stage1Games = emptyList()
        manualSelection = emptySet()
        stage2Games = emptyList()
        neverAppearedPool = null
        finalGameResult = null
    }

    fun startAuto() {
        stage1Mode = Stage1Mode.AUTO
        resetFromStage1()
        stage1Games = LottoStageGenerator.generateGames(STAGE1_GAME_COUNT, FULL_RANGE)
    }

    fun startManual() {
        stage1Mode = Stage1Mode.MANUAL
        resetFromStage1()
    }

    fun toggleManualNumber(number: Int) {
        if (stage1Games.size >= STAGE1_GAME_COUNT) return
        val updated = if (number in manualSelection) manualSelection - number else manualSelection + number
        if (updated.size > LottoNumberGenerator.PICK_COUNT) return
        if (updated.size == LottoNumberGenerator.PICK_COUNT) {
            stage1Games = stage1Games + LottoGame(updated.sorted())
            manualSelection = emptySet()
        } else {
            manualSelection = updated
        }
    }

    // generateGames/generateGame are the same building blocks LottoStageGenerator.generateSession
    // uses internally; only the pool-exclusion arithmetic (already implemented and tested in
    // LottoStageGenerator.assembleSession) is repeated here, because the UI needs Stage 2 and the
    // final game to be separate, explicit button presses rather than one bundled call.
    fun generateStage2() {
        val stage1Used = stage1Games.flatMap { it.numbers }.toSet()
        val stage2Pool = FULL_RANGE - stage1Used
        val games = LottoStageGenerator.generateGames(STAGE2_REGULAR_GAME_COUNT, stage2Pool)
        stage2Games = games
        val stage2Used = games.flatMap { it.numbers }.toSet()
        neverAppearedPool = FULL_RANGE - stage1Used - stage2Used
        finalGameResult = null
    }

    fun generateFinalGame() {
        val pool = neverAppearedPool ?: return
        finalGameResult = LottoNumberGenerator.generateGame(pool)
    }

    fun saveWithRound(round: Int?) {
        val finalGame = (finalGameResult as? GenerationResult.Success)?.let { LottoGame(it.numbers) }
        val set = GenerationSet(
            id = UUID.randomUUID().toString(),
            lottoRound = round,
            createdAt = System.currentTimeMillis(),
            stage1Games = stage1Games,
            stage2Games = stage2Games,
            finalGame = finalGame
        )
        generationSetStore.save(set)
        savedGenerationSets = generationSetStore.getAll()
        showSaveRoundDialog = false
        Toast.makeText(context, "생성 세트를 저장했습니다.", Toast.LENGTH_SHORT).show()
    }

    fun updateRound(id: String, round: Int?) {
        generationSetStore.updateRound(id, round)
        savedGenerationSets = generationSetStore.getAll()
        editingRoundSetId = null
    }

    fun deleteSet(id: String) {
        generationSetStore.delete(id)
        savedGenerationSets = generationSetStore.getAll()
        pendingDeleteId = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(text = "PickPick", style = MaterialTheme.typography.headlineLarge)

        Text(
            text = "1단계",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 20.dp)
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { startAuto() }) { Text(text = "자동 생성") }
            Button(onClick = { startManual() }) { Text(text = "직접 입력") }
        }

        if (stage1Mode == Stage1Mode.MANUAL && stage1Games.size < STAGE1_GAME_COUNT) {
            Text(
                text = "${stage1Games.size + 1}게임 선택 중 (${manualSelection.size}/${LottoNumberGenerator.PICK_COUNT})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            if (manualSelection.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    manualSelection.sorted().forEach { number -> LottoBall(number = number, size = 28.dp) }
                }
            }
            NumberPickerGrid(
                selected = manualSelection,
                onNumberClick = { toggleManualNumber(it) },
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (stage1Games.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                stage1Games.forEachIndexed { index, game ->
                    GameRow(label = "${index + 1}게임", game = game)
                }
            }
        }

        Button(
            onClick = { generateStage2() },
            enabled = stage1Games.size == STAGE1_GAME_COUNT,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Text(text = "2단계 생성")
        }

        if (stage2Games.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(text = "2단계", style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.padding(top = 8.dp)) {
                stage2Games.forEachIndexed { index, game ->
                    GameRow(label = "${index + 1}게임", game = game)
                }
            }
        }

        neverAppearedPool?.let { pool ->
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(text = "미출현 번호", style = MaterialTheme.typography.titleLarge)
            if (pool.size >= LottoNumberGenerator.PICK_COUNT) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pool.sorted().forEach { number -> LottoBall(number = number, size = 28.dp) }
                }
                Button(onClick = { generateFinalGame() }, modifier = Modifier.padding(top = 12.dp)) {
                    Text(text = "최종 1게임 생성")
                }
            } else {
                Text(
                    text = "미출현 번호가 ${pool.size}개뿐이라 최종 게임을 생성할 수 없습니다.",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        finalGameResult?.let { result ->
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(text = "최종 게임", style = MaterialTheme.typography.titleLarge)
            when (result) {
                is GenerationResult.Success -> {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        result.numbers.forEach { number -> LottoBall(number = number, size = 44.dp) }
                    }
                }
                is GenerationResult.InsufficientNumbers -> {
                    Text(
                        text = "번호가 부족해 최종 게임을 생성할 수 없습니다.",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Button(
            onClick = { showSaveRoundDialog = true },
            enabled = stage1Games.size == STAGE1_GAME_COUNT && stage2Games.size == STAGE2_REGULAR_GAME_COUNT,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Text(text = "이 생성 결과 저장")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text(text = "저장된 생성 세트", style = MaterialTheme.typography.titleLarge)
        if (savedGenerationSets.isEmpty()) {
            Text(
                text = "저장된 생성 세트가 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                savedGenerationSets.forEach { set ->
                    SavedSetRow(
                        set = set,
                        onEditRoundClick = { editingRoundSetId = set.id },
                        onDeleteClick = { pendingDeleteId = set.id }
                    )
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(text = "생성 세트 삭제") },
            text = { Text(text = "이 생성 세트를 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = { deleteSet(id) }) {
                    Text(text = "삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(text = "취소")
                }
            }
        )
    }

    if (showSaveRoundDialog) {
        RoundInputDialog(
            title = "생성 결과 저장",
            initialValue = "",
            onDismiss = { showSaveRoundDialog = false },
            onConfirm = { round -> saveWithRound(round) }
        )
    }

    editingRoundSetId?.let { id ->
        val target = savedGenerationSets.first { it.id == id }
        RoundInputDialog(
            title = "회차 수정",
            initialValue = target.lottoRound?.toString().orEmpty(),
            onDismiss = { editingRoundSetId = null },
            onConfirm = { round -> updateRound(id, round) }
        )
    }
}

@Composable
private fun RoundInputDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit
) {
    var input by remember { mutableStateOf(initialValue) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                Text(text = "로또 회차", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = input,
                    onValueChange = { new -> if (new.all { it.isDigit() }) input = new },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = input.trim()
                if (trimmed.isEmpty()) {
                    onConfirm(null)
                    return@TextButton
                }
                val parsed = trimmed.toIntOrNull()
                if (parsed == null || parsed <= 0) {
                    Toast.makeText(context, "회차는 1 이상의 숫자로 입력하세요.", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                onConfirm(parsed)
            }) {
                Text(text = "저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "취소")
            }
        }
    )
}

@Composable
private fun NumberPickerGrid(
    selected: Set<Int>,
    onNumberClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        FULL_RANGE.chunked(9).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { number ->
                    NumberCell(
                        number = number,
                        selected = number in selected,
                        onClick = { onNumberClick(number) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun NumberCell(number: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (selected) lottoBallColor(number) else Color(0xFFE0E0E0))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            color = if (selected) Color.White else Color.Black,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun GameRow(label: String, game: LottoGame) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.width(56.dp), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            game.numbers.forEach { number -> LottoBall(number = number, size = 28.dp) }
        }
    }
}

@Composable
private fun SavedSetRow(set: GenerationSet, onEditRoundClick: () -> Unit, onDeleteClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = set.lottoRound?.let { "제${it}회" } ?: "회차 미지정",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = dateFormat.format(Date(set.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Text(
                text = "1단계 ${set.stage1Games.size}게임 · 2단계 ${set.stage2Games.size}게임 · " +
                    if (set.finalGame != null) "최종 조합 있음" else "최종 조합 없음",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        Row {
            TextButton(onClick = onEditRoundClick) {
                Text(text = "회차 수정")
            }
            TextButton(onClick = onDeleteClick) {
                Text(text = "삭제")
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
