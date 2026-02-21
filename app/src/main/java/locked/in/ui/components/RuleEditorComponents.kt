package locked.`in`.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import locked.`in`.domain.model.RuleType
import locked.`in`.domain.model.SupportedApp

val CATEGORIES = listOf(
    "call" to "Phone calls",
    "message" to "Text messages",
    "email" to "Email notifications",
    "social" to "Social media",
    "mention" to "Mentions and tags",
    "group_message" to "Group messages",
    "system" to "System notifications",
    "other" to "Other notifications"
)

fun ruleTypeLabel(type: RuleType): String = when (type) {
    RuleType.APP -> "App"
    RuleType.KEYWORD -> "Keyword"
    RuleType.CONTACT -> "Contact"
    RuleType.CATEGORY -> "Category"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDropdown(
    selectedApp: SupportedApp?,
    onSelect: (SupportedApp) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedApp?.displayName ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Select app") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SupportedApp.entries.forEach { app ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(app.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                app.packageNames.first(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelect(app)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdown(
    selectedCategory: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = CATEGORIES.firstOrNull { it.first == selectedCategory }?.second ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = displayLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Select category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            CATEGORIES.forEach { (value, description) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(description, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}
