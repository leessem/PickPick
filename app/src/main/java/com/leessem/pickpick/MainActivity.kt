package com.leessem.pickpick

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

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
private const val STAGE2_GAME_COUNT = 2
private const val STAGE3_GAME_COUNT = 3
private const val TOTAL_GAME_COUNT = STAGE1_GAME_COUNT + STAGE2_GAME_COUNT + STAGE3_GAME_COUNT

private enum class LatestCheckStatus { CHECKING, DONE, FAILED }

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val generationSetStore = remember { GenerationSetStore(context) }
    val drawStore = remember { LottoDrawStore(context) }

    var latestCheckStatus by remember { mutableStateOf(LatestCheckStatus.CHECKING) }

    // Runs exactly once per process lifetime (keyed on Unit), not per recomposition or per
    // screen navigation — MainScreen itself is only ever composed once at the app's root.
    LaunchedEffect(Unit) {
        val seed = drawStore.latestRound() ?: LottoLatestRoundChecker.estimateCurrentRound()
        when (val result = LottoLatestRoundChecker.findLatestRound(seed)) {
            is LottoLatestRoundChecker.Result.Found -> {
                if (drawStore.get(result.round) == null) {
                    val fetchResult = LottoDrawRepository.getDraw(result.round)
                    if (fetchResult is LottoDrawFetchResult.Success) {
                        drawStore.save(fetchResult.draw)
                    }
                }
                latestCheckStatus = LatestCheckStatus.DONE
            }
            is LottoLatestRoundChecker.Result.NetworkError -> latestCheckStatus = LatestCheckStatus.FAILED
        }
    }

    var stage1Mode by remember { mutableStateOf<Stage1Mode?>(null) }
    var stage1Games by remember { mutableStateOf<List<LottoGame>>(emptyList()) }
    var manualSelection by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var stage2Games by remember { mutableStateOf<List<LottoGame>>(emptyList()) }
    var stage3BasisPool by remember { mutableStateOf<List<Int>?>(null) }
    var stage3Games by remember { mutableStateOf<List<LottoGame>>(emptyList()) }
    var savedGenerationSets by remember { mutableStateOf(generationSetStore.getAll()) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var showSaveRoundDialog by remember { mutableStateOf(false) }
    var editingRoundSetId by remember { mutableStateOf<String?>(null) }
    var selectedGenerationSet by remember { mutableStateOf<GenerationSet?>(null) }

    fun resetFromStage1() {
        stage1Games = emptyList()
        manualSelection = emptySet()
        stage2Games = emptyList()
        stage3BasisPool = null
        stage3Games = emptyList()
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

    // splitStage1/neverAppearedPool/generateStageGame are the same building blocks
    // LottoStageGenerator.generateSession uses internally, reused directly here because the UI
    // needs Stage 2 and Stage 3 to be separate, explicit button presses rather than one bundled
    // call. Stage 3's basis (and thus its never-appeared pool) is fixed the moment Stage 2 is
    // generated — the leftover 2 Stage 1 games never change afterward, so Stage 3's pool must not
    // be recomputed from whatever Stage 2 happens to generate.
    fun generateStage2() {
        val (stage2Basis, stage3Basis) = LottoStageGenerator.splitStage1(stage1Games)
        val stage2Pool = LottoStageGenerator.neverAppearedPool(stage2Basis)
        stage2Games = List(STAGE2_GAME_COUNT) { LottoStageGenerator.generateStageGame(stage2Pool) }
        stage3BasisPool = LottoStageGenerator.neverAppearedPool(stage3Basis)
        stage3Games = emptyList()
    }

    fun generateStage3() {
        val pool = stage3BasisPool ?: return
        stage3Games = List(STAGE3_GAME_COUNT) { LottoStageGenerator.generateStageGame(pool) }
    }

    fun saveWithRound(round: Int?) {
        val set = GenerationSet(
            id = UUID.randomUUID().toString(),
            lottoRound = round,
            createdAt = System.currentTimeMillis(),
            stage1Games = stage1Games,
            stage2Games = stage2Games,
            stage3Games = stage3Games
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
        if (selectedGenerationSet?.id == id) {
            selectedGenerationSet = null
        }
    }

    val currentSelection = selectedGenerationSet
    if (currentSelection != null) {
        GenerationSetDetailScreen(
            set = currentSelection,
            drawStore = drawStore,
            onBack = { selectedGenerationSet = null },
            onDeleteClick = { pendingDeleteId = currentSelection.id }
        )
        pendingDeleteId?.let { id ->
            DeleteSetConfirmDialog(onConfirm = { deleteSet(id) }, onDismiss = { pendingDeleteId = null })
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Without this, targetSdk 36 draws edge-to-edge on Android 15+ and the bottom of the
            // saved-set list (and the save/generate buttons above it) end up under the system
            // navigation bar on a real phone — not reproduced on every emulator/nav-mode combo.
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(text = "PickPick", style = MaterialTheme.typography.headlineLarge)

        when (latestCheckStatus) {
            LatestCheckStatus.CHECKING -> Text(
                text = "최신 당첨 결과 확인 중...",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
            LatestCheckStatus.FAILED -> Text(
                text = "최신 당첨 결과를 확인하지 못했습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
            LatestCheckStatus.DONE -> {}
        }

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

        // Always rendered (like the "2단계 생성" button above), enabled only once stage2 is
        // complete — mirrors that button's visible-but-disabled pattern instead of hiding this
        // button until stage2 exists, so the full 1->2->3 flow is visible from the start.
        Button(
            onClick = { generateStage3() },
            enabled = stage2Games.size == STAGE2_GAME_COUNT,
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Text(text = "3단계 생성")
        }

        if (stage3Games.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(text = "3단계", style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.padding(top = 8.dp)) {
                stage3Games.forEachIndexed { index, game ->
                    GameRow(label = "${index + 1}게임", game = game)
                }
            }
        }

        val totalGeneratedGames = stage1Games.size + stage2Games.size + stage3Games.size
        Text(
            text = "생성된 게임 $totalGeneratedGames / $TOTAL_GAME_COUNT",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (totalGeneratedGames == TOTAL_GAME_COUNT) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 20.dp)
        )

        Button(
            onClick = { showSaveRoundDialog = true },
            enabled = stage1Games.size == STAGE1_GAME_COUNT &&
                stage2Games.size == STAGE2_GAME_COUNT &&
                stage3Games.size == STAGE3_GAME_COUNT,
            modifier = Modifier.padding(top = 12.dp)
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
                        onClick = { selectedGenerationSet = set },
                        onEditRoundClick = { editingRoundSetId = set.id },
                        onDeleteClick = { pendingDeleteId = set.id }
                    )
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        DeleteSetConfirmDialog(onConfirm = { deleteSet(id) }, onDismiss = { pendingDeleteId = null })
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
private fun GameRow(label: String, game: LottoGame, checkResult: LottoCheckResult? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, modifier = Modifier.width(56.dp), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                game.numbers.forEach { number -> LottoBall(number = number, size = 28.dp) }
            }
        }
        if (checkResult != null) {
            // A non-NONE rank is a win, however small (5th place included) — give it the same
            // emphasis as 1st place's existing "🎉" text so it never reads identically to
            // "미당첨" at a glance. Rank determination itself (LottoResultChecker) is untouched;
            // this only changes how an already-computed rank is displayed.
            val isWin = checkResult.rank != LottoRank.NONE
            val resultColor = if (isWin) MaterialTheme.colorScheme.primary else Color.Gray
            val resultWeight = if (isWin) FontWeight.Bold else FontWeight.Normal
            Column(modifier = Modifier.padding(start = 56.dp, top = 2.dp)) {
                Text(
                    text = lottoMatchCountText(checkResult),
                    style = MaterialTheme.typography.bodySmall,
                    color = resultColor,
                    fontWeight = resultWeight
                )
                Text(
                    text = lottoRankDisplayText(checkResult),
                    style = MaterialTheme.typography.bodySmall,
                    color = resultColor,
                    fontWeight = resultWeight
                )
            }
        }
    }
}

@Composable
private fun SummaryCountRow(label: String, count: Int) {
    // A non-zero win count is emphasized the same way GameRow emphasizes a winning game, so a
    // "5등  1" line never blends into the surrounding all-gray summary the way it used to.
    val isWin = count > 0
    Text(
        text = "$label  $count",
        style = MaterialTheme.typography.bodySmall,
        color = if (isWin) MaterialTheme.colorScheme.primary else Color.Gray,
        fontWeight = if (isWin) FontWeight.Bold else FontWeight.Normal
    )
}

@Composable
private fun SavedSetRow(
    set: GenerationSet,
    onClick: () -> Unit,
    onEditRoundClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                    "3단계 ${set.stage3Games.size}게임",
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

private sealed class DrawCheckState {
    object NoRound : DrawCheckState()
    object Loading : DrawCheckState()
    data class Success(val draw: LottoDrawResult, val check: GenerationCheckResult) : DrawCheckState()
    // Holds the fetchResult (not just its message) so the UI can decide whether this specific
    // failure is retryable (NetworkError only) without re-deriving that from display text.
    data class Failure(val fetchResult: LottoDrawFetchResult) : DrawCheckState()
}

/** Only a NetworkError is worth retrying — NotFound/InvalidData will fail the same way again. */
internal fun isRetryableFailure(fetchResult: LottoDrawFetchResult): Boolean =
    fetchResult is LottoDrawFetchResult.NetworkError

@Composable
private fun GenerationSetDetailScreen(
    set: GenerationSet,
    drawStore: LottoDrawStore,
    onBack: () -> Unit,
    onDeleteClick: () -> Unit
) {
    BackHandler(onBack = onBack)
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA) }
    val drawDateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.KOREA) }
    val coroutineScope = rememberCoroutineScope()

    var drawCheckState by remember(set.id) {
        mutableStateOf<DrawCheckState>(if (set.lottoRound == null) DrawCheckState.NoRound else DrawCheckState.Loading)
    }
    var isRetrying by remember(set.id) { mutableStateOf(false) }

    // Cache-first: LottoDrawStore is checked before ever calling LottoDrawRepository (see
    // resolveDraw in LottoDrawCache.kt). Shared by the initial load below and by the "다시 시도"
    // button so a retry re-runs exactly the same lookup, not a separate code path.
    suspend fun loadDraw() {
        val round = set.lottoRound ?: return
        when (val result = resolveDraw(round, getCached = drawStore::get, saveToCache = drawStore::save)) {
            is DrawLookupResult.FromCache -> {
                val checkResult = LottoResultChecker.check(set, result.draw)
                drawCheckState = DrawCheckState.Success(result.draw, checkResult)
            }
            is DrawLookupResult.Fetched -> {
                val checkResult = LottoResultChecker.check(set, result.draw)
                drawCheckState = DrawCheckState.Success(result.draw, checkResult)
            }
            is DrawLookupResult.Failed -> {
                drawCheckState = DrawCheckState.Failure(result.fetchResult)
            }
        }
    }

    // Keyed on set.id so this only re-fetches when the displayed set changes, not on every
    // recomposition (e.g. a state update from the delete dialog).
    LaunchedEffect(set.id) {
        if (set.lottoRound == null) return@LaunchedEffect
        drawCheckState = DrawCheckState.Loading
        loadDraw()
    }

    // No separate loading state here on purpose — the existing error message and retry button
    // stay visible the whole time, only disabled, so a retry doesn't flash a different screen.
    fun retryLoadDraw() {
        if (isRetrying) return
        isRetrying = true
        coroutineScope.launch {
            loadDraw()
            isRetrying = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            // Same bottom-nav-bar clipping as MainScreen — the last stage3 game here was
            // getting hidden behind the system navigation bar on a real phone.
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text(text = "목록으로") }
            TextButton(onClick = onDeleteClick) { Text(text = "삭제") }
        }

        Text(
            text = set.lottoRound?.let { "제${it}회" } ?: "회차 미지정",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = dateFormat.format(Date(set.createdAt)),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        // Shown unconditionally — independent of drawCheckState — since the game count is a
        // fact about the saved set itself, not about whether a draw was found for its round.
        Text(
            text = "총 ${set.stage1Games.size + set.stage2Games.size + set.stage3Games.size}게임",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text(text = "당첨 결과", style = MaterialTheme.typography.titleLarge)
        when (val state = drawCheckState) {
            is DrawCheckState.NoRound -> {
                Text(
                    text = "회차가 지정되지 않아 당첨 결과를 확인할 수 없습니다.",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            is DrawCheckState.Loading -> {
                Text(text = "당첨 결과 확인 중...", modifier = Modifier.padding(top = 8.dp))
            }
            is DrawCheckState.Failure -> {
                Text(
                    text = lottoDrawFetchErrorMessage(state.fetchResult),
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (isRetryableFailure(state.fetchResult)) {
                    Button(
                        onClick = { retryLoadDraw() },
                        enabled = !isRetrying,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(text = "다시 시도")
                    }
                }
            }
            is DrawCheckState.Success -> {
                Text(
                    text = "제${state.draw.round}회",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "추첨일: ${drawDateFormat.format(Date(state.draw.drawDate))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(text = "당첨번호:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    state.draw.winningNumbers.sorted().forEach { number -> LottoBall(number = number, size = 36.dp) }
                }
                Text(text = "보너스:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                LottoBall(number = state.draw.bonusNumber, size = 36.dp)

                val summary = LottoResultSummarizer.summarize(state.check)
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    SummaryCountRow(label = "1등", count = summary.firstCount)
                    SummaryCountRow(label = "2등", count = summary.secondCount)
                    SummaryCountRow(label = "3등", count = summary.thirdCount)
                    SummaryCountRow(label = "4등", count = summary.fourthCount)
                    SummaryCountRow(label = "5등", count = summary.fifthCount)
                    Text(text = "미당첨  ${summary.noneCount}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }

        val checkResult = (drawCheckState as? DrawCheckState.Success)?.check

        Text(text = "1단계", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp))
        Column(modifier = Modifier.padding(top = 8.dp)) {
            set.stage1Games.forEachIndexed { index, game ->
                GameRow(label = "${index + 1}게임", game = game, checkResult = checkResult?.stage1Results?.getOrNull(index))
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text(text = "2단계", style = MaterialTheme.typography.titleLarge)
        Column(modifier = Modifier.padding(top = 8.dp)) {
            set.stage2Games.forEachIndexed { index, game ->
                GameRow(label = "${index + 1}게임", game = game, checkResult = checkResult?.stage2Results?.getOrNull(index))
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text(text = "3단계", style = MaterialTheme.typography.titleLarge)
        Column(modifier = Modifier.padding(top = 8.dp)) {
            set.stage3Games.forEachIndexed { index, game ->
                GameRow(label = "${index + 1}게임", game = game, checkResult = checkResult?.stage3Results?.getOrNull(index))
            }
        }
    }
}

@Composable
private fun DeleteSetConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "생성 세트 삭제") },
        text = { Text(text = "이 생성 세트를 삭제할까요?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "삭제")
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
