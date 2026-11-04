package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Machine
import com.example.data.model.MaintenanceEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceDialog(
    machines: List<Machine>,
    preselectedMachine: Machine?,
    defaultPerformerName: String,
    onDismiss: () -> Unit,
    onSave: (entry: MaintenanceEntry, rescheduleDate: String?, rescheduleTask: String?) -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val today = remember { LocalDate.now() }

    var selectedMachine by remember {
        mutableStateOf(preselectedMachine ?: machines.firstOrNull())
    }
    var machineDropdownExpanded by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Inspektion") }
    var completedDate by remember { mutableStateOf(today.format(dateFormatter)) }
    var performedBy by remember { mutableStateOf(defaultPerformerName) }
    var notes by remember { mutableStateOf("") }

    // Rescheduling feature
    var shouldReschedule by remember { mutableStateOf(true) }
    var rescheduleTaskTitle by remember { mutableStateOf("") }
    var rescheduleDate by remember { mutableStateOf(today.plusYears(1).format(dateFormatter)) }

    val quickTitles = listOf(
        "Ölwechsel", "Batteriewechsel", "Messer schärfen",
        "Filter reinigen", "TÜV / Inspektion", "Reifenwechsel", "Prüfung / Test"
    )

    val maintenanceTypes = listOf(
        "Inspektion", "Verschleißteil", "Reinigung & Pflege", "Prüfung / TÜV", "Reparatur"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp)
                .imePadding()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Wartung eintragen",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_maintenance_dialog")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Schließen")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Machine Selector Dropdown
                ExposedDropdownMenuBox(
                    expanded = machineDropdownExpanded,
                    onExpandedChange = { machineDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedMachine?.name ?: "Kein Gerät vorhanden",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Gerät / Maschine *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = machineDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("machine_selector_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = machineDropdownExpanded,
                        onDismissRequest = { machineDropdownExpanded = false }
                    ) {
                        machines.forEach { machine ->
                            DropdownMenuItem(
                                text = { Text("${machine.name} (${machine.groupTag})") },
                                onClick = {
                                    selectedMachine = machine
                                    machineDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Title / Task Name
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (rescheduleTaskTitle.isBlank()) {
                            rescheduleTaskTitle = it
                        }
                    },
                    label = { Text("Durchgeführte Wartung *") },
                    placeholder = { Text("z.B. Ölwechsel 5W-30 + Filter") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("maintenance_title_input")
                )

                // Quick Title Suggestions
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickTitles.forEach { suggestion ->
                        FilterChip(
                            selected = title == suggestion,
                            onClick = {
                                title = suggestion
                                if (rescheduleTaskTitle.isBlank()) {
                                    rescheduleTaskTitle = suggestion
                                }
                            },
                            label = { Text(suggestion, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Maintenance Type
                Text(
                    text = "Wartungsart:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    maintenanceTypes.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Completed Date & Performed By
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = completedDate,
                        onValueChange = { completedDate = it },
                        label = { Text("Durchgeführt am") },
                        placeholder = { Text("YYYY-MM-DD") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("completed_date_input")
                    )

                    OutlinedTextField(
                        value = performedBy,
                        onValueChange = { performedBy = it },
                        label = { Text("Wer") },
                        placeholder = { Text("z.B. Marco") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("performed_by_input")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Spacer(modifier = Modifier.height(12.dp))

                // Freitext-Notiz zur Wartung (z.B. welches Öl und wie viel verwendet wurde)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Wartungs-Notiz (Freitext)") },
                    placeholder = { Text("z.B. 4,3 Liter Castrol Edge 5W-30 LL-04, neuer Ölfilter MANN HU711/51x, Dichtring neu") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("maintenance_notes_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // RESCHEDULING SECTION (Crucial user requirement)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Folgetermin erneut planen",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "Terminiert die nächste Fälligkeit am Gerät",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Switch(
                                checked = shouldReschedule,
                                onCheckedChange = { shouldReschedule = it },
                                modifier = Modifier.testTag("reschedule_switch")
                            )
                        }

                        if (shouldReschedule) {
                            Spacer(modifier = Modifier.height(12.dp))

                            // Quick interval chips
                            Text(
                                text = "Intervall wählen:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val intervals = listOf(
                                    "1 Monat" to 1L,
                                    "3 Monate" to 3L,
                                    "6 Monate" to 6L,
                                    "1 Jahr" to 12L,
                                    "2 Jahre" to 24L
                                )
                                intervals.forEach { (label, months) ->
                                    val targetDateStr = today.plusMonths(months).format(dateFormatter)
                                    FilterChip(
                                        selected = rescheduleDate == targetDateStr,
                                        onClick = {
                                            rescheduleDate = targetDateStr
                                        },
                                        label = { Text("+$label", fontSize = 11.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = rescheduleDate,
                                onValueChange = { rescheduleDate = it },
                                label = { Text("Nächstes Fälligkeitsdatum (YYYY-MM-DD)") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reschedule_date_input")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = rescheduleTaskTitle,
                                onValueChange = { rescheduleTaskTitle = it },
                                label = { Text("Bezeichnung der Folge-Wartung") },
                                placeholder = { Text("z.B. Nächster Ölwechsel") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reschedule_task_input")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Save Button
                Button(
                    onClick = {
                        val machine = selectedMachine ?: return@Button

                        val entry = MaintenanceEntry(
                            groupId = machine.groupId,
                            machineId = machine.id,
                            machineName = machine.name,
                            title = title.ifBlank { "Wartung" },
                            type = selectedType,
                            completedDate = completedDate.ifBlank { today.format(dateFormatter) },
                            nextScheduledDate = if (shouldReschedule) rescheduleDate else "",
                            performedBy = performedBy.ifBlank { "Ich" },
                            cost = 0.0,
                            notes = notes
                        )

                        onSave(
                            entry,
                            if (shouldReschedule) rescheduleDate else null,
                            if (shouldReschedule) rescheduleTaskTitle.ifBlank { title } else null
                        )
                    },
                    enabled = selectedMachine != null && title.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_maintenance_button")
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Wartung speichern", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
