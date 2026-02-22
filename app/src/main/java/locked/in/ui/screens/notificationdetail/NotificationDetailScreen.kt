package locked.`in`.ui.screens.notificationdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import locked.`in`.domain.model.NotificationOutcome
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: NotificationDetailViewModel = hiltViewModel()
) {
    val record by viewModel.record.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        val r = record
        if (r == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val outcomeColor = when (r.outcome) {
            NotificationOutcome.ALLOWED.name, NotificationOutcome.PASSED_THROUGH.name -> MaterialTheme.colorScheme.tertiary
            NotificationOutcome.SUPPRESSED.name -> MaterialTheme.colorScheme.error
            NotificationOutcome.BUNDLED.name -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val outcomeLabel = when (r.outcome) {
            NotificationOutcome.ALLOWED.name, NotificationOutcome.PASSED_THROUGH.name -> "Allowed"
            NotificationOutcome.SUPPRESSED.name -> "Suppressed"
            NotificationOutcome.BUNDLED.name -> "Bundled"
            else -> r.outcome
        }
        val timeFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    r.appLabel,
                    style = MaterialTheme.typography.titleLarge
                )
                SuggestionChip(
                    onClick = { },
                    label = { Text(outcomeLabel) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = outcomeColor.copy(alpha = 0.12f),
                        labelColor = outcomeColor
                    )
                )
            }
            Text(
                timeFormat.format(Date(r.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Content card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium)
                    if (r.text.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            r.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Metadata card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Details", style = MaterialTheme.typography.titleSmall)
                    MetadataRow("Package", r.packageName)
                    MetadataRow("Category", r.category)
                    if (r.priorityScore != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Priority",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(
                                progress = { (r.priorityScore / 10f).coerceIn(0f, 1f) },
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                String.format("%.1f", r.priorityScore),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    MetadataRow("Bundle ID", r.bundleId ?: "None")
                    MetadataRow("Rule ID", r.appliedRuleId ?: "None")
                    MetadataRow("Contact", if (r.isContact) "Yes" else "No")
                }
            }

            // Collapsible raw prompt
            var showPrompt by remember { mutableStateOf(false) }
            TextButton(
                onClick = { showPrompt = !showPrompt },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Raw Prompt")
                Icon(
                    if (showPrompt) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            AnimatedVisibility(visible = showPrompt) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        r.rawPrompt,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
