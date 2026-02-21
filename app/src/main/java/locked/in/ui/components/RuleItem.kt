package locked.`in`.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import locked.`in`.domain.model.FilterRule
import locked.`in`.domain.model.RuleEffect
import locked.`in`.domain.model.RuleType
import locked.`in`.domain.model.SupportedApp

private fun ruleIcon(type: RuleType) = when (type) {
    RuleType.APP -> Icons.Default.PhoneAndroid
    RuleType.KEYWORD -> Icons.Default.TextFields
    RuleType.CONTACT -> Icons.Default.Person
    RuleType.CATEGORY -> Icons.Default.Category
}

private fun ruleDescription(rule: FilterRule): String {
    val verb = when (rule.effect) {
        RuleEffect.SUPPRESS -> "Suppress"
        RuleEffect.ALLOW -> "Allow"
    }
    return when (rule.type) {
        RuleType.APP -> {
            val app = SupportedApp.fromValue(rule.value)
            "$verb ${app?.displayName ?: rule.value}"
        }
        RuleType.KEYWORD -> "$verb keyword: ${rule.value}"
        RuleType.CONTACT -> "$verb contact: ${rule.value}"
        RuleType.CATEGORY -> "$verb category: ${rule.value}"
    }
}

private fun ruleTitle(rule: FilterRule): String {
    return when (rule.type) {
        RuleType.APP -> {
            val app = SupportedApp.fromValue(rule.value)
            app?.displayName ?: rule.value
        }
        else -> rule.value
    }
}

@Composable
fun RuleItem(
    rule: FilterRule,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = ruleIcon(rule.type),
                contentDescription = rule.type.name,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    ruleTitle(rule),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    ruleDescription(rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete rule")
            }
        }
    }
}
