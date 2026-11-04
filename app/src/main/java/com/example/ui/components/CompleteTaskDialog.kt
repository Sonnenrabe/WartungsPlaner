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
import com.example.data.model.Machine
import com.example.data.model.MaintenanceTask
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CompleteTaskDialog(
    task: MaintenanceTask,
    machine: Machine,
    defaultPerformerName: String,
    onDismiss: () -> Unit,
    onComplete: (completedDate: String, performedBy: String, notes: String, nextScheduledDate: String?, nextTaskTitle: String?) -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val today = remember { LocalDate.now() }

    var completedDate by remember { mutableStateOf(today.format(dateFormatter)) }
    var performedBy by remember { mutableStateOf(defaultPerformerName) }
    var notes by remember { mutableStateOf(task.notes) }

    val hasInterval = task.intervalMonths > 0
    var shouldReschedule by remember { mutableStateOf(hasInterval) }
    var rescheduleDate by remember {
        val initialMonths = if (task.intervalMonths > 0) task.intervalMonths else 12L
        mutableStateOf(today.plusMonths(initialMonths).format(dateFormatter))
    }
    var rescheduleTitle by remember { mutableStateOf(task.title) }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Wartung erledigt",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${task.title} • ${machine.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_complete_dialog")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Schließen")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Date & Performed By
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = completedDate,
                        onValueChange = { completedDate = it },
                        label = { Text("Erledigt am") },
                        placeholder = { Text("YYYY-MM-DD") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("complete_date_input")
                    )

                    OutlinedTextField(
                        value = performedBy,
                        onValueChange = { performedBy = it },
                        label = { Text("Durchgeführt von") },
                        placeholder = { Text("z.B. Marco") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("complete_performer_input")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Freitext-Notiz zur Wartung (z.B. welches Öl, wie viel etc.)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Wartungs-Notiz (Freitext)") },
                    placeholder = { Text("z.B. 4,3 Liter Castrol Edge 5W-30 LL-04, neuer Ölfilter MANN HU711/51x, Dichtring neu") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("complete_notes_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Rescheduling card
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
                                        text = "Direkt erneut terminieren",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = if (task.intervalMonths > 0) "Intervall: Alle ${task.intervalMonths} Monate" else "Nächsten Fälligkeitstermin planen",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Switch(
                                checked = shouldReschedule,
                                onCheckedChange = { shouldReschedule = it },
                                modifier = Modifier.testTag("complete_reschedule_switch")
                            )
                        }

                        if (shouldReschedule) {
                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick interval chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val quickIntervals = listOf(
                                    "1 Monat" to 1L,
                                    "3 Monate" to 3L,
                                    "6 Monate" to 6L,
                                    "1 Jahr" to 12L,
                                    "2 Jahre" to 24L
                                )
                                quickIntervals.forEach { (label, months) ->
                                    val target = today.plusMonths(months).format(dateFormatter)
                                    FilterChip(
                                        selected = rescheduleDate == target,
                                        onClick = { rescheduleDate = target },
                                        label = { Text("+$label", fontSize = 11.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = rescheduleDate,
                                onValueChange = { rescheduleDate = it },
                                label = { Text("Neues Fälligkeitsdatum") },
                                placeholder = { Text("YYYY-MM-DD") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("complete_reschedule_date_input")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        onComplete(
                            completedDate.ifBlank { today.format(dateFormatter) },
                            performedBy.ifBlank { "Ich" },
                            notes.trim(),
                            if (shouldReschedule) rescheduleDate else null,
                            if (shouldReschedule) rescheduleTitle.ifBlank { task.title } else null
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_complete_button")
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("In Historie protokollieren & Speichern", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
