package locked.`in`.domain.engine

import android.util.Log
import locked.`in`.domain.model.FilterRule
import locked.`in`.domain.model.ParsedNotification
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
        val ordered = rules.sortedWith(compareBy { typeOrder(it.type) })
        Log.d(TAG, "Evaluating ${ordered.size} rules against pkg=${parsed.packageName}, category=${parsed.category}")
        for (rule in ordered) {
            val matched = matches(parsed, rule)
            Log.d(TAG, "  Rule[${rule.type}/${rule.value}/${rule.effect}] -> matched=$matched")
            if (matched) {
                return RuleResult.Match(rule)
            }
        }
        Log.d(TAG, "No rule matched")
        return RuleResult.NoMatch
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
