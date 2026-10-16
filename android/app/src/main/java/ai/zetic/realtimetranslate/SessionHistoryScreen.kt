package ai.zetic.realtimetranslate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import java.text.DateFormat
import java.util.Date

@Composable
fun SessionHistoryScreen(history: SessionHistory, sessionActive: Boolean, initialId: String? = null,
                         generate: (String) -> Unit, close: () -> Unit) {
    val state by history.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(initialId) }
    var query by remember { mutableStateOf("") }
    BackHandler { if (selected != null) selected = null else close() }
    val session = state.sessions.firstOrNull { it.id == selected }
    Column(Modifier.fillMaxSize().background(Surface).windowInsetsPadding(WindowInsets.safeContent)) {
        if (session == null) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.history_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = close) { Text(stringResource(if (sessionActive) R.string.history_return else R.string.history_new)) }
            }
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.history_search)) },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            if (state.storageFailed) {
                Text(stringResource(R.string.history_storage_error), color = Error, modifier = Modifier.padding(16.dp))
                TextButton(onClick = history::retryStorage) { Text(stringResource(R.string.history_retry)) }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                if (history.savedSessions.isEmpty()) item { Text(stringResource(R.string.history_empty), color = TextSecondary) }
                items(history.savedSessions.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }, key = { it.id }) { saved ->
                    Column(Modifier.fillMaxWidth().clickable { selected = saved.id }.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(saved.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(saved.startedAt)) + " · " + saved.durationText, fontSize = 12.sp)
                        Text(saved.turns.map { it.targetLanguage.uppercase() }.distinct().joinToString(" · "), fontSize = 12.sp)
                        Text(stringResource(summaryStatusResource(saved.summaryStatus)), fontSize = 12.sp, color = TextSecondary)
                    }
                    HorizontalDivider(color = DividerLine)
                }
            }
        } else {
            SavedSessionScreen(session, state, history, sessionActive, generate) { selected = null }
        }
    }
}

private fun summaryStatusResource(status: SummaryStatus) = when (status) {
    SummaryStatus.pending -> R.string.history_summary_pending
    SummaryStatus.generating -> R.string.history_generating
    SummaryStatus.ready -> R.string.history_summary_ready
    SummaryStatus.failed -> R.string.history_summary_failed
}

@Composable private fun SavedSessionScreen(session: SavedSession, state: HistoryState, history: SessionHistory,
    sessionActive: Boolean, generate: (String) -> Unit, back: () -> Unit) {
    var tab by remember(session.id) { mutableIntStateOf(0) }
    var focus by remember(session.id) { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var title by remember(session.id) { mutableStateOf(session.title) }
    var consentPrompt by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("relais-summary", 0) }
    var consent by remember { mutableStateOf(preferences.getBoolean("modelConsent.v1", false)) }
    LaunchedEffect(session.id) { if (consent && session.summaryStatus == SummaryStatus.pending && !sessionActive) generate(session.id) }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = back) { Text(stringResource(R.string.history_title)) }
        Text(stringResource(R.string.history_session), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Box {
            TextButton(onClick = { menu = true }) { Text(stringResource(R.string.history_options)) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.history_rename)) }, onClick = { menu = false; rename = true })
                DropdownMenuItem(text = { Text(stringResource(R.string.history_delete)) }, onClick = { menu = false; deleting = true })
            }
        }
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(session.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.startedAt)) + " · " + session.durationText, fontSize = 12.sp)
        Text(stringResource(R.string.history_turn_count, session.turns.size), fontSize = 12.sp, color = TextSecondary)
    }
    TabRow(selectedTabIndex = tab, containerColor = Surface, contentColor = TextPrimary) {
        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.history_summary)) })
        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.history_transcript)) })
    }
    val listState = rememberLazyListState()
    LaunchedEffect(tab, focus) {
        if (tab == 1) session.turns.indexOfFirst { it.id == focus }.takeIf { it >= 0 }?.let { listState.scrollToItem(it) }
    }
    if (tab == 1) {
        LazyColumn(state = listState, contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(session.turns, key = { _, turn -> turn.id }) { index, turn ->
                Column(Modifier.fillMaxWidth().padding(start = if (turn.speaker == "B") 16.dp else 0.dp,
                    end = if (turn.speaker == "A") 16.dp else 0.dp)
                    .clip(MessageShape).background(if (turn.speaker == "A") TintA else TintB)
                    .border(if (focus == turn.id) 2.dp else 0.dp, if (focus == turn.id) AccentB else DividerLine, MessageShape)
                    .padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${turn.speaker} · ${turn.targetLanguage.uppercase()} · ${index + 1}", fontSize = 11.sp, color = TextSecondary)
                    if (turn.translation != null) Text(turn.translation, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                    else Text(stringResource(R.string.history_translation_unavailable), fontSize = 12.sp)
                    HorizontalDivider(color = DividerLine)
                    Text(turn.original, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(stringResource(R.string.history_ai_notice), fontSize = 12.sp, color = TextSecondary) }
            val summary = session.summary
            if (session.summaryStatus == SummaryStatus.ready && summary != null) {
                val sections = listOf(R.string.history_essentials to summary.essentials, R.string.history_decisions to summary.decisions,
                    R.string.history_actions to summary.actions, R.string.history_questions to summary.questions)
                sections.forEach { (heading, points) ->
                    item { Text(stringResource(heading), fontWeight = FontWeight.Bold) }
                    if (points.isEmpty()) item { Text(stringResource(R.string.history_none_stated), fontSize = 14.sp, color = TextSecondary) }
                    items(points) { point ->
                        Column {
                            Text(point.text, fontSize = 16.sp, lineHeight = 24.sp)
                            Row {
                                point.sources.distinct().forEach { source ->
                                    TextButton(onClick = { focus = source; tab = 1 }) {
                                        Text("[${session.turns.indexOfFirst { it.id == source } + 1}]")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item { Text(stringResource(summaryStatusResource(session.summaryStatus)), fontWeight = FontWeight.Bold) }
                item { Text(stringResource(R.string.history_transcript_safe), fontSize = 14.sp) }
                if (session.summaryStatus == SummaryStatus.generating) {
                    item { CircularProgressIndicator(); TextButton(onClick = history::interrupt) { Text(stringResource(R.string.history_stop_summary)) } }
                } else {
                    item {
                        Button(onClick = { if (consent) generate(session.id) else consentPrompt = true }, enabled = !sessionActive && !state.storageFailed) {
                            Text(stringResource(if (session.summaryStatus == SummaryStatus.failed) R.string.history_retry else R.string.history_generate))
                        }
                        if (sessionActive) Text(stringResource(R.string.history_end_first), fontSize = 12.sp)
                    }
                }
            }
            item { TextButton(onClick = { tab = 1 }) { Text(stringResource(R.string.history_read_transcript)) } }
            if (state.storageFailed) item { Text(stringResource(R.string.history_storage_error), color = Error) }
        }
    }
    if (rename) AlertDialog(onDismissRequest = { rename = false }, title = { Text(stringResource(R.string.history_rename)) },
        text = { OutlinedTextField(title, { title = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { history.rename(session.id, title); rename = false }) { Text(stringResource(R.string.history_save)) } },
        dismissButton = { TextButton(onClick = { rename = false }) { Text(stringResource(R.string.history_cancel)) } })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(stringResource(R.string.history_delete_question)) },
        text = { Text(stringResource(R.string.history_delete_detail)) },
        confirmButton = { TextButton(onClick = { history.delete(session.id); deleting = false; if (!history.state.value.storageFailed) back() }) { Text(stringResource(R.string.history_delete), color = Error) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.history_cancel)) } })
    if (consentPrompt) AlertDialog(onDismissRequest = { consentPrompt = false }, title = { Text(stringResource(R.string.history_model_title)) },
        text = { Text(stringResource(R.string.history_model_detail)) },
        confirmButton = { TextButton(onClick = { preferences.edit().putBoolean("modelConsent.v1", true).apply(); consent = true; consentPrompt = false; generate(session.id) }) { Text(stringResource(R.string.history_download)) } },
        dismissButton = { TextButton(onClick = { consentPrompt = false }) { Text(stringResource(R.string.history_later)) } })
}
