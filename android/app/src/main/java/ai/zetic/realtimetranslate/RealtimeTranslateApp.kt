package ai.zetic.realtimetranslate

import android.content.Context
import android.content.res.Resources
import com.zeticai.mlange.core.background.BackgroundDownloadState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.max

sealed interface UiAction {
    data object RequestPermission : UiAction
    data object RefreshSpeechLanguages : UiAction
    data class SelectInput(val speaker: Speaker, val language: SpeechLanguage) : UiAction
    data class RequestSpeechModelDownload(val language: SpeechLanguage.Installed) : UiAction
    data class SelectReading(val speaker: Speaker, val language: TranslationLanguage) : UiAction
    data object ScheduleModelDownload : UiAction
    data object RemoveDownloadedModel : UiAction
    data object StartConversation : UiAction
    data object EndSession : UiAction
    data object CancelModelPreparation : UiAction
    data object ClearConversation : UiAction
    data class PttPress(val speaker: Speaker) : UiAction
    data class PttRelease(val speaker: Speaker) : UiAction
    data class TogglePtt(val speaker: Speaker) : UiAction
    data class SubmitTyped(val text: String, val speaker: Speaker) : UiAction
    data class RetryTranslation(val id: String) : UiAction
    data object Retry : UiAction
}

fun UiAction.toSessionAction(context: Context): SessionAction = when (this) {
    UiAction.RequestPermission -> SessionAction.Retry
    UiAction.RefreshSpeechLanguages -> SessionAction.RefreshSpeechLanguages(context)
    is UiAction.SelectInput -> SessionAction.InputLanguageChanged(speaker, language)
    is UiAction.RequestSpeechModelDownload -> SessionAction.RequestSpeechModelDownload(context, language)
    is UiAction.SelectReading -> SessionAction.ReadingLanguageChanged(speaker, language)
    UiAction.ScheduleModelDownload -> SessionAction.ScheduleBackgroundDownload(context)
    UiAction.RemoveDownloadedModel -> SessionAction.RemoveDownloadedModel(context)
    UiAction.StartConversation -> SessionAction.StartConversation(context)
    UiAction.EndSession -> SessionAction.EndSession
    UiAction.CancelModelPreparation -> SessionAction.CancelModelPreparation
    UiAction.ClearConversation -> SessionAction.ClearConversation
    is UiAction.PttPress -> SessionAction.PttPress(context, speaker)
    is UiAction.PttRelease -> SessionAction.PttRelease(speaker)
    is UiAction.TogglePtt -> SessionAction.TogglePtt(context, speaker)
    is UiAction.SubmitTyped -> SessionAction.SubmitTyped(text, speaker)
    is UiAction.RetryTranslation -> SessionAction.RetryTranslation(id)
    UiAction.Retry -> SessionAction.Retry
}

fun statusLabel(state: SessionUiState): UiText = when {
    state.backgroundDownload?.isActive == true -> UiText.res(R.string.status_model_download_in_progress)
    else -> when (state.phase) {
    SessionPhase.PermissionRequired -> UiText.res(R.string.status_permission_required)
    SessionPhase.LoadingModel -> UiText.res(R.string.status_preparing_model)
    SessionPhase.ModelLoadFailed -> UiText.res(R.string.status_model_unavailable)
    SessionPhase.Ready ->
        if (state.conversationStarted) UiText.res(R.string.status_conversation_ready) else UiText.res(R.string.status_ready_to_start)
    SessionPhase.ListeningA -> UiText.res(R.string.status_listening, Speaker.A.label)
    SessionPhase.ListeningB -> UiText.res(R.string.status_listening, Speaker.B.label)
    SessionPhase.FinalizingA -> UiText.res(R.string.status_finalizing, Speaker.A.label)
    SessionPhase.FinalizingB -> UiText.res(R.string.status_finalizing, Speaker.B.label)
    // The status names who the translation is *for*, which is the other speaker.
    SessionPhase.TranslatingA -> UiText.res(R.string.status_translating, Speaker.B.label)
    SessionPhase.TranslatingB -> UiText.res(R.string.status_translating, Speaker.A.label)
    SessionPhase.Error -> UiText.res(R.string.status_error)
    }
}

/**
 * One screen holds everything: title and status, a per-speaker language bar, an inline session
 * banner, the chat transcript, and the A/B push-to-talk controls.
 */
@Composable
fun RealtimeTranslateApp(
    state: SessionUiState,
    onAction: (UiAction) -> Unit,
    onOpenAppSettings: () -> Unit = {},
    onOpenVoiceInputSettings: () -> Unit = {},
    onOpenSettingsDrawer: () -> Unit = {},
    onCopyBubble: (ConversationItem) -> Unit = {},
    copyToast: ToastState? = null,
    onReplayBubble: (ConversationItem) -> Unit = {},
) {
    var typedInput by remember { mutableStateOf<TypedInputDraft?>(null) }
    var lastTypedSpeaker by remember { mutableStateOf(Speaker.A) }
    Column(
        Modifier.fillMaxSize().background(Surface).windowInsetsPadding(WindowInsets.safeContent),
    ) {
        Header(state, onOpenSettingsDrawer)
        LanguageBar(state, onAction, onOpenVoiceInputSettings)
        SessionBanner(state, onAction, onOpenAppSettings)
        // The copy confirmation is anchored to the bottom of the transcript rather than the bottom
        // of the screen, so it never lands on top of the push-to-talk row or the session action.
        Box(Modifier.weight(1f)) {
            ConversationList(state, Modifier.fillMaxSize(), onCopyBubble, onReplayBubble, onAction)
            copyToast?.let { ToastHost(it, Modifier.align(Alignment.BottomCenter)) }
        }
        BottomBar(state, onAction) { typedInput = TypedInputDraft(speaker = lastTypedSpeaker) }
    }
    typedInput?.let { draft ->
        TypedInputSheet(
            draft = draft,
            state = state,
            onChange = {
                typedInput = it
                lastTypedSpeaker = it.speaker
            },
            onDismiss = { typedInput = null },
            onSend = { text, speaker ->
                lastTypedSpeaker = speaker
                onAction(UiAction.SubmitTyped(text, speaker))
                typedInput = null
            },
        )
    }
}

@Composable private fun Header(state: SessionUiState, onOpenSettingsDrawer: () -> Unit) {
    val status = statusLabel(state).text()
    val statusAccessibility = stringResource(R.string.status_accessibility, status)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            Image(
                painterResource(R.drawable.zetic_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(16.dp).align(Alignment.CenterStart),
            )
            Text(
                FirstRunCopy.PRODUCT_NAME,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Center),
            )
            val settingsLabel = stringResource(R.string.settings_title)
            IconButton(
                onClick = onOpenSettingsDrawer,
                modifier = Modifier.size(48.dp).align(Alignment.CenterEnd).semantics {
                    contentDescription = settingsLabel
                    role = Role.Button
                },
            ) {
                Icon(painterResource(R.drawable.ic_menu), null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            status,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.semantics { contentDescription = statusAccessibility },
        )
        val pendingSpeechPack = state.speechLanguages.filterIsInstance<SpeechLanguage.Installed>()
            .any { it.onDeviceStatus == SpeechLanguage.OnDeviceStatus.DownloadPending }
        if (state.speechLanguageCatalogLoading && !pendingSpeechPack) {
            Text(stringResource(R.string.speech_catalog_loading), color = TextSecondary, fontSize = 12.sp)
        }
        state.speechLanguageCatalogMessage?.let { Text(it.text(), color = TextSecondary, fontSize = 12.sp) }
    }
    HorizontalDivider(color = DividerLine)
}

/** Language chips can be changed at any time except while an utterance is in flight. */
private fun canEditLanguages(state: SessionUiState): Boolean =
    state.activeSpeaker() == null && state.phase != SessionPhase.LoadingModel

/**
 * Chips render the short form; the menu entries keep the full display name. `Automatic` loses its
 * parenthetical here rather than gaining a second catalog entry, so the two can never drift.
 */
@Composable private fun shortLanguageName(language: SpeechLanguage): String = when (language) {
    SpeechLanguage.Automatic -> stringResource(R.string.speech_language_automatic).substringBefore(" (")
    is SpeechLanguage.Installed -> language.name
}

/** One chip per speaker, mirroring the side their chat bubbles appear on. */
@Composable private fun LanguageBar(
    state: SessionUiState,
    onAction: (UiAction) -> Unit,
    onOpenVoiceInputSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeakerLanguageChip(Speaker.A, state, onAction, onOpenVoiceInputSettings, Modifier.weight(1f, fill = false))
        SpeakerLanguageChip(Speaker.B, state, onAction, onOpenVoiceInputSettings, Modifier.weight(1f, fill = false))
    }
    HorizontalDivider(color = DividerLine)
}

@Composable private fun SpeakerLanguageChip(
    speaker: Speaker,
    state: SessionUiState,
    onAction: (UiAction) -> Unit,
    onOpenVoiceInputSettings: () -> Unit,
    modifier: Modifier,
) {
    val settings = state.settingsFor(speaker)
    val enabled = canEditLanguages(state)
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: Locale.getDefault()
    val deviceLanguageCode = Resources.getSystem().configuration.locales[0]?.language
    val reading = settings.readingLanguage.localizedName(locale)
    val speaking = shortLanguageName(settings.inputLanguage)
    val chipAccessibility = stringResource(R.string.language_chip_accessibility, speaker.label, reading, speaking)
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            shape = ControlShape,
            border = BorderStroke(1.dp, if (enabled) speakerBorder(speaker) else DividerLine),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Surface, contentColor = TextPrimary),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.semantics { contentDescription = chipAccessibility },
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = if (enabled) speakerDeep(speaker) else TextSecondary,
                            fontWeight = FontWeight.Bold,
                        ),
                    ) { append("${speaker.label} ·") }
                    append(" ")
                    append(reading)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            MenuSectionHeader(stringResource(R.string.menu_spoken_language))
            val spokenLanguages = SpeechLanguageMenuOrdering.order(
                candidates = state.speechLanguages,
                pinned = listOf(settings.inputLanguage, state.settingsFor(speaker.other()).inputLanguage),
                deviceLanguageCode = deviceLanguageCode,
                automaticName = stringResource(R.string.speech_language_automatic),
                locale = locale,
            )
            val readyLanguages = spokenLanguages.filter { language ->
                (language as? SpeechLanguage.Installed)?.onDeviceStatus?.isSelectable != false
            }
            val pendingLanguages = spokenLanguages.filterIsInstance<SpeechLanguage.Installed>()
                .filter { it.onDeviceStatus == SpeechLanguage.OnDeviceStatus.DownloadPending }
            val downloadableLanguages = spokenLanguages.filterIsInstance<SpeechLanguage.Installed>()
                .filter { it.onDeviceStatus == SpeechLanguage.OnDeviceStatus.DownloadRequired }

            readyLanguages.forEach { language ->
                SpokenLanguageMenuItem(language) {
                    expanded = false
                    onAction(UiAction.SelectInput(speaker, language))
                }
            }
            if (pendingLanguages.isNotEmpty()) {
                MenuSectionHeader(stringResource(R.string.speech_model_group_downloading))
                pendingLanguages.forEach { SpokenLanguageMenuItem(it, enabled = false) {} }
            }
            if (downloadableLanguages.isNotEmpty()) {
                HorizontalDivider(color = DividerLine)
                MenuSectionHeader(stringResource(R.string.speech_model_group_available))
                downloadableLanguages.forEach { language ->
                    SpokenLanguageMenuItem(language) {
                        onAction(UiAction.RequestSpeechModelDownload(language))
                    }
                }
                Text(
                    stringResource(R.string.speech_model_download_guidance),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            state.speechModelDownloadError?.let { error ->
                HorizontalDivider(color = DividerLine)
                Text(
                    error.text(),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                TextButton(onClick = { expanded = false; onOpenVoiceInputSettings() }) {
                    Text(stringResource(R.string.speech_open_voice_input_settings))
                }
            }
            HorizontalDivider(color = DividerLine)
            MenuSectionHeader(stringResource(R.string.menu_reading_language))
            LanguageMenuOrdering.order(
                candidates = HyMt2Languages.all,
                pinned = listOf(settings.readingLanguage, state.settingsFor(speaker.other()).readingLanguage),
                deviceLanguageCode = deviceLanguageCode,
                locale = locale,
            ).forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.localizedName(locale)) },
                    onClick = { expanded = false; onAction(UiAction.SelectReading(speaker, language)) },
                )
            }
        }
    }
}

@Composable
private fun SpokenLanguageMenuItem(
    language: SpeechLanguage,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val status = (language as? SpeechLanguage.Installed)?.onDeviceStatus
    DropdownMenuItem(
        text = {
            Column {
                Text(language.displayName.text())
                speechLanguageStatusLabel(status)?.let {
                    Text(
                        it,
                        color = if (status == SpeechLanguage.OnDeviceStatus.DownloadRequired) Accent else TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun speechLanguageStatusLabel(status: SpeechLanguage.OnDeviceStatus?): String? = when (status) {
    SpeechLanguage.OnDeviceStatus.DownloadRequired -> stringResource(R.string.speech_model_status_download_required)
    SpeechLanguage.OnDeviceStatus.DownloadPending -> stringResource(R.string.speech_model_status_download_pending)
    SpeechLanguage.OnDeviceStatus.Ready, null -> null
}

@Composable private fun MenuSectionHeader(label: String) {
    Text(
        label,
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable private fun SessionBanner(state: SessionUiState, onAction: (UiAction) -> Unit, onOpenAppSettings: () -> Unit) {
    when {
        state.modelRemovalMessage != null -> Banner {
            Text(state.modelRemovalMessage.text(), color = TextPrimary, fontSize = 14.sp)
        }
        state.backgroundDownload?.isActive == true -> Banner {
            val progress = state.backgroundDownload.progress
            if (progress == null) {
                Text(stringResource(R.string.status_model_download_in_progress), color = TextPrimary, fontSize = 14.sp)
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = DividerLine,
                )
            } else {
                Text(
                    stringResource(R.string.banner_model_download_in_progress, (progress * 100).toInt()),
                    color = TextPrimary,
                    fontSize = 14.sp,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = DividerLine,
                )
            }
            Text(stringResource(R.string.banner_model_download_wait), color = TextSecondary, fontSize = 12.sp)
            val cancel = stringResource(R.string.session_cancel)
            BannerAction(cancel, cancel) { onAction(UiAction.CancelModelPreparation) }
        }
        state.backgroundDownload?.state in setOf(BackgroundDownloadState.FAILED, BackgroundDownloadState.STOPPED) -> Banner {
            Text(
                state.backgroundDownload?.errorMessage ?: stringResource(R.string.banner_model_download_failed),
                color = Error,
                fontSize = 14.sp,
            )
            val retry = stringResource(R.string.banner_retry_model_download)
            BannerAction(retry, retry) { onAction(UiAction.Retry) }
        }
        else -> when (state.phase) {
        SessionPhase.PermissionRequired -> Banner {
            Text(stringResource(R.string.banner_permission_body), color = TextPrimary, fontSize = 14.sp)
            if (state.permissionPermanentlyDenied) {
                val open = stringResource(R.string.banner_open_app_settings)
                BannerAction(open, open, onOpenAppSettings)
            } else {
                BannerAction(
                    stringResource(R.string.banner_allow_microphone),
                    stringResource(R.string.banner_request_permission_accessibility),
                ) { onAction(UiAction.RequestPermission) }
            }
        }
        SessionPhase.LoadingModel -> Banner {
            val progress = state.modelLoadProgress
            if (progress == null) {
                Text(stringResource(R.string.banner_loading_model), color = TextPrimary, fontSize = 14.sp)
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = DividerLine,
                )
            } else {
                Text(
                    stringResource(R.string.banner_model_download_in_progress, (progress * 100).toInt()),
                    color = TextPrimary,
                    fontSize = 14.sp,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    trackColor = DividerLine,
                )
            }
            Text(stringResource(R.string.banner_controls_unlock), color = TextSecondary, fontSize = 12.sp)
            val cancel = stringResource(R.string.session_cancel)
            BannerAction(cancel, cancel) { onAction(UiAction.CancelModelPreparation) }
        }
        SessionPhase.ModelLoadFailed -> Banner {
            Text(
                state.errorMessage?.text() ?: stringResource(R.string.error_model_load_failed),
                color = Error,
                fontSize = 14.sp,
            )
            val retry = stringResource(R.string.banner_retry_model_load)
            BannerAction(retry, retry) { onAction(UiAction.Retry) }
        }
        SessionPhase.Error -> Banner {
            Text(state.errorMessage?.text().orEmpty(), color = Error, fontSize = 14.sp)
            val tryAgain = stringResource(R.string.banner_try_again)
            BannerAction(tryAgain, tryAgain) { onAction(UiAction.Retry) }
        }
        else -> state.notice?.let { notice -> Banner {
            Text(notice.text(), color = TextSecondary, fontSize = 14.sp)
        } }
        }
    }
}

@Composable private fun Banner(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(SurfaceSubtle).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
    HorizontalDivider(color = DividerLine)
}

@Composable private fun BannerAction(label: String, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = ControlShape,
        border = BorderStroke(1.dp, DividerLine),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Surface, contentColor = TextPrimary),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
    ) { Text(label, fontSize = 14.sp) }
}

@Composable private fun ConversationList(
    state: SessionUiState,
    modifier: Modifier,
    onCopyBubble: (ConversationItem) -> Unit,
    onReplayBubble: (ConversationItem) -> Unit,
    onAction: (UiAction) -> Unit,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val touchExplorationEnabled = rememberTouchExplorationEnabled()
    val scope = rememberCoroutineScope()
    var follow by remember { mutableStateOf(ConversationFollow()) }
    var previousState by remember { mutableStateOf(state) }
    var programmaticScroll by remember { mutableStateOf(false) }

    suspend fun scrollToLatest() {
        if (state.conversations.isEmpty()) return
        programmaticScroll = true
        try {
            listState.animateScrollToItem(state.conversations.size)
        } finally {
            programmaticScroll = false
        }
    }

    LaunchedEffect(state.conversations) {
        val (next, effect) = follow.contentChanged(
            isEmpty = state.conversations.isEmpty(),
            isTouchExplorationEnabled = touchExplorationEnabled,
        )
        follow = next
        if (effect == ConversationFollow.Effect.ScrollToLatest) scrollToLatest()
    }
    LaunchedEffect(state.phase, state.conversationStarted) {
        if (ConversationSnapMoment.shouldSnap(previousState, state)) {
            follow = follow.snapToLatest().first
            scrollToLatest()
        }
        previousState = state
    }
    LaunchedEffect(listState, density) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()
            val distance = when {
                layout.totalItemsCount == 0 -> 0f
                last == null || last.index < layout.totalItemsCount - 1 -> Float.MAX_VALUE
                else -> max(0, last.offset + last.size - layout.viewportEndOffset).toFloat()
            }
            Triple(listState.isScrollInProgress, programmaticScroll, distance / density.density)
        }.collect { (scrolling, programmatic, distanceDp) ->
            if (scrolling && !programmatic) follow = follow.observeDistanceFromBottom(distanceDp)
        }
    }

    Box(modifier.fillMaxWidth()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            if (state.conversations.isEmpty() && state.phase !in setOf(
                    SessionPhase.PermissionRequired,
                    SessionPhase.LoadingModel,
                    SessionPhase.ModelLoadFailed,
                    SessionPhase.Error,
                )
            ) {
                item {
                    val hint = stringResource(
                        if (state.conversationStarted) R.string.transcript_empty_live else R.string.transcript_empty_idle,
                    )
                    Text(hint, color = TextSecondary, fontSize = 14.sp)
                }
            }
            items(state.conversations, key = { it.id }) { item ->
                MessageBubble(
                    item,
                    onCopyBubble,
                    state.isRecognizerLive,
                    state.phase == SessionPhase.Ready,
                    onReplayBubble,
                    onAction,
                )
            }
            if (state.conversations.isNotEmpty()) {
                item(key = CONVERSATION_BOTTOM_TAG) {
                    Spacer(Modifier.height(1.dp).testTag(CONVERSATION_BOTTOM_TAG))
                }
            }
        }
        if (follow.showsJumpControl) {
            val label = stringResource(R.string.jump_to_latest)
            IconButton(
                onClick = {
                    follow = follow.snapToLatest().first
                    scope.launch { scrollToLatest() }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .border(1.dp, DividerLine, CircleShape)
                    .semantics { contentDescription = label },
            ) {
                Icon(painterResource(R.drawable.ic_chevron_down), null, tint = TextPrimary)
            }
        }
    }
}

internal const val CONVERSATION_BOTTOM_TAG = "conversation-bottom"

@Composable private fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
    }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        if (manager == null) return@DisposableEffect onDispose { }
        val listener = android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener {
            enabled = it
        }
        manager.addTouchExplorationStateChangeListener(listener)
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

/**
 * A named menu action rather than a bare long press that copies silently: the transcript scrolls,
 * so a bare gesture fires on a slow drag, and the menu names the action before it happens and
 * exposes it to the accessibility service. A bubble with nothing to copy offers no action at all.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun MessageBubble(
    item: ConversationItem,
    onCopyBubble: (ConversationItem) -> Unit,
    isRecognizerLive: Boolean,
    canRetry: Boolean,
    onReplayBubble: (ConversationItem) -> Unit,
    onAction: (UiAction) -> Unit,
) {
    val isA = item.speaker == Speaker.A
    var menuExpanded by remember { mutableStateOf(false) }
    val copyable = item.copyableText != null
    val copyAction = stringResource(R.string.bubble_copy_action)
    val bubbleAccessibility = stringResource(R.string.bubble_accessibility, item.speaker.label)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val bubbleMaxWidth = (maxWidth - 64.dp).coerceAtLeast(1.dp)
        Column(
            Modifier
                .align(if (isA) Alignment.CenterStart else Alignment.CenterEnd)
                .widthIn(max = bubbleMaxWidth)
                .clip(MessageShape)
                .background(speakerTint(item.speaker))
                .combinedClickable(
                    enabled = copyable,
                    onClick = {},
                    onLongClick = { menuExpanded = true },
                    onLongClickLabel = copyAction,
                )
                .padding(12.dp)
                .semantics { contentDescription = bubbleAccessibility },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(copyAction) },
                    onClick = { menuExpanded = false; onCopyBubble(item) },
                )
            }
            Text(
                stringResource(R.string.bubble_speaker_heading, item.speaker.label),
                color = speakerDeep(item.speaker),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                item.transcript.ifBlank { stringResource(R.string.bubble_listening) },
                fontSize = 16.sp,
                color = if (item.transcript.isBlank()) TextSecondary else TextPrimary,
            )
            Text(
                stringResource(
                    R.string.bubble_destination,
                    item.speaker.other().label,
                    item.targetLanguage.localizedName(LocalConfiguration.current.locales[0] ?: Locale.getDefault()),
                ),
                color = TextSecondary,
                fontSize = 12.sp,
            )
            // The replay control sits in the bottom trailing corner of the translation region, as a
            // separate element from the bubble, so the bubble's own label and its copy long press
            // are unchanged.
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    Modifier.widthIn(max = (bubbleMaxWidth - 36.dp).coerceAtLeast(1.dp)),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when {
                        item.translation != null ->
                            Text(item.translation, fontSize = 16.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        item.translationError != null -> {
                            Text(item.translationError.text(), color = Error, fontSize = 12.sp)
                            if (!isRecognizerLive) {
                                TextButton(
                                    onClick = { onAction(UiAction.RetryTranslation(item.id)) },
                                    enabled = canRetry,
                                ) {
                                    Text(stringResource(R.string.retry_translation))
                                }
                            }
                        }
                        item.isFinal -> Text(stringResource(R.string.bubble_translation_pending), color = TextSecondary, fontSize = 12.sp)
                        else -> Text(stringResource(R.string.bubble_recognizing), color = TextSecondary, fontSize = 12.sp)
                    }
                    item.provisionalTranslation?.let {
                        Text(it, color = TextSecondary, fontSize = 14.sp)
                    }
                }
                if (ReplayControl.isPresent(item)) {
                    ReplayButton(
                        enabled = ReplayControl.isEnabled(item, isRecognizerLive),
                        onClick = { onReplayBubble(item) },
                    )
                }
            }
        }
    }
}

/** Absent, not disabled, on a bubble with nothing to play; disabled while nothing can be heard. */
@Composable private fun ReplayButton(enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.replay_label)
    val hint = stringResource(R.string.replay_hint)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(28.dp).semantics(mergeDescendants = true) {
            contentDescription = label
            // Compose has no hint slot, so the sentence that says what the tap will do rides on the
            // click label the accessibility service reads out with the action.
            onClick(label = hint) { onClick(); true }
            role = Role.Button
            if (!enabled) disabled()
        },
    ) {
        Icon(
            painterResource(R.drawable.ic_volume_up),
            contentDescription = null,
            tint = if (enabled) speakerDeep(Speaker.A) else TextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable private fun BottomBar(
    state: SessionUiState,
    onAction: (UiAction) -> Unit,
    onOpenTypedInput: () -> Unit,
) {
    HorizontalDivider(color = DividerLine)
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PttControl(Speaker.A, state, onAction, Modifier.weight(1f))
            PttControl(Speaker.B, state, onAction, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(bottomHint(state).text(), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            val typedLabel = stringResource(R.string.typed_input_action)
            IconButton(
                onClick = onOpenTypedInput,
                enabled = state.conversationStarted && state.phase == SessionPhase.Ready,
                modifier = Modifier.size(48.dp).semantics { contentDescription = typedLabel },
            ) {
                Icon(painterResource(R.drawable.ic_keyboard), null, tint = TextSecondary)
            }
        }
        SessionButton(state, onAction)
    }
}

private fun bottomHint(state: SessionUiState): UiText {
    val active = state.activeSpeaker()
    return when {
        state.backgroundDownload?.isActive == true -> UiText.res(R.string.hint_model_download_in_progress)
        state.phase == SessionPhase.PermissionRequired -> UiText.res(R.string.hint_grant_microphone)
        state.phase == SessionPhase.LoadingModel || state.phase == SessionPhase.ModelLoadFailed ->
            UiText.res(R.string.hint_model_not_ready)
        state.phase == SessionPhase.Error -> UiText.res(R.string.hint_resolve_error)
        !state.conversationStarted -> UiText.res(R.string.hint_start_conversation)
        active != null -> UiText.res(R.string.hint_other_speaker_active, active.other().label, active.label)
        else -> UiText.res(R.string.hint_push_to_talk)
    }
}

@Composable private fun PttControl(speaker: Speaker, state: SessionUiState, onAction: (UiAction) -> Unit, modifier: Modifier) {
    val active = state.activeSpeaker()
    val listening = state.phase == if (speaker == Speaker.A) SessionPhase.ListeningA else SessionPhase.ListeningB
    val enabled = state.conversationStarted && (state.phase == SessionPhase.Ready || listening)
    val actionLabel = stringResource(if (listening) R.string.ptt_stop else R.string.ptt_start, speaker.label)
    val blockedLabel = if (active != null) {
        stringResource(R.string.ptt_blocked_by_other, speaker.label, active.label)
    } else {
        stringResource(R.string.ptt_blocked_model_not_ready, speaker.label)
    }
    val container = when {
        listening -> speakerAccent(speaker)
        enabled -> Surface
        else -> SurfaceSubtle
    }
    val contentColor = when {
        listening -> Surface
        enabled -> TextPrimary
        else -> TextSecondary
    }
    val prefixColor = when {
        listening -> Surface
        enabled -> speakerDeep(speaker)
        else -> TextSecondary
    }
    val borderColor = when {
        listening -> speakerAccent(speaker)
        enabled -> speakerBorder(speaker)
        else -> DividerLine
    }
    val caption = stringResource(if (listening) R.string.ptt_caption_recording else R.string.ptt_caption_idle)
    val label = buildAnnotatedString {
        withStyle(SpanStyle(color = prefixColor, fontWeight = FontWeight.Bold)) { append(speaker.label) }
        append(caption)
    }
    Box(
        modifier
            .clip(ControlShape)
            .background(container)
            .border(1.dp, borderColor, ControlShape)
            .clickable(enabled = enabled) { onAction(UiAction.TogglePtt(speaker)) }
            .semantics(mergeDescendants = true) {
                contentDescription = if (enabled) actionLabel else blockedLabel
                if (!enabled) disabled()
            }
            .padding(vertical = 16.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable private fun SessionButton(state: SessionUiState, onAction: (UiAction) -> Unit) {
    val endLabel = stringResource(R.string.session_end)
    val startLabel = stringResource(R.string.session_start)
    val cancelLabel = stringResource(R.string.session_cancel)
    if (state.backgroundDownload?.isActive == true || state.phase == SessionPhase.LoadingModel) {
        OutlinedButton(
            onClick = { onAction(UiAction.CancelModelPreparation) },
            shape = ControlShape,
            border = BorderStroke(1.dp, DividerLine),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Surface, contentColor = TextPrimary),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = cancelLabel },
        ) { Text(cancelLabel, fontSize = 14.sp) }
    } else if (state.conversationStarted) {
        OutlinedButton(
            onClick = { onAction(UiAction.EndSession) },
            shape = ControlShape,
            border = BorderStroke(1.dp, DividerLine),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Surface, contentColor = TextPrimary),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = endLabel },
        ) { Text(endLabel, fontSize = 14.sp) }
    } else {
        Button(
            onClick = { onAction(UiAction.StartConversation) },
            enabled = state.phase == SessionPhase.Ready,
            shape = ControlShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Surface,
                disabledContainerColor = SurfaceSubtle,
                disabledContentColor = TextSecondary,
            ),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = startLabel },
        ) { Text(startLabel, fontSize = 14.sp) }
    }
}

private data class TypedInputDraft(
    val speaker: Speaker = Speaker.A,
    val text: String = "",
) {
    val trimmedText: String get() = text.trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TypedInputSheet(
    draft: TypedInputDraft,
    state: SessionUiState,
    onChange: (TypedInputDraft) -> Unit,
    onDismiss: () -> Unit,
    onSend: (String, Speaker) -> Unit,
) {
    val settings = state.settingsFor(draft.speaker)
    val counterpart = state.settingsFor(draft.speaker.other())
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.typed_input_title), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.typed_input_speaker), color = TextSecondary, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Speaker.entries.forEach { speaker ->
                    OutlinedButton(
                        onClick = { onChange(draft.copy(speaker = speaker)) },
                        border = BorderStroke(1.dp, if (draft.speaker == speaker) speakerAccent(speaker) else DividerLine),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (draft.speaker == speaker) speakerTint(speaker) else Surface,
                            contentColor = TextPrimary,
                        ),
                        modifier = Modifier.weight(1f),
                    ) { Text(speaker.label) }
                }
            }
            Text(
                stringResource(
                    R.string.typed_input_guidance,
                    draft.speaker.label,
                    settings.readingLanguage.localizedName(locale),
                    draft.speaker.other().label,
                    counterpart.readingLanguage.localizedName(locale),
                ),
                color = TextSecondary,
                fontSize = 12.sp,
            )
            OutlinedTextField(
                value = draft.text,
                onValueChange = { onChange(draft.copy(text = it)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).focusRequester(focusRequester),
                label = { Text(stringResource(R.string.typed_input_message)) },
                placeholder = {
                    Text(
                        stringResource(
                            R.string.typed_input_placeholder,
                            draft.speaker.label,
                            settings.readingLanguage.localizedName(locale),
                        ),
                    )
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.typed_input_cancel)) }
                TextButton(
                    onClick = { onSend(draft.trimmedText, draft.speaker) },
                    enabled = draft.trimmedText.isNotEmpty() && state.conversationStarted && state.phase == SessionPhase.Ready,
                ) { Text(stringResource(R.string.typed_input_send)) }
            }
        }
    }
}
