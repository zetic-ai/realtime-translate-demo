package ai.zetic.realtimetranslate

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface UiAction {
    data object RequestPermission : UiAction
    data class SelectReading(val speaker: Speaker, val language: TranslationLanguage) : UiAction
    data object StartConversation : UiAction
    data object EndSession : UiAction
    data class PttPress(val speaker: Speaker) : UiAction
    data class PttRelease(val speaker: Speaker) : UiAction
    data class TogglePtt(val speaker: Speaker) : UiAction
    data object Retry : UiAction
}

fun UiAction.toSessionAction(context: Context): SessionAction = when (this) {
    UiAction.RequestPermission -> SessionAction.Retry
    is UiAction.SelectReading -> SessionAction.ReadingLanguageChanged(speaker, language)
    UiAction.StartConversation -> SessionAction.StartConversation
    UiAction.EndSession -> SessionAction.EndSession
    is UiAction.PttPress -> SessionAction.PttPress(context, speaker)
    is UiAction.PttRelease -> SessionAction.PttRelease(speaker)
    is UiAction.TogglePtt -> SessionAction.TogglePtt(context, speaker)
    UiAction.Retry -> SessionAction.Retry
}

@Composable
fun RealtimeTranslateApp(state: SessionUiState, onAction: (UiAction) -> Unit, onOpenAppSettings: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().background(Surface).windowInsetsPadding(WindowInsets.safeContent).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Realtime Translate", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        StatusHeader(state)
        when {
            state.phase == SessionPhase.PermissionRequired -> PermissionPanel(state, onAction, onOpenAppSettings)
            state.phase == SessionPhase.Error -> ErrorPanel(state, onAction)
            !state.conversationStarted -> SetupPanel(state, onAction)
            else -> ConversationPanel(state, onAction)
        }
    }
}

@Composable private fun StatusHeader(state: SessionUiState) {
    val label = when (state.phase) {
        SessionPhase.PermissionRequired -> "Microphone permission required"
        SessionPhase.Ready -> if (state.conversationStarted) "Conversation ready" else "Conversation setup"
        SessionPhase.ListeningA -> "Speaker A is speaking"
        SessionPhase.ListeningB -> "Speaker B is speaking"
        SessionPhase.FinalizingA -> "Finalizing speaker A transcript"
        SessionPhase.FinalizingB -> "Finalizing speaker B transcript"
        SessionPhase.TranslatingA -> "Translating for speaker B"
        SessionPhase.TranslatingB -> "Translating for speaker A"
        SessionPhase.Error -> "An error occurred"
    }
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceMuted), modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Session status: $label" }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable private fun PermissionPanel(state: SessionUiState, onAction: (UiAction) -> Unit, onOpenAppSettings: () -> Unit) {
    if (state.permissionPermanentlyDenied) Button(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Open app settings" }) { Text("Open app settings") }
    else Button(onClick = { onAction(UiAction.RequestPermission) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Request microphone permission" }) { Text("Allow microphone") }
}

@Composable private fun SetupPanel(state: SessionUiState, onAction: (UiAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SpeakerSetup(Speaker.A, state, onAction)
        SpeakerSetup(Speaker.B, state, onAction)
        Button(onClick = { onAction(UiAction.StartConversation) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Start conversation" }) { Text("Start conversation") }
    }
}

@Composable private fun SpeakerSetup(speaker: Speaker, state: SessionUiState, onAction: (UiAction) -> Unit) {
    val settings = state.settingsFor(speaker)
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceMuted), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Speaker ${speaker.label}", fontWeight = FontWeight.Bold)
            Text("Recognition language", color = TextSecondary, fontSize = 12.sp)
            Text(
                settings.inputLanguage.displayName,
                modifier = Modifier.semantics { contentDescription = "Speaker ${speaker.label} recognition language: ${settings.inputLanguage.displayName}" },
            )
            LanguagePicker("Speaker ${speaker.label} translation language", settings.readingLanguage.displayName, HyMt2Languages.all.map { it.displayName to UiAction.SelectReading(speaker, it) }, onAction)
        }
    }
}

@Composable private fun LanguagePicker(label: String, selected: String, options: List<Pair<String, UiAction>>, onAction: (UiAction) -> Unit, enabledActions: Set<UiAction> = options.map { it.second }.toSet()) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label selector: $selected" }) { Text(selected, modifier = Modifier.fillMaxWidth()) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            options.forEach { (name, action) -> DropdownMenuItem(text = { Text(name) }, enabled = action in enabledActions, onClick = { expanded = false; onAction(action) }) }
        }
    }
}

@Composable private fun ConversationPanel(state: SessionUiState, onAction: (UiAction) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
        items(state.conversations, key = { it.id }) { MessageBubble(it) }
        if (state.conversations.isEmpty()) item { Text("Speaker A or B can begin speaking.", color = TextSecondary) }
    }
    val active = state.activeSpeaker()
    if (active != null) Text("Speaker ${active.other().label} cannot begin while speaker ${active.label} is active.", color = TextSecondary, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        PttControl(Speaker.A, state, onAction, Modifier)
        PttControl(Speaker.B, state, onAction, Modifier)
    }
    OutlinedButton(onClick = { onAction(UiAction.EndSession) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "End session" }) { Text("End session") }
}

@Composable private fun PttControl(speaker: Speaker, state: SessionUiState, onAction: (UiAction) -> Unit, modifier: Modifier) {
    val active = state.activeSpeaker()
    val listening = state.phase == if (speaker == Speaker.A) SessionPhase.ListeningA else SessionPhase.ListeningB
    val enabled = state.phase == SessionPhase.Ready || listening
    val actionLabel = if (listening) "Stop speaker ${speaker.label}" else "Start speaker ${speaker.label}"
    val color = if (speaker == Speaker.A) Primary else Secondary
    Card(colors = CardDefaults.cardColors(containerColor = if (enabled) color else SurfaceMuted), shape = RoundedCornerShape(20.dp), modifier = modifier
        .semantics(mergeDescendants = true) {
            contentDescription = if (enabled) actionLabel else "Speaker ${speaker.label} cannot start while speaker ${active?.label} is active"
            if (enabled) onClick { onAction(UiAction.TogglePtt(speaker)); true } else disabled()
        }
        .pointerInput(enabled, listening) {
            if (enabled) detectTapGestures(onPress = {
                onAction(UiAction.PttPress(speaker))
                tryAwaitRelease()
                onAction(UiAction.PttRelease(speaker))
            })
        }) {
        Text(if (listening) "Release to stop ${speaker.label}" else "Hold to talk as ${speaker.label}", Modifier.padding(16.dp), color = if (enabled) Surface else TextSecondary, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun MessageBubble(item: ConversationItem) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceMuted), modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Speaker ${item.speaker.label} utterance" }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${item.speaker.label} - ${item.sourceLanguage.displayName}", color = TextSecondary, fontSize = 12.sp)
            Text(item.transcript, fontSize = 16.sp)
            HorizontalDivider()
            Text("For ${item.targetLanguage.displayName}", color = TextSecondary, fontSize = 12.sp)
            when {
                item.translation != null -> Text(item.translation, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                item.translationError != null -> Text(item.translationError, color = Error, fontSize = 12.sp)
                item.isFinal -> Text("Waiting for translation", color = TextSecondary, fontSize = 12.sp)
                else -> Text("Recognizing speech", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable private fun ErrorPanel(state: SessionUiState, onAction: (UiAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.conversations.forEach { MessageBubble(it) }
        Text(state.errorMessage.orEmpty(), color = Error)
        Button(onClick = { onAction(UiAction.Retry) }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Try again" }) { Text("Try again") }
    }
}
