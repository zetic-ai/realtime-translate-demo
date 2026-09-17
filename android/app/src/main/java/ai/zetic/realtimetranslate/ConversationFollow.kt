package ai.zetic.realtimetranslate

/** Pure follow/reading decision. The Compose list owns only the scrolling effect. */
data class ConversationFollow(
    val isFollowing: Boolean = true,
    val hasUnseenContent: Boolean = false,
) {
    val showsJumpControl: Boolean get() = !isFollowing && hasUnseenContent

    fun observeDistanceFromBottom(distanceDp: Float): ConversationFollow =
        if (distanceDp <= NEAR_BOTTOM_THRESHOLD_DP) ConversationFollow() else copy(isFollowing = false)

    fun contentChanged(
        isEmpty: Boolean,
        isTouchExplorationEnabled: Boolean = false,
    ): Pair<ConversationFollow, Effect> = when {
        isEmpty -> ConversationFollow() to Effect.None
        isFollowing && isTouchExplorationEnabled -> this to Effect.None
        isFollowing -> this to Effect.ScrollToLatest
        else -> copy(hasUnseenContent = true) to Effect.None
    }

    fun snapToLatest(): Pair<ConversationFollow, Effect> = ConversationFollow() to Effect.ScrollToLatest

    enum class Effect { None, ScrollToLatest }

    companion object { const val NEAR_BOTTOM_THRESHOLD_DP = 120f }
}

object ConversationSnapMoment {
    fun shouldSnap(previous: SessionUiState, next: SessionUiState): Boolean = when {
        previous == next -> false
        next.phase in setOf(SessionPhase.ListeningA, SessionPhase.ListeningB) -> true
        previous.phase == SessionPhase.LoadingModel && next.phase == SessionPhase.Ready && next.conversationStarted -> true
        previous.conversationStarted && !next.conversationStarted -> true
        else -> false
    }
}
