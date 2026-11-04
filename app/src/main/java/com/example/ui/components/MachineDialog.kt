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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@Composable
fun MachineDialog(
    machineToEdit: Machine?,
    existingGroupTags: List<String>,
    onDismiss: () -> Unit,
    onSave: (Machine) -> Unit,
    onAddNewGroupTag: ((String) -> Unit)? = null
) {
    val cleanTags = remember(existingGroupTags) {
        existingGroupTags.filter { it.isNotBlank() && it != "Alle" }.distinct()
    }

    var name by remember { mutableStateOf(machineToEdit?.name ?: "") }
    var category by remember { mutableStateOf(machineToEdit?.category ?: "Auto") }
    var groupTag by remember {
        mutableStateOf(machineToEdit?.groupTag ?: cleanTags.firstOrNull() ?: "Allgemein")
    }
    var isAddingCustomGroup by remember { mutableStateOf(false) }
    var customGroupInput by remember { mutableStateOf("") }
    var location by remember { mutableStateOf(machineToEdit?.location ?: "") }
    var notes by remember { mutableStateOf(machineToEdit?.notes ?: "") }
    var nextDueDate by remember { mutableStateOf(machineToEdit?.nextDueDate ?: "") }
    var nextDueMaintenance by remember { mutableStateOf(machineToEdit?.nextDueMaintenance ?: "") }

    val categories = listOf(
        "Auto" to Icons.Default.DirectionsCar,
        "Garten" to Icons.Default.Yard,
        "Sicherheit" to Icons.Default.Security,
        "Haushalt" to Icons.Default.Home,
        "Werkzeug" to Icons.Default.Handyman,
        "Sonstiges" to Icons.Default.Handyman
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
                        text = if (machineToEdit == null) "Gerät / Maschine anlegen" else "Gerät bearbeiten",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_machine_dialog")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Schließen")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name / Bezeichnung *") },
                    placeholder = { Text("z.B. VW Golf 7, Rasenmäher, Rauchmelder Flur") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("machine_name_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category Chips
                Text(
                    text = "Kategorie:",
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
                    categories.forEach { (cat, icon) ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            leadingIcon = {
                                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text(cat, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Group Tag (Startbildschirm-Gruppierung)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Gruppe auf dem Startbildschirm *",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { isAddingCustomGroup = !isAddingCustomGroup },
                        modifier = Modifier.testTag("toggle_add_custom_group_button")
                    ) {
                        Icon(
                            imageVector = if (isAddingCustomGroup) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isAddingCustomGroup) "Abbrechen" else "+ Neue Gruppe", fontSize = 12.sp)
                    }
                }

                if (isAddingCustomGroup) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customGroupInput,
                            onValueChange = { customGroupInput = it },
                            label = { Text("Neue Gruppe benennen") },
                            placeholder = { Text("z.B. Autos, Rasenmäher, Heizung...") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("custom_group_text_input")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val trimmed = customGroupInput.trim()
                                if (trimmed.isNotBlank()) {
                                    groupTag = trimmed
                                    onAddNewGroupTag?.invoke(trimmed)
                                    customGroupInput = ""
                                    isAddingCustomGroup = false
                                }
                            },
                            enabled = customGroupInput.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("confirm_custom_group_button")
                        ) {
                            Text("OK")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val availableTags = (cleanTags + listOf(groupTag)).filter { it.isNotBlank() }.distinct()
                    if (availableTags.isEmpty()) {
                        FilterChip(
                            selected = groupTag == "Allgemein",
                            onClick = { groupTag = "Allgemein" },
                            label = { Text("Allgemein", fontSize = 12.sp) }
                        )
                    } else {
                        availableTags.forEach { tag ->
                            FilterChip(
                                selected = groupTag == tag,
                                onClick = { groupTag = tag },
                                label = { Text(tag, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Location
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Standort / Aufbewahrungsort") },
                    placeholder = { Text("z.B. Garage links, Geräteschuppen, Flur OG") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("location_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Notes (Freitext für Spezifikationen wie Öl, Reifen, etc.)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notizen & Details (Freitext)") },
                    placeholder = {
                        Text("z.B. Motoröl 5W-30 LL-04, Winterreifen DOT 3821 (205/55 R16), Reifendruck 2.3 bar, Akkutyp, Filter")
                    },
                    minLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("machine_notes_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Initial Next Due Date (optional)
                Text(
                    text = "Nächste fällige Wartung (optional):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = nextDueDate,
                        onValueChange = { nextDueDate = it },
                        label = { Text("Fälligkeitsdatum") },
                        placeholder = { Text("YYYY-MM-DD") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("machine_next_due_date_input")
                    )

                    OutlinedTextField(
                        value = nextDueMaintenance,
                        onValueChange = { nextDueMaintenance = it },
                        label = { Text("Wartungsaufgabe") },
                        placeholder = { Text("z.B. Inspektion") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("machine_next_due_task_input")
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val m = (machineToEdit ?: Machine()).copy(
                            name = name.trim(),
                            category = category.trim(),
                            groupTag = groupTag.trim().ifBlank { "Allgemein" },
                            location = location.trim(),
                            notes = notes.trim(),
                            nextDueDate = nextDueDate.trim(),
                            nextDueMaintenance = nextDueMaintenance.trim()
                        )
                        onSave(m)
                    },
                    enabled = name.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_machine_button")
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (machineToEdit == null) "Gerät anlegen" else "Änderungen speichern",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
