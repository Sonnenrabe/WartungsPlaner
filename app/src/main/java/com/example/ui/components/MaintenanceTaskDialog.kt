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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.MaintenanceTask
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun MaintenanceTaskDialog(
    taskToEdit: MaintenanceTask?,
    defaultPerformerName: String = "Ich",
    initiallyCompleted: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (MaintenanceTask) -> Unit,
    onSaveCompleted: ((title: String, completedDate: String, performedBy: String, notes: String, rescheduleDate: String?, intervalMonths: Long) -> Unit)? = null
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val today = remember { LocalDate.now() }

    // Mode: Already completed vs open task
    var isAlreadyCompleted by remember { mutableStateOf(initiallyCompleted && taskToEdit == null) }

    var title by remember { mutableStateOf(taskToEdit?.title ?: "") }
    var dueDate by remember {
        mutableStateOf(taskToEdit?.dueDate?.takeIf { it.isNotBlank() } ?: today.plusMonths(6).format(dateFormatter))
    }
    var intervalMonths by remember { mutableStateOf(taskToEdit?.intervalMonths ?: 12L) }
    var notes by remember { mutableStateOf(taskToEdit?.notes ?: "") }

    // Completed fields
    var completedDate by remember { mutableStateOf(today.format(dateFormatter)) }
    var performedBy by remember { mutableStateOf(defaultPerformerName) }
    var planNextFollowUp by remember { mutableStateOf(intervalMonths > 0) }
    var nextFollowUpDate by remember {
        val nextMonths = if (intervalMonths > 0) intervalMonths else 12L
        mutableStateOf(today.plusMonths(nextMonths).format(dateFormatter))
    }

    val quickTitles = listOf(
        "Ölwechsel", "TÜV / HU", "Batteriewechsel", "Messer schärfen",
        "Luftfilter reinigen", "Reifenwechsel (Winter/Sommer)", "Bremsen prüfen",
        "Zahnriemen", "Funktionstest"
    )

    val intervalOptions = listOf(
        "Einmalig" to 0L,
        "Alle 3 Monate" to 3L,
        "Alle 6 Monate" to 6L,
        "Jährlich (12 Mon.)" to 12L,
        "Alle 2 Jahre (24 Mon.)" to 24L
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
                        text = when {
                            taskToEdit != null -> "Aufgabe bearbeiten"
                            isAlreadyCompleted -> "Erledigte Wartung erfassen"
                            else -> "Wartungsaufgabe anlegen"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_task_dialog")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Schließen")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle: Is already completed (only available when creating new task)
                if (taskToEdit == null && onSaveCompleted != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAlreadyCompleted) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isAlreadyCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Bereits erledigt?",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = if (isAlreadyCompleted) {
                                            "Wartung wurde schon durchgeführt (direkt protokollieren)"
                                        } else {
                                            "Offene Aufgabe für die Zukunft planen"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isAlreadyCompleted,
                                onCheckedChange = { isAlreadyCompleted = it },
                                modifier = Modifier.testTag("already_completed_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Task Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Wartungs-Bezeichnung *") },
                    placeholder = { Text("z.B. Ölwechsel 5W-30, TÜV / HU, Messer schärfen") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick suggestions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickTitles.forEach { suggestion ->
                        FilterChip(
                            selected = title == suggestion,
                            onClick = { title = suggestion },
                            label = { Text(suggestion, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isAlreadyCompleted) {
                    // --- ALREADY COMPLETED FIELDS ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = completedDate,
                            onValueChange = { completedDate = it },
                            label = { Text("Erledigt am *") },
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
                            label = { Text("Durchgeführt von *") },
                            placeholder = { Text("z.B. Marco") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("completed_performer_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Freitext Notiz (z.B. welches Öl, wie viel verwendet, Filter etc.)
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Wartungs-Notiz / Freitext (Details)") },
                        placeholder = { Text("z.B. 4,3 Liter Castrol 5W-30, Ölfilter MANN neu, Kupferdichtung gewechselt") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("completed_notes_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Follow-up scheduling card
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
                                            text = "Folgetermin für nächstes Mal planen",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "Erstellt automatisch eine neue Aufgabe für die nächste Wartung",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                Switch(
                                    checked = planNextFollowUp,
                                    onCheckedChange = { planNextFollowUp = it },
                                    modifier = Modifier.testTag("plan_follow_up_switch")
                                )
                            }

                            if (planNextFollowUp) {
                                Spacer(modifier = Modifier.height(10.dp))

                                // Quick intervals
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    intervalOptions.filter { it.second > 0 }.forEach { (label, months) ->
                                        FilterChip(
                                            selected = intervalMonths == months,
                                            onClick = {
                                                intervalMonths = months
                                                nextFollowUpDate = today.plusMonths(months).format(dateFormatter)
                                            },
                                            label = { Text(label, fontSize = 11.sp) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = nextFollowUpDate,
                                    onValueChange = { nextFollowUpDate = it },
                                    label = { Text("Nächstes Fälligkeitsdatum") },
                                    placeholder = { Text("YYYY-MM-DD") },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("next_follow_up_date_input")
                                )
                            }
                        }
                    }
                } else {
                    // --- OPEN TASK PLANNING FIELDS ---
                    Text(
                        text = "Fälligkeitsdatum *",
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
                        val quickDueDates = listOf(
                            "1 Monat" to 1L,
                            "3 Monate" to 3L,
                            "6 Monate" to 6L,
                            "1 Jahr" to 12L,
                            "2 Jahre" to 24L
                        )
                        quickDueDates.forEach { (label, months) ->
                            val targetDate = today.plusMonths(months).format(dateFormatter)
                            FilterChip(
                                selected = dueDate == targetDate,
                                onClick = { dueDate = targetDate },
                                label = { Text("+$label", fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("Datum (YYYY-MM-DD) *") },
                        placeholder = { Text("YYYY-MM-DD") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("task_due_date_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Recurring interval
                    Text(
                        text = "Wiederholungs-Intervall:",
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
                        intervalOptions.forEach { (label, months) ->
                            FilterChip(
                                selected = intervalMonths == months,
                                onClick = { intervalMonths = months },
                                leadingIcon = if (months > 0) {
                                    { Icon(imageVector = Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Task Notes
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Aufgaben-Notiz & Hinweise (optional)") },
                        placeholder = { Text("z.B. passendes Ersatzteil, Messertyp, Drehmoment") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("task_notes_input")
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Save Button
                Button(
                    onClick = {
                        if (isAlreadyCompleted && onSaveCompleted != null) {
                            onSaveCompleted(
                                title.trim(),
                                completedDate.ifBlank { today.format(dateFormatter) },
                                performedBy.ifBlank { defaultPerformerName },
                                notes.trim(),
                                if (planNextFollowUp) nextFollowUpDate.trim() else null,
                                intervalMonths
                            )
                        } else {
                            val task = (taskToEdit ?: MaintenanceTask()).copy(
                                id = taskToEdit?.id?.ifBlank { UUID.randomUUID().toString() } ?: UUID.randomUUID().toString(),
                                title = title.trim(),
                                dueDate = dueDate.trim(),
                                intervalMonths = intervalMonths,
                                notes = notes.trim(),
                                isCompleted = false
                            )
                            onSave(task)
                        }
                    },
                    enabled = if (isAlreadyCompleted) title.isNotBlank() && completedDate.isNotBlank() else title.isNotBlank() && dueDate.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_task_button")
                ) {
                    if (isAlreadyCompleted) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Als erledigte Wartung speichern",
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (taskToEdit == null) "Aufgabe hinzufügen" else "Änderungen speichern",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
