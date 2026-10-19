package ai.zetic.realtimetranslate

import androidx.compose.foundation.background
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
                         close: () -> Unit) {
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
                    }
                    HorizontalDivider(color = DividerLine)
                }
            }
        } else {
            SavedSessionScreen(session, state, history) { selected = null }
        }
    }
}

@Composable private fun SavedSessionScreen(session: SavedSession, state: HistoryState, history: SessionHistory,
    back: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var title by remember(session.id) { mutableStateOf(session.title) }
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
    Text(stringResource(R.string.history_transcript), fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
    if (state.storageFailed) {
        Text(stringResource(R.string.history_storage_error), color = Error, modifier = Modifier.padding(horizontal = 16.dp))
        TextButton(onClick = history::retryStorage) { Text(stringResource(R.string.history_retry)) }
    }
    val listState = rememberLazyListState()
    LazyColumn(state = listState, contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(session.turns, key = { _, turn -> turn.id }) { index, turn ->
            Column(Modifier.fillMaxWidth().padding(start = if (turn.speaker == "B") 16.dp else 0.dp,
                end = if (turn.speaker == "A") 16.dp else 0.dp)
                .clip(MessageShape).background(if (turn.speaker == "A") TintA else TintB)
                .padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${turn.speaker} · ${turn.targetLanguage.uppercase()} · ${index + 1}", fontSize = 11.sp, color = TextSecondary)
                if (turn.translation != null) Text(turn.translation, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                else Text(stringResource(R.string.history_translation_unavailable), fontSize = 12.sp)
                HorizontalDivider(color = DividerLine)
                Text(turn.original, fontSize = 13.sp, lineHeight = 19.sp)
            }
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
}
