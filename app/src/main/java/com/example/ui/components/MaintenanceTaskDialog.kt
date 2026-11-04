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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
    onDismiss: () -> Unit,
    onSave: (MaintenanceTask) -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val today = remember { LocalDate.now() }

    var title by remember { mutableStateOf(taskToEdit?.title ?: "") }
    var dueDate by remember {
        mutableStateOf(taskToEdit?.dueDate?.takeIf { it.isNotBlank() } ?: today.plusMonths(6).format(dateFormatter))
    }
    var intervalMonths by remember { mutableStateOf(taskToEdit?.intervalMonths ?: 12L) }
    var notes by remember { mutableStateOf(taskToEdit?.notes ?: "") }

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
                        text = if (taskToEdit == null) "Wartungsaufgabe anlegen" else "Aufgabe bearbeiten",
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

                Spacer(modifier = Modifier.height(16.dp))

                // Task Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Aufgaben-Bezeichnung *") },
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

                // Due Date
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

                Spacer(modifier = Modifier.height(20.dp))

                // Save Button
                Button(
                    onClick = {
                        val task = (taskToEdit ?: MaintenanceTask()).copy(
                            id = taskToEdit?.id?.ifBlank { UUID.randomUUID().toString() } ?: UUID.randomUUID().toString(),
                            title = title.trim(),
                            dueDate = dueDate.trim(),
                            intervalMonths = intervalMonths,
                            notes = notes.trim(),
                            isCompleted = false
                        )
                        onSave(task)
                    },
                    enabled = title.isNotBlank() && dueDate.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_task_button")
                ) {
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
