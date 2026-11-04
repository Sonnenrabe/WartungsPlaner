package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Machine
import com.example.data.model.MaintenanceEntry
import com.example.data.model.MaintenanceTask
import com.example.ui.components.CompleteTaskDialog
import com.example.ui.components.MaintenanceDetailDialog
import com.example.ui.components.MaintenanceTaskDialog
import com.example.ui.components.calculateDueStatus
import com.example.ui.components.getCategoryColor
import com.example.ui.components.getCategoryIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachineDetailScreen(
    machine: Machine,
    maintenances: List<MaintenanceEntry>,
    defaultPerformerName: String,
    onBack: () -> Unit,
    onEditMachine: () -> Unit,
    onDeleteMachine: () -> Unit,
    onUpdateNotes: (String) -> Unit,
    onAddTask: (MaintenanceTask) -> Unit,
    onUpdateTask: (MaintenanceTask) -> Unit,
    onDeleteTask: (String) -> Unit,
    onCompleteTask: (task: MaintenanceTask, completedDate: String, performedBy: String, notes: String, nextScheduledDate: String?, nextTaskTitle: String?) -> Unit,
    onAddMaintenanceManual: () -> Unit,
    onDeleteMaintenance: (String) -> Unit
) {
    BackHandler { onBack() }

    // Two distinct tabs: 0 = Wartungen, 1 = Notizen & Details
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    var showDeleteMachineConfirm by remember { mutableStateOf(false) }
    var maintenanceToDelete by remember { mutableStateOf<MaintenanceEntry?>(null) }
    var taskToDelete by remember { mutableStateOf<MaintenanceTask?>(null) }

    // Clicked maintenance to view all details without having to edit
    var viewingMaintenanceEntry by remember { mutableStateOf<MaintenanceEntry?>(null) }

    var showTaskDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<MaintenanceTask?>(null) }
    var taskToComplete by remember { mutableStateOf<MaintenanceTask?>(null) }

    // Giant text field state for Tab 1 (Notizen)
    var noteText by remember(machine.notes) { mutableStateOf(machine.notes) }
    val hasUnsavedNoteChanges = remember(noteText, machine.notes) { noteText != machine.notes }

    // Derive active tasks: if machine has explicit tasks list, use that.
    val effectiveTasks: List<MaintenanceTask> = remember(machine) {
        if (machine.tasks.isNotEmpty()) {
            machine.tasks
        } else if (machine.nextDueDate.isNotBlank()) {
            listOf(
                MaintenanceTask(
                    id = "initial_task",
                    title = machine.nextDueMaintenance.ifBlank { "Nächste Wartung" },
                    dueDate = machine.nextDueDate,
                    intervalMonths = 12L
                )
            )
        } else {
            emptyList()
        }
    }

    val overallDueInfo = calculateDueStatus(machine.nextDueDate)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = machine.name,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Zurück"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onEditMachine, modifier = Modifier.testTag("edit_machine_button")) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Gerät bearbeiten")
                        }
                        IconButton(
                            onClick = { showDeleteMachineConfirm = true },
                            modifier = Modifier.testTag("delete_machine_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Gerät löschen",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // TabRow (Reiter) inside the device
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("machine_detail_tab_row")
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = {
                            Text(
                                text = "Wartungen (${effectiveTasks.size})",
                                fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Icon(imageVector = Icons.Default.Build, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        modifier = Modifier.testTag("tab_wartungen")
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = {
                            Text(
                                text = "Notizen & Details",
                                fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Icon(imageVector = Icons.Default.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        modifier = Modifier.testTag("tab_notizen")
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTabIndex == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        taskToEdit = null
                        showTaskDialog = true
                    },
                    icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                    text = { Text("Aufgabe hinzufügen") },
                    modifier = Modifier.testTag("detail_add_task_fab")
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTabIndex) {
                // TAB 0: WARTUNGEN & WARTUNGSPLAN
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Quick Machine Header Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(getCategoryColor(machine.category).copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = getCategoryIcon(machine.category),
                                                contentDescription = null,
                                                tint = getCategoryColor(machine.category),
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = machine.name,
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Text(
                                                        text = machine.groupTag,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = machine.category,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (machine.nextDueDate.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = overallDueInfo.backgroundColor
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = overallDueInfo.icon,
                                                        contentDescription = null,
                                                        tint = overallDueInfo.contentColor,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = machine.nextDueDate,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = overallDueInfo.contentColor
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (machine.location.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Place,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Standort: ${machine.location}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Section 1: Anstehende Aufgaben (Wartungsplan)
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Anstehende Aufgaben (${effectiveTasks.size})",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        taskToEdit = null
                                        showTaskDialog = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("add_task_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Aufgabe", fontSize = 12.sp)
                                }
                            }
                        }

                        if (effectiveTasks.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Für dieses Gerät sind noch keine anstehenden Aufgaben angelegt.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = {
                                                taskToEdit = null
                                                showTaskDialog = true
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("+ Erste Wartungsaufgabe anlegen")
                                        }
                                    }
                                }
                            }
                        } else {
                            items(effectiveTasks) { task ->
                                val taskDueInfo = calculateDueStatus(task.dueDate)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("task_card_${task.id}"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = taskDueInfo.backgroundColor
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = taskDueInfo.icon,
                                                        contentDescription = null,
                                                        tint = taskDueInfo.contentColor,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = taskDueInfo.statusText,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = taskDueInfo.contentColor
                                                    )
                                                }
                                            }

                                            Row {
                                                IconButton(
                                                    onClick = {
                                                        taskToEdit = task
                                                        showTaskDialog = true
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Aufgabe bearbeiten",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { taskToDelete = task },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Aufgabe löschen",
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = task.title,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        if (task.intervalMonths > 0 || task.notes.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (task.intervalMonths > 0) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.Repeat,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Alle ${task.intervalMonths} Monate",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }

                                                if (task.notes.isNotBlank()) {
                                                    Text(
                                                        text = "• ${task.notes}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Button(
                                            onClick = { taskToComplete = task },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("complete_task_button_${task.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Als erledigt eintragen & Folgetermin planen")
                                        }
                                    }
                                }
                            }
                        }

                        // Section 2: Wartungshistorie
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Wartungshistorie (${maintenances.size})",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                TextButton(
                                    onClick = onAddMaintenanceManual,
                                    modifier = Modifier.testTag("manual_add_history_button")
                                ) {
                                    Text("+ Wartung dokumentieren", fontSize = 12.sp)
                                }
                            }
                        }

                        if (maintenances.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Bisher noch keine durchgeführten Wartungen eingetragen.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(maintenances) { entry ->
                                // CLICKABLE CARD TO VIEW ALL DETAILS (Point 1 fix!)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewingMaintenanceEntry = entry }
                                        .testTag("maintenance_entry_${entry.id}"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = entry.completedDate,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Text(
                                                        text = entry.type,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = { maintenanceToDelete = entry },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Löschen",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = entry.title,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        if (entry.performedBy.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Durchgeführt von: ${entry.performedBy}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        // PROMINENT FREITEXT-NOTIZ DER WARTUNG (z.B. welches Öl und wie viel verwendet wurde)
                                        if (entry.notes.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Description,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .padding(top = 2.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            text = "Wartungs-Notiz / Details (Tippen für Vollbild):",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = entry.notes,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 3
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (entry.nextScheduledDate.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Repeat,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Folgetermin: ${entry.nextScheduledDate}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(56.dp))
                        }
                    }
                }

                // TAB 1: RIESIGES FREIES TEXTFELD (Point 2 & 3 fix!)
                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .imePadding()
                            .navigationBarsPadding()
                    ) {
                        // Sleek top action bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Freitext-Notizen",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Button(
                                onClick = {
                                    onUpdateNotes(noteText.trim())
                                },
                                shape = RoundedCornerShape(10.dp),
                                enabled = hasUnsavedNoteChanges,
                                modifier = Modifier.testTag("save_notes_tab_button")
                            ) {
                                Icon(
                                    imageVector = if (hasUnsavedNoteChanges) Icons.Default.Save else Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (hasUnsavedNoteChanges) "Speichern" else "Gespeichert")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // GIANT FULL-SCREEN SCROLLABLE TEXT FIELD
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            placeholder = {
                                Text(
                                    "Schreibe hier alle wichtigen Notizen und Details zu diesem Gerät nieder...\n\n" +
                                    "z.B.:\n" +
                                    "• Motoröl: 5W-30 LL-04 (Füllmenge: 4,3 Liter)\n" +
                                    "• Winterreifen: DOT 3821, 205/55 R16 (6 mm Profil)\n" +
                                    "• Sommerreifen: DOT 1522, 225/45 R17\n" +
                                    "• Reifendruck: 2,3 bar vorn / 2,5 bar hinten\n" +
                                    "• Batterie: 12V 70Ah AGM (Kaufdatum: Okt 2023)\n" +
                                    "• Zündkerzen: Bosch FR7DC+\n" +
                                    "• Filtermaße: MANN HU 711/51x\n" +
                                    "• Weitere Besonderheiten..."
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("giant_notes_input")
                        )
                    }
                }
            }
        }
    }

    // Dialog: Full Maintenance Details on click (Point 1 fix!)
    if (viewingMaintenanceEntry != null) {
        MaintenanceDetailDialog(
            entry = viewingMaintenanceEntry!!,
            onDismiss = { viewingMaintenanceEntry = null },
            onDelete = {
                val id = viewingMaintenanceEntry!!.id
                viewingMaintenanceEntry = null
                onDeleteMaintenance(id)
            }
        )
    }

    // Dialog: Create or Edit Maintenance Task
    if (showTaskDialog) {
        MaintenanceTaskDialog(
            taskToEdit = taskToEdit,
            onDismiss = {
                showTaskDialog = false
                taskToEdit = null
            },
            onSave = { task ->
                showTaskDialog = false
                if (taskToEdit == null) {
                    onAddTask(task)
                } else {
                    onUpdateTask(task)
                }
                taskToEdit = null
            }
        )
    }

    // Dialog: Complete Task & Reschedule
    if (taskToComplete != null) {
        CompleteTaskDialog(
            task = taskToComplete!!,
            machine = machine,
            defaultPerformerName = defaultPerformerName,
            onDismiss = { taskToComplete = null },
            onComplete = { completedDate, performedBy, notes, nextScheduledDate, nextTaskTitle ->
                val currentTask = taskToComplete!!
                taskToComplete = null
                onCompleteTask(currentTask, completedDate, performedBy, notes, nextScheduledDate, nextTaskTitle)
            }
        )
    }

    // Dialog: Delete Machine
    if (showDeleteMachineConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteMachineConfirm = false },
            title = { Text("Gerät löschen?") },
            text = { Text("Möchtest du '${machine.name}' wirklich entfernen?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteMachineConfirm = false
                        onDeleteMachine()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMachineConfirm = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // Dialog: Delete Task
    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Aufgabe löschen?") },
            text = { Text("Möchtest du die Wartungsaufgabe '${taskToDelete!!.title}' entfernen?") },
            confirmButton = {
                Button(
                    onClick = {
                        val taskId = taskToDelete!!.id
                        taskToDelete = null
                        onDeleteTask(taskId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // Dialog: Delete Maintenance Entry from History
    if (maintenanceToDelete != null) {
        AlertDialog(
            onDismissRequest = { maintenanceToDelete = null },
            title = { Text("Wartungseintrag löschen?") },
            text = { Text("Möchtest du den Eintrag '${maintenanceToDelete!!.title}' vom ${maintenanceToDelete!!.completedDate} entfernen?") },
            confirmButton = {
                Button(
                    onClick = {
                        val id = maintenanceToDelete!!.id
                        maintenanceToDelete = null
                        onDeleteMaintenance(id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { maintenanceToDelete = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
