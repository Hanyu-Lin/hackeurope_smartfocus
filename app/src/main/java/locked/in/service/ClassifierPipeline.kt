package locked.`in`.service

import android.util.Log
import locked.`in`.data.local.entity.NotificationRecordEntity
import locked.`in`.data.repository.FocusModeRepository
import locked.`in`.data.repository.NotificationRecordRepository
import locked.`in`.domain.engine.RuleEngine
import locked.`in`.domain.engine.RuleResult
import locked.`in`.domain.model.NotificationOutcome
import locked.`in`.domain.model.ParsedNotification
import locked.`in`.domain.model.RuleAction
import locked.`in`.domain.model.RuleEffect
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class PipelineResult {
    data object PassThrough : PipelineResult()
    data class Allow(val action: RuleAction) : PipelineResult()
    data object Suppress : PipelineResult()
    data class Bundle(val bundleId: String) : PipelineResult()
}

@Singleton
class ClassifierPipeline @Inject constructor(
    private val focusModeRepository: FocusModeRepository,
    private val notificationRecordRepository: NotificationRecordRepository
) {

    companion object {
        private const val TAG = "ClassifierPipeline"
    }

    suspend fun process(parsed: ParsedNotification): PipelineResult {
        val activeModes = focusModeRepository.getActive()

        if (activeModes.isEmpty()) {
            Log.d(TAG, "No active focus mode — pass through: ${parsed.appLabel} / ${parsed.title}")
            persist(parsed, NotificationOutcome.ALLOWED, null)
            return PipelineResult.PassThrough
        }

        // Merge rules from all active modes (schedule window is not applied here; controller handles activation)
        val mergedRules = activeModes.flatMap { it.rules }
        Log.d(TAG, "Active modes: ${activeModes.joinToString { it.name }} with ${mergedRules.size} merged rules")
        Log.d(TAG, "Evaluating: pkg=${parsed.packageName}, category=${parsed.category}, title=${parsed.title}, text=${parsed.text.take(50)}")

        val ruleResult = RuleEngine.evaluate(parsed, mergedRules)

        return when (ruleResult) {
            is RuleResult.Match -> {
                val rule = ruleResult.rule
                Log.d(TAG, "Rule matched: type=${rule.type}, value=${rule.value}, effect=${rule.effect}")
                when (rule.effect) {
                    RuleEffect.SUPPRESS -> {
                        persist(parsed, NotificationOutcome.SUPPRESSED, rule.id)
                        PipelineResult.Suppress
                    }
                    RuleEffect.ALLOW -> {
                        persist(parsed, NotificationOutcome.ALLOWED, rule.id)
                        PipelineResult.Allow(rule.action)
                    }
                }
            }
            is RuleResult.NoMatch -> {
                Log.d(TAG, "No rule matched — pass through: ${parsed.appLabel} / ${parsed.title}")
                persist(parsed, NotificationOutcome.ALLOWED, null)
                PipelineResult.PassThrough
            }
        }
    }

    private suspend fun persist(
        parsed: ParsedNotification,
        outcome: NotificationOutcome,
        appliedRuleId: String?
    ) {
        try {
            notificationRecordRepository.insert(
                NotificationRecordEntity(
                    id = UUID.randomUUID().toString(),
                    packageName = parsed.packageName,
                    appLabel = parsed.appLabel,
                    category = parsed.category,
                    title = parsed.title,
                    text = parsed.text,
                    rawPrompt = parsed.rawPrompt,
                    timestamp = parsed.timestamp,
                    isContact = parsed.sender != null,
                    outcome = outcome.name,
                    appliedRuleId = appliedRuleId,
                    priorityScore = null,
                    bundleId = null
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist notification record", e)
        }
    }
}
