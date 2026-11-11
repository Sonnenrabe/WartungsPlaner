package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.repository.MaintenanceRepository
import com.example.ui.MainViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.performSignOut
import com.example.ui.components.BackupDialog
import com.example.ui.components.GroupManagementDialog
import com.example.ui.components.MachineDialog
import com.example.ui.components.MaintenanceDialog
import com.example.ui.components.ManageDeviceGroupsDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.MachineDetailScreen
import com.example.ui.theme.WartungsPlanerTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WartungsPlanerTheme {
                RootAuthGate()
            }
        }
    }
}

@Composable
fun RootAuthGate() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = {},
                scope = coroutineScope
            )
        }
    }

    if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        MainAppContent(
            userId = currentUser!!.uid,
            onSignOut = {
                performSignOut(
                    context = context,
                    credentialManager = credentialManager,
                    scope = coroutineScope,
                    onSignOutComplete = { currentUser = null }
                )
            }
        )
    }
}

@Composable
fun MainAppContent(
    userId: String,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val repository = remember { MaintenanceRepository(context) }
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModel.Factory(repository)
    )

    val activeGroup by viewModel.activeGroup.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val userGroups by viewModel.userGroups.collectAsStateWithLifecycle()
    val machines by viewModel.filteredMachines.collectAsStateWithLifecycle()
    val rawMaintenances by viewModel.rawMaintenances.collectAsStateWithLifecycle()
    val availableGroupTags by viewModel.availableGroupTags.collectAsStateWithLifecycle()
    val definedDeviceGroups by viewModel.definedDeviceGroups.collectAsStateWithLifecycle()

    val lastSyncTime by viewModel.lastSyncTime.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMode by viewModel.syncMode.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedGroupTag by viewModel.selectedGroupTag.collectAsStateWithLifecycle()
    val selectedSortOption by viewModel.selectedSortOption.collectAsStateWithLifecycle()
    val selectedStatusFilter by viewModel.selectedStatusFilter.collectAsStateWithLifecycle()
    val isGroupedView by viewModel.isGroupedView.collectAsStateWithLifecycle()
    val selectedMachineId by viewModel.selectedMachineId.collectAsStateWithLifecycle()

    val showMachineDialog by viewModel.showMachineDialog.collectAsStateWithLifecycle()
    val editingMachine by viewModel.editingMachine.collectAsStateWithLifecycle()
    val showMaintenanceDialog by viewModel.showMaintenanceDialog.collectAsStateWithLifecycle()
    val preselectedMachineForMaintenance by viewModel.preselectedMachineForMaintenance.collectAsStateWithLifecycle()
    val editingMaintenance by viewModel.editingMaintenance.collectAsStateWithLifecycle()
    val showGroupDialog by viewModel.showGroupDialog.collectAsStateWithLifecycle()
    val showManageDeviceGroupsDialog by viewModel.showManageDeviceGroupsDialog.collectAsStateWithLifecycle()
    val showBackupDialog by viewModel.showBackupDialog.collectAsStateWithLifecycle()
    val notification by viewModel.notification.collectAsStateWithLifecycle()

    // Sync automatically on App-Start and when returning from standby (ON_RESUME)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.triggerSync(manualClick = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Show Snackbars for notifications
    LaunchedEffect(notification) {
        notification?.let {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(it.message)
                viewModel.notification.value = null
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val selectedMachine = machines.firstOrNull { it.id == selectedMachineId }
            if (selectedMachine != null) {
                val machineMaintenances = rawMaintenances
                    .filter { it.machineId == selectedMachine.id }
                    .sortedWith { a, b ->
                        when {
                            a.completedDate.isBlank() && b.completedDate.isBlank() -> a.title.compareTo(b.title, ignoreCase = true)
                            a.completedDate.isBlank() -> 1
                            b.completedDate.isBlank() -> -1
                            else -> b.completedDate.compareTo(a.completedDate)
                        }
                    }
                MachineDetailScreen(
                    machine = selectedMachine,
                    maintenances = machineMaintenances,
                    defaultPerformerName = userProfile?.displayName?.ifBlank { "Ich" } ?: "Ich",
                    onBack = { viewModel.selectedMachineId.value = null },
                    onEditMachine = {
                        viewModel.editingMachine.value = selectedMachine
                        viewModel.showMachineDialog.value = true
                    },
                    onDeleteMachine = {
                        viewModel.deleteMachine(selectedMachine.id)
                    },
                    onUpdateNotes = { updatedNotes ->
                        viewModel.updateMachineNotes(selectedMachine.id, updatedNotes)
                    },
                    onAddTask = { task ->
                        viewModel.addTaskToMachine(selectedMachine.id, task)
                    },
                    onUpdateTask = { task ->
                        viewModel.updateTaskOnMachine(selectedMachine.id, task)
                    },
                    onDeleteTask = { taskId ->
                        viewModel.deleteTaskFromMachine(selectedMachine.id, taskId)
                    },
                    onCompleteTask = { task, completedDate, performedBy, notes, nextScheduledDate, nextTaskTitle ->
                        viewModel.completeTask(
                            machineId = selectedMachine.id,
                            task = task,
                            completedDate = completedDate,
                            performedBy = performedBy,
                            notes = notes,
                            nextScheduledDate = nextScheduledDate,
                            nextTaskTitle = nextTaskTitle
                        )
                    },
                    onRecordCompletedMaintenance = { title, completedDate, performedBy, notes, rescheduleDate, intervalMonths ->
                        viewModel.recordCompletedMaintenanceDirectly(
                            machineId = selectedMachine.id,
                            title = title,
                            completedDate = completedDate,
                            performedBy = performedBy,
                            notes = notes,
                            rescheduleDate = rescheduleDate,
                            intervalMonths = intervalMonths
                        )
                    },
                    onAddMaintenanceManual = {
                        viewModel.editingMaintenance.value = null
                        viewModel.preselectedMachineForMaintenance.value = selectedMachine
                        viewModel.showMaintenanceDialog.value = true
                    },
                    onEditMaintenance = { entry ->
                        viewModel.editingMaintenance.value = entry
                        viewModel.preselectedMachineForMaintenance.value = machines.firstOrNull { it.id == entry.machineId } ?: selectedMachine
                        viewModel.showMaintenanceDialog.value = true
                    },
                    onDeleteMaintenance = { maintId ->
                        viewModel.deleteMaintenance(maintId)
                    }
                )
            } else {
                DashboardScreen(
                    activeGroup = activeGroup,
                    machines = machines,
                    userProfile = userProfile,
                    lastSyncTime = lastSyncTime,
                    isSyncing = isSyncing,
                    syncMode = syncMode,
                    onTriggerSync = { viewModel.triggerSync(manualClick = true) },
                    onSetSyncMode = { viewModel.syncMode.value = it },
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.searchQuery.value = it },
                    selectedGroupTag = selectedGroupTag,
                    onSelectGroupTag = { viewModel.selectedGroupTag.value = it },
                    availableGroupTags = availableGroupTags,
                    selectedSortOption = selectedSortOption,
                    onSelectSortOption = { viewModel.selectedSortOption.value = it },
                    selectedStatusFilter = selectedStatusFilter,
                    onSelectStatusFilter = { viewModel.selectedStatusFilter.value = it },
                    isGroupedView = isGroupedView,
                    onToggleGroupedView = { viewModel.isGroupedView.value = !isGroupedView },
                    onMachineClick = { machine ->
                        viewModel.selectedMachineId.value = machine.id
                    },
                    onAddMachineClick = {
                        viewModel.editingMachine.value = null
                        viewModel.showMachineDialog.value = true
                    },
                    onAddMaintenanceClick = { preselected ->
                        viewModel.preselectedMachineForMaintenance.value = preselected
                        viewModel.showMaintenanceDialog.value = true
                    },
                    onManageGroupClick = { viewModel.showGroupDialog.value = true },
                    onManageDeviceGroupsClick = { viewModel.showManageDeviceGroupsDialog.value = true },
                    onOpenBackupClick = { viewModel.showBackupDialog.value = true },
                    onUpdateDisplayName = { newName -> viewModel.updateDisplayName(newName) },
                    onSignOutClick = onSignOut
                )
            }
        }
    }

    // Machine Dialog
    if (showMachineDialog) {
        MachineDialog(
            machineToEdit = editingMachine,
            existingGroupTags = availableGroupTags,
            onDismiss = {
                viewModel.showMachineDialog.value = false
                viewModel.editingMachine.value = null
            },
            onSave = { machine ->
                viewModel.saveMachine(machine)
            },
            onAddNewGroupTag = { newTag ->
                viewModel.addDeviceGroup(newTag)
            }
        )
    }

    // Manage Device Groups Dialog
    if (showManageDeviceGroupsDialog) {
        ManageDeviceGroupsDialog(
            definedGroups = definedDeviceGroups,
            machines = machines,
            onAddGroup = { viewModel.addDeviceGroup(it) },
            onRenameGroup = { oldName, newName -> viewModel.renameDeviceGroup(oldName, newName) },
            onDeleteGroup = { viewModel.deleteDeviceGroup(it) },
            onDismiss = { viewModel.showManageDeviceGroupsDialog.value = false }
        )
    }

    // Maintenance Dialog
    if (showMaintenanceDialog) {
        MaintenanceDialog(
            machines = machines,
            preselectedMachine = preselectedMachineForMaintenance,
            defaultPerformerName = userProfile?.displayName?.ifBlank { "Ich" } ?: "Ich",
            entryToEdit = editingMaintenance,
            onDismiss = {
                viewModel.showMaintenanceDialog.value = false
                viewModel.preselectedMachineForMaintenance.value = null
                viewModel.editingMaintenance.value = null
            },
            onSave = { entry, rescheduleDate, rescheduleTask ->
                viewModel.saveMaintenance(entry, rescheduleDate, rescheduleTask)
            }
        )
    }

    // Group Management Dialog
    if (showGroupDialog) {
        GroupManagementDialog(
            activeGroup = activeGroup,
            userGroups = userGroups,
            currentUserId = viewModel.currentUser?.uid,
            onSelectGroup = { groupId ->
                viewModel.switchActiveGroup(groupId)
                viewModel.showGroupDialog.value = false
            },
            onCreateGroup = { name, code ->
                viewModel.createNewGroup(name, code)
            },
            onJoinGroup = { code ->
                viewModel.joinGroupByCode(code)
            },
            onDeleteGroup = { groupId ->
                viewModel.deleteGroup(groupId)
            },
            onLeaveGroup = { groupId ->
                viewModel.leaveGroup(groupId)
            },
            onDismiss = { viewModel.showGroupDialog.value = false }
        )
    }

    // Backup Export & Import Dialog
    if (showBackupDialog) {
        BackupDialog(
            onExport = { callback ->
                viewModel.exportBackup(callback)
            },
            onImport = { json ->
                viewModel.importBackup(json) { }
            },
            onDismiss = { viewModel.showBackupDialog.value = false }
        )
    }
}
