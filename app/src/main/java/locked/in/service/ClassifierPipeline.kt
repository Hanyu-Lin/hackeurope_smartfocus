package locked.`in`.service

import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import locked.`in`.data.local.entity.BundleMapEntryEntity
import locked.`in`.data.local.entity.NotificationRecordEntity
import locked.`in`.data.repository.BundleRepository
import locked.`in`.data.repository.FocusModeRepository
import locked.`in`.data.repository.NotificationRecordRepository
import locked.`in`.domain.engine.BundleEngine
import locked.`in`.domain.engine.RuleEngine
import locked.`in`.domain.engine.RuleResult
import locked.`in`.domain.classifier.NotificationModel
import locked.`in`.domain.model.BundleDecision
import locked.`in`.domain.model.NotificationOutcome
import locked.`in`.domain.model.ParsedNotification
import locked.`in`.domain.model.FilterRule
import locked.`in`.domain.model.RuleAction
import locked.`in`.domain.model.RuleEffect
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Sentinel: first notification in bundle passed through (system shows it); we did not post. */
const val BUNDLE_POSTED_ID_SOLO_SYSTEM = -1

sealed class PipelineResult {
    data object PassThrough : PipelineResult()
    data class Allow(val action: RuleAction) : PipelineResult()
    data object Suppress : PipelineResult()
    /** JoinBundle path: listener must cancel soloSbnKey + this key, call handleBundle, then dispatch allowAction if not NONE. */
    data class Bundle(val bundleId: String, val soloSbnKey: String, val recordId: String, val allowAction: RuleAction) : PipelineResult()
}

@Singleton
class ClassifierPipeline @Inject constructor(
    private val focusModeRepository: FocusModeRepository,
    private val notificationRecordRepository: NotificationRecordRepository,
    private val notificationModel: NotificationModel,
    private val bundleEngine: BundleEngine,
    private val bundleRepository: BundleRepository,
    private val parsedCache: ParsedNotificationCache
) {

    companion object {
        private const val TAG = "ClassifierPipeline"
    }

    /**
     * Pipeline order per docs: Parse → Model (every notif) → BundleEngine.assign → RuleEngine (for NewBundle only) → delivery.
     * JoinBundle → always group (rules do not override). NewBundle → rule ALLOW/SUPPRESS or priority vs threshold.
     */
    suspend fun process(parsed: ParsedNotification): PipelineResult {
        val activeModes = focusModeRepository.getActive()

        if (activeModes.isEmpty()) {
            Log.d(TAG, "No active focus mode — pass through: ${parsed.appLabel} / ${parsed.title}")
            persist(parsed, NotificationOutcome.ALLOWED, null, null, null)
            return PipelineResult.PassThrough
        }

        val mergedRules = activeModes.flatMap { it.rules }
        val threshold = activeModes.first().priorityThreshold
        Log.d(TAG, "Active modes: ${activeModes.joinToString { it.name }}, threshold=$threshold, rules=${mergedRules.size}")

        // 1. Model on every notification (for bundling + priority)
        val output = notificationModel.infer(parsed.rawPrompt, parsed.packageName)
        val decision = bundleEngine.assign(parsed, output)

        return when (decision) {
            is BundleDecision.JoinBundle -> {
                val existingBundle = bundleRepository.getBundleByBundleId(decision.bundleId)
                if (!hasLiveState(existingBundle)) {
                    Log.d(TAG, "JoinBundle but no live state — treat as NewBundle for delivery (run rules)")
                    handleNewBundle(parsed, BundleDecision.NewBundle(decision.bundleId, decision.priority), mergedRules, threshold)
                } else {
                    handleJoinBundle(parsed, decision, output.priority, existingBundle!!)
                }
            }
            is BundleDecision.NewBundle -> handleNewBundle(parsed, decision, mergedRules, threshold)
        }
    }

    private fun hasLiveState(entry: BundleMapEntryEntity?): Boolean =
        entry != null && !entry.notificationIds.isNullOrBlank()

    private suspend fun handleJoinBundle(
        parsed: ParsedNotification,
        decision: BundleDecision.JoinBundle,
        priority: Float,
        existing: BundleMapEntryEntity
    ): PipelineResult {
        val recordId = UUID.randomUUID().toString()
        persist(parsed, NotificationOutcome.BUNDLED, null, priority, decision.bundleId, recordId)
        val allowAction = existing.allowAction?.let { RuleAction.valueOf(it) } ?: RuleAction.NONE
        Log.d(TAG, "JoinBundle: bundleId=${decision.bundleId}, soloSbnKey=${existing.soloSbnKey}")
        return PipelineResult.Bundle(decision.bundleId, existing.soloSbnKey!!, recordId, allowAction)
    }

    private suspend fun handleNewBundle(
        parsed: ParsedNotification,
        decision: BundleDecision.NewBundle,
        mergedRules: List<FilterRule>,
        threshold: Float
    ): PipelineResult {
        val ruleResult = RuleEngine.evaluate(parsed, mergedRules)

        return when (ruleResult) {
            is RuleResult.Match -> {
                val rule = ruleResult.rule
                Log.d(TAG, "NewBundle + Rule: type=${rule.type}, value=${rule.value}, effect=${rule.effect}")
                when (rule.effect) {
                    RuleEffect.SUPPRESS -> {
                        persist(parsed, NotificationOutcome.SUPPRESSED, rule.id, decision.priority, null)
                        PipelineResult.Suppress
                    }
                    RuleEffect.ALLOW -> {
                        passThroughNewBundle(parsed, decision, rule.id, decision.priority, rule.action)
                    }
                }
            }
            is RuleResult.NoMatch -> {
                if (decision.priority >= threshold) {
                    Log.d(TAG, "NewBundle + NoMatch: priority ${decision.priority} >= $threshold — pass through")
                    passThroughNewBundle(parsed, decision, null, decision.priority, RuleAction.NONE)
                } else {
                    Log.d(TAG, "NewBundle + NoMatch: priority ${decision.priority} < $threshold — suppress")
                    persist(parsed, NotificationOutcome.SUPPRESSED, null, decision.priority, null)
                    PipelineResult.Suppress
                }
            }
        }
    }

    /** First notification in thread passes through: set bundle live state, cache parsed, persist ALLOWED. */
    private suspend fun passThroughNewBundle(
        parsed: ParsedNotification,
        decision: BundleDecision.NewBundle,
        appliedRuleId: String?,
        priority: Float,
        action: RuleAction
    ): PipelineResult {
        val recordId = UUID.randomUUID().toString()
        persist(parsed, NotificationOutcome.ALLOWED, appliedRuleId, priority, null, recordId)
        val allowActionStr = action.name.takeIf { it != RuleAction.NONE.name }
        bundleRepository.updateBundleLive(
            decision.bundleId,
            parsed.appLabel,
            Json.encodeToString(listOf(recordId)),
            parsed.originalKey,
            BUNDLE_POSTED_ID_SOLO_SYSTEM,
            allowActionStr,
            parsed.timestamp
        )
        parsedCache.put(parsed.originalKey, parsed)
        return if (appliedRuleId != null) PipelineResult.Allow(action) else PipelineResult.PassThrough
    }

    private suspend fun persist(
        parsed: ParsedNotification,
        outcome: NotificationOutcome,
        appliedRuleId: String?,
        priorityScore: Float?,
        bundleId: String?,
        recordId: String = UUID.randomUUID().toString()
    ) {
        try {
            notificationRecordRepository.insert(
                NotificationRecordEntity(
                    id = recordId,
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
                    priorityScore = priorityScore,
                    bundleId = bundleId
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist notification record", e)
        }
    }
}
