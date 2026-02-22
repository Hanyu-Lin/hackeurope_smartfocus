package locked.`in`.ui.screens.focusmodedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import locked.`in`.domain.model.FilterRule
import locked.`in`.domain.model.RuleAction
import locked.`in`.domain.model.RuleEffect
import locked.`in`.domain.model.RuleType
import locked.`in`.domain.model.SupportedApp
import locked.`in`.ui.components.AppDropdown
import locked.`in`.ui.components.CategoryDropdown
import locked.`in`.ui.components.RuleItem
import locked.`in`.ui.components.ruleTypeLabel
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: FocusModeDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Bottom sheet state
    var showRuleSheet by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<FilterRule?>(null) }
    var selectedType by remember { mutableStateOf(RuleType.APP) }
    var ruleValue by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<SupportedApp?>(null) }
    var selectedEffect by remember { mutableStateOf(RuleEffect.SUPPRESS) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Focus Mode") },
            text = { Text("Are you sure you want to delete this focus mode and all its rules?") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteMode(onNavigateBack) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRuleSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showRuleSheet = false
                editingRule = null
            },
            sheetState = sheetState
        ) {
            RuleEditorSheetContent(
                isEditing = editingRule != null,
                selectedType = selectedType,
                ruleValue = ruleValue,
                selectedApp = selectedApp,
                selectedEffect = selectedEffect,
                onTypeSelected = { type ->
                    selectedType = type
                    ruleValue = ""
                    selectedApp = null
                },
                onValueChanged = { ruleValue = it },
                onAppSelected = { app ->
                    selectedApp = app
                    ruleValue = app.name
                },
                onCategorySelected = { ruleValue = it },
                onEffectSelected = { selectedEffect = it },
                onSave = {
                    if (ruleValue.isNotBlank()) {
                        val existing = editingRule
                        if (existing != null) {
                            viewModel.updateRule(
                                existing.copy(
                                    type = selectedType,
                                    value = ruleValue,
                                    effect = selectedEffect
                                )
                            )
                        } else {
                            viewModel.addRule(selectedType, ruleValue, selectedEffect)
                        }
                        scope.launch {
                            sheetState.hide()
                            showRuleSheet = false
                            editingRule = null
                        }
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.mode?.name ?: "Focus Mode") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingRule = null
                selectedType = RuleType.APP
                ruleValue = ""
                selectedApp = null
                selectedEffect = RuleEffect.SUPPRESS
                showRuleSheet = true
            }) {
                Icon(Icons.Default.Add, "Add Rule")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val mode = uiState.mode ?: return@Scaffold

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Mode name
            item {
                var name by remember(mode.name) { mutableStateOf(mode.name) }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        viewModel.updateName(it)
                    },
                    label = { Text("Mode Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Activate toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (mode.isActive) "This mode is currently active" else "Tap to activate this mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = mode.isActive,
                        onCheckedChange = { viewModel.toggleActive() }
                    )
                }
            }

            // Timer section
            item {
                TimerSection(
                    timerEnabled = mode.timerEnabled,
                    timerDurationMinutes = mode.timerDurationMinutes,
                    enabled = !mode.scheduleEnabled,
                    onToggleTimer = { viewModel.toggleTimerEnabled() },
                    onDurationChanged = { viewModel.updateTimerDuration(it) }
                )
            }

            // Schedule section
            item {
                ScheduleSection(
                    scheduleEnabled = mode.scheduleEnabled,
                    scheduleDays = mode.scheduleDays,
                    scheduleStartMinute = mode.scheduleStartMinute,
                    scheduleEndMinute = mode.scheduleEndMinute,
                    enabled = !mode.timerEnabled,
                    onToggleSchedule = { viewModel.toggleScheduleEnabled() },
                    onDaysChanged = { viewModel.updateScheduleDays(it) },
                    onStartTimeChanged = { h, m -> viewModel.updateScheduleStartTime(h, m) },
                    onEndTimeChanged = { h, m -> viewModel.updateScheduleEndTime(h, m) }
                )
            }

            // Priority threshold
            item {
                Column {
                    Text("Priority Threshold", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Low",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            String.format("%.1f", mode.priorityThreshold),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "High",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    var threshold by remember(mode.priorityThreshold) { mutableFloatStateOf(mode.priorityThreshold) }
                    Slider(
                        value = threshold,
                        onValueChange = { threshold = it },
                        onValueChangeFinished = { viewModel.updateThreshold(threshold) },
                        valueRange = 0f..10f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Notifications scoring below this threshold will be suppressed when no rule matches",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Rules grouped by type
            val rulesByType = uiState.rules.groupBy { it.type }
            val typeOrder = listOf(RuleType.APP, RuleType.KEYWORD, RuleType.CONTACT, RuleType.CATEGORY)

            typeOrder.forEach { type ->
                val rulesForType = rulesByType[type] ?: return@forEach
                val sectionTitle = when (type) {
                    RuleType.APP -> "App Rules"
                    RuleType.KEYWORD -> "Keyword Rules"
                    RuleType.CONTACT -> "Contact Rules"
                    RuleType.CATEGORY -> "Category Rules"
                }
                item {
                    Text(
                        "$sectionTitle (${rulesForType.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(rulesForType) { rule ->
                    RuleItem(
                        rule = rule,
                        onClick = {
                            editingRule = rule
                            selectedType = rule.type
                            ruleValue = rule.value
                            selectedApp = if (rule.type == RuleType.APP) SupportedApp.fromValue(rule.value) else null
                            selectedEffect = rule.effect
                            showRuleSheet = true
                        },
                        onDelete = { viewModel.deleteRule(rule.id) }
                    )
                }
            }

            if (uiState.rules.isEmpty()) {
                item {
                    Text(
                        "No rules yet. Tap + to add a rule.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Delete mode button
            item {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Focus Mode")
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditorSheetContent(
    isEditing: Boolean,
    selectedType: RuleType,
    ruleValue: String,
    selectedApp: SupportedApp?,
    selectedEffect: RuleEffect,
    onTypeSelected: (RuleType) -> Unit,
    onValueChanged: (String) -> Unit,
    onAppSelected: (SupportedApp) -> Unit,
    onCategorySelected: (String) -> Unit,
    onEffectSelected: (RuleEffect) -> Unit,
    onSave: () -> Unit
) {
    val effectVerb = if (selectedEffect == RuleEffect.SUPPRESS) "suppressed" else "allowed through"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            if (isEditing) "Edit Rule" else "Add Rule",
            style = MaterialTheme.typography.titleLarge
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RuleType.entries.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { onTypeSelected(type) },
                    label = { Text(ruleTypeLabel(type)) }
                )
            }
        }

        // Effect selector
        Column {
            Text("Effect", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedEffect == RuleEffect.SUPPRESS,
                    onClick = { onEffectSelected(RuleEffect.SUPPRESS) },
                    label = { Text("Suppress") }
                )
                FilterChip(
                    selected = selectedEffect == RuleEffect.ALLOW,
                    onClick = { onEffectSelected(RuleEffect.ALLOW) },
                    label = { Text("Allow (Pass Through)") }
                )
            }
        }

        when (selectedType) {
            RuleType.APP -> AppDropdown(
                selectedApp = selectedApp,
                onSelect = onAppSelected
            )
            RuleType.CATEGORY -> CategoryDropdown(
                selectedCategory = ruleValue,
                onSelect = onCategorySelected
            )
            RuleType.KEYWORD -> {
                OutlinedTextField(
                    value = ruleValue,
                    onValueChange = onValueChanged,
                    label = { Text("Keyword") },
                    supportingText = { Text("Notifications containing this keyword will be $effectVerb") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            RuleType.CONTACT -> {
                OutlinedTextField(
                    value = ruleValue,
                    onValueChange = onValueChanged,
                    label = { Text("Contact name") },
                    supportingText = { Text("Notifications from this sender will be $effectVerb") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = onSave,
            enabled = ruleValue.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isEditing) "Update Rule" else "Add Rule")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSection(
    scheduleEnabled: Boolean,
    scheduleDays: Set<DayOfWeek>,
    scheduleStartMinute: Int,
    scheduleEndMinute: Int,
    enabled: Boolean = true,
    onToggleSchedule: () -> Unit,
    onDaysChanged: (Set<DayOfWeek>) -> Unit,
    onStartTimeChanged: (Int, Int) -> Unit,
    onEndTimeChanged: (Int, Int) -> Unit
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Schedule", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Auto-activate this mode on a schedule",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = scheduleEnabled, onCheckedChange = { onToggleSchedule() }, enabled = enabled)
        }

        if (scheduleEnabled) {
            // Day-of-week chips
            Text("Days", style = MaterialTheme.typography.labelMedium)
            if (scheduleDays.isEmpty()) {
                Text(
                    "Select at least one day — mode won't activate until you do",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in scheduleDays,
                        onClick = {
                            val newDays = if (day in scheduleDays) scheduleDays - day else scheduleDays + day
                            onDaysChanged(newDays)
                        },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }
                    )
                }
            }

            // Quick-select buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    onDaysChanged(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
                }) { Text("Weekdays") }
                TextButton(onClick = {
                    onDaysChanged(DayOfWeek.entries.toSet())
                }) { Text("Every day") }
            }

            // Time pickers (end time is exclusive: active until just before end)
            Text(
                "Active from start time up to (but not including) end time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.weight(1f)
                ) { Text("Start: ${formatMinutes(scheduleStartMinute)}") }

                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.weight(1f)
                ) { Text("End: ${formatMinutes(scheduleEndMinute)}") }
            }
        }
    }

    if (showStartPicker) {
        TimePickerDialog(
            initialHour = scheduleStartMinute / 60,
            initialMinute = scheduleStartMinute % 60,
            onConfirm = { h, m ->
                onStartTimeChanged(h, m)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initialHour = scheduleEndMinute / 60,
            initialMinute = scheduleEndMinute % 60,
            onConfirm = { h, m ->
                onEndTimeChanged(h, m)
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimerSection(
    timerEnabled: Boolean,
    timerDurationMinutes: Int,
    enabled: Boolean = true,
    onToggleTimer: () -> Unit,
    onDurationChanged: (Int) -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Timer", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Auto-deactivate after a set duration",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = timerEnabled, onCheckedChange = { onToggleTimer() }, enabled = enabled)
        }

        if (timerEnabled) {
            Text("Duration", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 45, 60, 90).forEach { minutes ->
                    FilterChip(
                        selected = timerDurationMinutes == minutes,
                        onClick = { onDurationChanged(minutes) },
                        label = { Text("${minutes}m") }
                    )
                }
                FilterChip(
                    selected = listOf(25, 45, 60, 90).none { it == timerDurationMinutes },
                    onClick = { showCustomDialog = true },
                    label = {
                        Text(
                            if (listOf(25, 45, 60, 90).none { it == timerDurationMinutes })
                                "Custom (${timerDurationMinutes}m)"
                            else
                                "Custom"
                        )
                    }
                )
            }
        }
    }

    if (showCustomDialog) {
        CustomDurationDialog(
            initialMinutes = timerDurationMinutes,
            onConfirm = {
                onDurationChanged(it)
                showCustomDialog = false
            },
            onDismiss = { showCustomDialog = false }
        )
    }
}

@Composable
private fun CustomDurationDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialMinutes.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Duration") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() } },
                label = { Text("Minutes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minutes = text.toIntOrNull()
                    if (minutes != null && minutes > 0) onConfirm(minutes)
                }
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = state)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
                }
            }
        }
    }
}

private fun formatMinutes(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    val amPm = if (h < 12) "AM" else "PM"
    val displayHour = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return String.format("%d:%02d %s", displayHour, m, amPm)
}
