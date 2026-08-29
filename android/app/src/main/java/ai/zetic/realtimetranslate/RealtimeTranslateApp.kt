package ai.zetic.realtimetranslate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface UiAction {
    data object RequestPermission : UiAction
    data class SelectInput(val language: SpeechLanguage) : UiAction
    data class SelectOutput(val language: TranslationLanguage) : UiAction
    data object Start : UiAction
    data object Stop : UiAction
    data object Retry : UiAction
    data object NewSession : UiAction
}

fun UiAction.toSessionAction(context: android.content.Context): SessionAction = when (this) {
    UiAction.RequestPermission -> SessionAction.Retry
    is UiAction.SelectInput -> SessionAction.InputLanguageChanged(language)
    is UiAction.SelectOutput -> SessionAction.OutputLanguageChanged(language)
    UiAction.Start -> SessionAction.Start(context)
    UiAction.Stop -> SessionAction.Stop
    UiAction.Retry -> SessionAction.Retry
    UiAction.NewSession -> SessionAction.NewSession
}

@Composable
fun RealtimeTranslateApp(
    state: SessionUiState,
    onAction: (UiAction) -> Unit,
    onOpenAppSettings: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialThemeColor.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Realtime Translate", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        StatusHeader(state.phase)
        when (state.phase) {
            SessionPhase.PermissionRequired, SessionPhase.Ready -> SetupPanel(state, onAction, onOpenAppSettings)
            SessionPhase.Error -> ErrorPanel(state.errorMessage.orEmpty(), onAction)
            SessionPhase.Recording, SessionPhase.Processing, SessionPhase.Finished -> ConversationPanel(state, onAction)
        }
    }
}

private val MaterialThemeColor
    @Composable get() = androidx.compose.material3.MaterialTheme.colorScheme

@Composable
private fun StatusHeader(phase: SessionPhase) {
    val label = when (phase) {
        SessionPhase.PermissionRequired -> "마이크 권한 필요"
        SessionPhase.Ready -> "시작 준비됨"
        SessionPhase.Recording -> "녹음 중"
        SessionPhase.Processing -> "남은 발화를 처리 중"
        SessionPhase.Finished -> "세션 완료"
        SessionPhase.Error -> "오류 발생"
    }
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceMuted), modifier = Modifier.fillMaxWidth().semantics { contentDescription = "세션 상태: $label" }) {
        Text(label, modifier = Modifier.padding(12.dp), color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun SetupPanel(state: SessionUiState, onAction: (UiAction) -> Unit, onOpenAppSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LanguagePicker("발화 언어", state.inputLanguage.displayName, SpeechLanguage.entries.toList().map { it.displayName to UiAction.SelectInput(it) }, onAction)
        LanguagePicker("번역 언어", state.outputLanguage.displayName, HyMt2Languages.all.map { it.displayName to UiAction.SelectOutput(it) }, onAction)
        if (state.phase == SessionPhase.PermissionRequired) {
            if (state.permissionPermanentlyDenied) {
                Button(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "앱 설정 열기" }) {
                    Text("앱 설정 열기")
                }
            } else {
                Button(onClick = { onAction(UiAction.RequestPermission) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "마이크 권한 요청" }) {
                    Text("마이크 권한 허용")
                }
            }
        } else {
            Button(onClick = { onAction(UiAction.Start) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "번역 세션 시작" }) {
                Text("세션 시작")
            }
        }
    }
}

@Composable
private fun LanguagePicker(label: String, selected: String, options: List<Pair<String, UiAction>>, onAction: (UiAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label 선택: $selected" }) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(selected)
                Text("변경")
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            options.forEach { (name, action) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onAction(action) })
            }
        }
    }
}

@Composable
private fun ConversationPanel(state: SessionUiState, onAction: (UiAction) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        items(state.conversations, key = { it.id }) { MessageBubble(it) }
        if (state.conversations.isEmpty()) item { Text("대화를 기다리고 있습니다.", color = TextSecondary) }
    }
    if (state.phase == SessionPhase.Recording) {
        Button(onClick = { onAction(UiAction.Stop) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "녹음 종료" }) { Text("녹음 종료") }
    } else {
        if (state.phase == SessionPhase.Finished) {
            Button(onClick = { onAction(UiAction.NewSession) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "새 세션 시작" }) {
                Text("새 세션 시작")
            }
        } else {
            Text("처리가 끝나면 확정된 대화가 여기에 남습니다.", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MessageBubble(item: ConversationItem) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceMuted),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${item.speaker ?: "처리 중"} 발화" },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.speaker ?: "처리 중", color = TextSecondary, fontSize = 12.sp)
            Text(item.transcript, fontSize = 16.sp)
            item.translation?.let {
                HorizontalDivider()
                Text(it, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Text(if (item.isFinal) "확정" else "처리 중", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ErrorPanel(message: String, onAction: (UiAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, color = Error)
        Button(onClick = { onAction(UiAction.Retry) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "다시 시도" }) { Text("다시 시도") }
    }
}
