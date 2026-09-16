package ai.zetic.realtimetranslate

data class PartialTranslationPass(val itemId: String, val revision: Long, val sourceText: String)

object LivePartialTranslationGuard {
    fun shouldApply(
        pass: PartialTranslationPass,
        liveItemId: String?,
        item: ConversationItem?,
        appliedRevision: Long,
    ): Boolean =
        pass.itemId == liveItemId &&
            item != null &&
            item.translation == null &&
            item.transcript == pass.sourceText &&
            pass.revision > appliedRevision &&
            pass.sourceText.isNotBlank()
}
