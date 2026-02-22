package locked.`in`.domain.engine

import android.util.Log
import locked.`in`.domain.model.FilterRule
import locked.`in`.domain.model.ParsedNotification
import locked.`in`.domain.model.RuleEffect
import locked.`in`.domain.model.RuleType
import locked.`in`.domain.model.SupportedApp

sealed class RuleResult {
    data class Match(val rule: FilterRule) : RuleResult()
    data object NoMatch : RuleResult()
}

object RuleEngine {

    private const val TAG = "RuleEngine"

    fun evaluate(parsed: ParsedNotification, rules: List<FilterRule>): RuleResult {
        if (rules.isEmpty()) {
            Log.d(TAG, "No rules to evaluate")
            return RuleResult.NoMatch
        }
        Log.d(TAG, "Evaluating ${rules.size} rules against pkg=${parsed.packageName}, category=${parsed.category}")
        val matching = rules.filter { matches(parsed, it) }
        matching.forEach { rule ->
            Log.d(TAG, "  Rule[${rule.type}/${rule.value}/${rule.effect}] -> matched")
        }
        val allowRules = matching.filter { it.effect == RuleEffect.ALLOW }
        val suppressRules = matching.filter { it.effect == RuleEffect.SUPPRESS }
        return when {
            allowRules.isNotEmpty() -> {
                val pick = allowRules.minWith(compareBy<FilterRule> { typeOrder(it.type) }.thenBy { it.sortOrder })
                RuleResult.Match(pick)
            }
            suppressRules.isNotEmpty() -> {
                val pick = suppressRules.minWith(compareBy<FilterRule> { typeOrder(it.type) }.thenBy { it.sortOrder })
                RuleResult.Match(pick)
            }
            else -> {
                Log.d(TAG, "No rule matched")
                RuleResult.NoMatch
            }
        }
    }

    private fun typeOrder(type: RuleType): Int = when (type) {
        RuleType.CATEGORY -> 0
        RuleType.CONTACT -> 1
        RuleType.APP -> 2
        RuleType.KEYWORD -> 3
    }

    private fun matches(parsed: ParsedNotification, rule: FilterRule): Boolean = when (rule.type) {
        RuleType.CATEGORY -> parsed.category.equals(rule.value, ignoreCase = true)
        RuleType.CONTACT -> {
            val sender = parsed.sender ?: parsed.title
            sender.equals(rule.value, ignoreCase = true)
        }
        RuleType.APP -> {
            val supportedApp = SupportedApp.fromValue(rule.value)
            if (supportedApp != null) {
                supportedApp.packageNames.any { it.equals(parsed.packageName, ignoreCase = true) }
            } else {
                parsed.packageName.equals(rule.value, ignoreCase = true)
            }
        }
        RuleType.KEYWORD -> {
            val haystack = "${parsed.title} ${parsed.text}"
            haystack.contains(rule.value, ignoreCase = true)
        }
    }
}
