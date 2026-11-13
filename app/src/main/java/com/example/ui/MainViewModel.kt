package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.FamilyGroup
import com.example.data.model.Machine
import com.example.data.model.MaintenanceEntry
import com.example.data.model.UserProfile
import com.example.data.repository.MaintenanceRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class SortOption(val label: String) {
    NEXT_DUE("Nächste Wartung (Dringend)"),
    NAME("Gerätename (A - Z)"),
    CATEGORY("Wartungsart / Kategorie"),
    LAST_MAINTENANCE("Zuletzt gewartet")
}

enum class StatusFilter(val label: String) {
    ALL("Alle Status"),
    OVERDUE("Nur Überfällige"),
    DUE_SOON("Fällig in 30 Tagen")
}

data class UiNotification(
    val message: String,
    val isError: Boolean = false
)

class MainViewModel(private val repository: MaintenanceRepository) : ViewModel() {

    private val auth = Firebase.auth
    val currentUser: FirebaseUser? get() = auth.currentUser

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _userGroups = MutableStateFlow<List<FamilyGroup>>(emptyList())
    val userGroups: StateFlow<List<FamilyGroup>> = _userGroups.asStateFlow()

    private val _activeGroup = MutableStateFlow<FamilyGroup?>(null)
    val activeGroup: StateFlow<FamilyGroup?> = _activeGroup.asStateFlow()

    private val _machines = MutableStateFlow<List<Machine>>(emptyList())
    val rawMachines: StateFlow<List<Machine>> = _machines.asStateFlow()

    private val _maintenances = MutableStateFlow<List<MaintenanceEntry>>(emptyList())
    val rawMaintenances: StateFlow<List<MaintenanceEntry>> = _maintenances.asStateFlow()

    // Filter & Sorting state
    val searchQuery = MutableStateFlow("")
    val selectedGroupTag = MutableStateFlow<String?>("Alle")
    val selectedSortOption = MutableStateFlow(SortOption.NEXT_DUE)
    val selectedStatusFilter = MutableStateFlow(StatusFilter.ALL)
    val isGroupedView = MutableStateFlow(true)

    // Navigation and detail view
    val selectedMachineId = MutableStateFlow<String?>(null)

    // Dialog state
    val showMachineDialog = MutableStateFlow(false)
    val editingMachine = MutableStateFlow<Machine?>(null)

    val showMaintenanceDialog = MutableStateFlow(false)
    val preselectedMachineForMaintenance = MutableStateFlow<Machine?>(null)
    val editingMaintenance = MutableStateFlow<MaintenanceEntry?>(null)

    val showGroupDialog = MutableStateFlow(false)
    val isGroupActionLoading = MutableStateFlow(false)
    val groupActionError = MutableStateFlow<String?>(null)
    val showManageDeviceGroupsDialog = MutableStateFlow(false)
    val showBackupDialog = MutableStateFlow(false)

    val notification = MutableStateFlow<UiNotification?>(null)
    val isLoading = MutableStateFlow(false)

    // Sync state & settings (Point 5)
    val lastSyncTime = MutableStateFlow(System.currentTimeMillis())
    val isSyncing = MutableStateFlow(false)
    val syncMode = MutableStateFlow("BEI_START_UND_STANDBY") // "BEI_START_UND_STANDBY", "ECHTZEIT", "NUR_MANUELL"

    fun openGroupDialog() {
        groupActionError.value = null
        showGroupDialog.value = true
    }

    fun dismissGroupDialog() {
        if (!isGroupActionLoading.value) {
            groupActionError.value = null
            showGroupDialog.value = false
        }
    }

    fun clearGroupActionError() {
        groupActionError.value = null
    }

    fun triggerSync(manualClick: Boolean = false) {
        viewModelScope.launch {
            isSyncing.value = true
            val groupId = _activeGroup.value?.id
            if (!groupId.isNullOrBlank()) {
                switchActiveGroup(groupId, persistToProfile = false)
            } else {
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    initUserData(uid)
                }
            }
            kotlinx.coroutines.delay(350)
            lastSyncTime.value = System.currentTimeMillis()
            isSyncing.value = false
            if (manualClick) {
                notification.value = UiNotification("Datenbank erfolgreich synchronisiert!")
            }
        }
    }

    private var userProfileObservationJob: Job? = null
    private var userGroupsObservationJob: Job? = null
    private var activeGroupObservationJob: Job? = null
    private var machinesObservationJob: Job? = null
    private var maintenancesObservationJob: Job? = null

    init {
        val user = auth.currentUser
        if (user != null) {
            initUserData(user.uid)
        }
    }

    fun initUserData(uid: String) {
        userProfileObservationJob?.cancel()
        userGroupsObservationJob?.cancel()

        // Ensure initial user profile exists without overwriting an existing custom displayName
        viewModelScope.launch {
            try {
                repository.ensureUserProfileExists(uid)
            } catch (e: Exception) {
                Log.w("MainViewModel", "Auto-init user profile: ${e.message}")
            }
        }

        userProfileObservationJob = viewModelScope.launch {
            repository.observeUserProfile(uid)
                .catch { e -> Log.e("MainViewModel", "observeUserProfile error: ${e.message}", e) }
                .collect { profile ->
                    _userProfile.value = profile
                    if (profile != null &&
                        profile.activeGroupId.isNotBlank() &&
                        _activeGroup.value?.id != profile.activeGroupId &&
                        _userGroups.value.any { it.id == profile.activeGroupId }
                    ) {
                        switchActiveGroup(profile.activeGroupId, persistToProfile = false)
                    }
                }
        }

        userGroupsObservationJob = viewModelScope.launch {
            repository.observeUserGroups(uid)
                .catch { e -> Log.e("MainViewModel", "observeUserGroups error: ${e.message}", e) }
                .collect { groups ->
                    _userGroups.value = groups
                    if (groups.isEmpty()) {
                        activeGroupObservationJob?.cancel()
                        machinesObservationJob?.cancel()
                        maintenancesObservationJob?.cancel()
                        _activeGroup.value = null
                        _machines.value = emptyList()
                        _maintenances.value = emptyList()
                    } else {
                        val currentActiveId = _activeGroup.value?.id
                        if (currentActiveId == null || groups.none { it.id == currentActiveId }) {
                            val profileActiveId = _userProfile.value?.activeGroupId
                            val targetGroupId = profileActiveId?.takeIf { id -> groups.any { it.id == id } }
                                ?: groups.first().id
                            switchActiveGroup(
                                groupId = targetGroupId,
                                persistToProfile = targetGroupId != profileActiveId
                            )
                        }
                    }
                }
        }
    }

    fun switchActiveGroup(groupId: String, persistToProfile: Boolean = true) {
        activeGroupObservationJob?.cancel()
        machinesObservationJob?.cancel()
        maintenancesObservationJob?.cancel()

        activeGroupObservationJob = viewModelScope.launch {
            repository.observeGroup(groupId)
                .catch { e -> Log.e("MainViewModel", "observeGroup error: ${e.message}", e) }
                .collect { group ->
                    _activeGroup.value = group
                }
        }

        machinesObservationJob = viewModelScope.launch {
            repository.observeMachines(groupId)
                .catch { e -> Log.e("MainViewModel", "observeMachines error: ${e.message}", e) }
                .collect { list ->
                    _machines.value = list
                }
        }

        maintenancesObservationJob = viewModelScope.launch {
            repository.observeMaintenances(groupId)
                .catch { e -> Log.e("MainViewModel", "observeMaintenances error: ${e.message}", e) }
                .collect { list ->
                    _maintenances.value = list.sortedWith { a, b ->
                        when {
                            a.completedDate.isBlank() && b.completedDate.isBlank() -> a.title.compareTo(b.title, ignoreCase = true)
                            a.completedDate.isBlank() -> 1
                            b.completedDate.isBlank() -> -1
                            else -> b.completedDate.compareTo(a.completedDate)
                        }
                    }
                }
        }

        if (persistToProfile) {
            viewModelScope.launch {
                try {
                    repository.setActiveGroupId(groupId)
                } catch (e: Exception) {
                    Log.w("MainViewModel", "Persist active group in profile error: ${e.message}")
                }
            }
        }
    }

    private fun formatGroupError(prefix: String, e: Throwable): String {
        val msg = e.localizedMessage ?: "Unbekannter Fehler"
        return if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
            "$prefix: Keine Berechtigung oder fehlende Verbindung zu Firebase ($msg)."
        } else {
            msg
        }
    }

    fun createNewGroup(name: String, code: String?) {
        viewModelScope.launch {
            isGroupActionLoading.value = true
            groupActionError.value = null
            isLoading.value = true
            val result = repository.createGroup(name, code)
            isLoading.value = false
            isGroupActionLoading.value = false
            result.onSuccess { created ->
                switchActiveGroup(created.id, persistToProfile = false)
                showGroupDialog.value = false
                groupActionError.value = null
                notification.value = UiNotification("Gruppe '${created.name}' mit Code ${created.code} erstellt!")
            }.onFailure { e ->
                val errorText = formatGroupError("Fehler beim Erstellen der Gruppe", e)
                groupActionError.value = errorText
                notification.value = UiNotification(errorText, isError = true)
            }
        }
    }

    fun joinGroupByCode(code: String) {
        viewModelScope.launch {
            isGroupActionLoading.value = true
            groupActionError.value = null
            isLoading.value = true
            val result = repository.joinGroupByCode(code)
            isLoading.value = false
            isGroupActionLoading.value = false
            result.onSuccess { joined ->
                switchActiveGroup(joined.id, persistToProfile = false)
                showGroupDialog.value = false
                groupActionError.value = null
                notification.value = UiNotification("Erfolgreich der Gruppe '${joined.name}' beigetreten!")
            }.onFailure { e ->
                val errorText = formatGroupError("Beitritt fehlgeschlagen", e)
                groupActionError.value = errorText
                notification.value = UiNotification(errorText, isError = true)
            }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            isGroupActionLoading.value = true
            groupActionError.value = null
            isLoading.value = true
            val result = repository.deleteGroup(groupId)
            isLoading.value = false
            isGroupActionLoading.value = false
            result.onSuccess {
                notification.value = UiNotification("Gruppe erfolgreich gelöscht.")
                if (_activeGroup.value?.id == groupId) {
                    val nextGroup = _userGroups.value.firstOrNull { it.id != groupId }
                    if (nextGroup != null) {
                        switchActiveGroup(nextGroup.id)
                    } else {
                        activeGroupObservationJob?.cancel()
                        machinesObservationJob?.cancel()
                        maintenancesObservationJob?.cancel()
                        _activeGroup.value = null
                        _machines.value = emptyList()
                        _maintenances.value = emptyList()
                    }
                }
            }.onFailure { e ->
                val errorText = formatGroupError("Fehler beim Löschen", e)
                groupActionError.value = errorText
                notification.value = UiNotification(errorText, isError = true)
            }
        }
    }

    fun leaveGroup(groupId: String) {
        viewModelScope.launch {
            isGroupActionLoading.value = true
            groupActionError.value = null
            isLoading.value = true
            val result = repository.leaveGroup(groupId)
            isLoading.value = false
            isGroupActionLoading.value = false
            result.onSuccess {
                notification.value = UiNotification("Gruppe verlassen.")
                if (_activeGroup.value?.id == groupId) {
                    val nextGroup = _userGroups.value.firstOrNull { it.id != groupId }
                    if (nextGroup != null) {
                        switchActiveGroup(nextGroup.id)
                    } else {
                        activeGroupObservationJob?.cancel()
                        machinesObservationJob?.cancel()
                        maintenancesObservationJob?.cancel()
                        _activeGroup.value = null
                        _machines.value = emptyList()
                        _maintenances.value = emptyList()
                    }
                }
            }.onFailure { e ->
                val errorText = formatGroupError("Fehler beim Verlassen", e)
                groupActionError.value = errorText
                notification.value = UiNotification(errorText, isError = true)
            }
        }
    }

    fun updateDisplayName(newName: String) {
        val uid = auth.currentUser?.uid ?: return
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            isLoading.value = true
            val current = _userProfile.value ?: UserProfile(userId = uid, email = auth.currentUser?.email ?: "")
            val updated = current.copy(displayName = cleanName)
            val result = repository.saveUserProfile(updated)
            isLoading.value = false
            result.onSuccess {
                notification.value = UiNotification("Dein Name wurde in '$cleanName' geändert!")
            }.onFailure { e ->
                notification.value = UiNotification("Fehler beim Speichern: ${e.localizedMessage}", isError = true)
            }
        }
    }

    fun addDeviceGroup(name: String) {
        val groupId = _activeGroup.value?.id ?: return
        viewModelScope.launch {
            isLoading.value = true
            val result = repository.addDeviceGroup(groupId, name)
            isLoading.value = false
            result.onSuccess {
                notification.value = UiNotification("Gruppe '$name' hinzugefügt.")
            }.onFailure { e ->
                notification.value = UiNotification("Fehler: ${e.localizedMessage}", isError = true)
            }
        }
    }

    fun renameDeviceGroup(oldName: String, newName: String) {
        val groupId = _activeGroup.value?.id ?: return
        viewModelScope.launch {
            isLoading.value = true
            val result = repository.renameDeviceGroup(groupId, oldName, newName)
            isLoading.value = false
            result.onSuccess {
                if (selectedGroupTag.value == oldName) {
                    selectedGroupTag.value = newName
                }
                notification.value = UiNotification("Gruppe in '$newName' umbenannt.")
            }.onFailure { e ->
                notification.value = UiNotification("Fehler: ${e.localizedMessage}", isError = true)
            }
        }
    }

    fun deleteDeviceGroup(name: String) {
        val groupId = _activeGroup.value?.id ?: return
        viewModelScope.launch {
            isLoading.value = true
            val result = repository.deleteDeviceGroup(groupId, name)
            isLoading.value = false
            result.onSuccess {
                if (selectedGroupTag.value == name) {
                    selectedGroupTag.value = "Alle"
                }
                notification.value = UiNotification("Gruppe '$name' gelöscht.")
            }.onFailure { e ->
                notification.value = UiNotification("Fehler: ${e.localizedMessage}", isError = true)
            }
        }
    }

    fun saveMachine(machine: Machine) {
        val groupId = _activeGroup.value?.id
        if (groupId.isNullOrBlank()) {
            notification.value = UiNotification("Bitte wähle zuerst eine Gruppe aus.", isError = true)
            return
        }
        viewModelScope.launch {
            isLoading.value = true
            val result = repository.saveMachine(groupId, machine)
            isLoading.value = false
            result.onSuccess {
                showMachineDialog.value = false
                editingMachine.value = null
                notification.value = UiNotification("Gerät '${machine.name}' gespeichert.")
            }.onFailure { e ->
                notification.value = UiNotification("Fehler: ${e.localizedMessage}", isError = true)
            }
        }
    }

    fun updateMachineNotes(machineId: String, notes: String) {
        val groupId = _activeGroup.value?.id ?: return
        viewModelScope.launch {
            isLoading.value = true
            val result = repository.updateMachineNotes(groupId, machineId, notes)
            isLoading.value = false
            result.onSuccess {
                notification.value = UiNotification("Notizen für Gerät gespeichert.")
            }.onFailure { e ->
                notification.value = UiNotification("Fehler: ${e.localizedMessage}", isError = true)
            }
        }
    }

    private fun sortTasksByDueDate(tasks: List<com.example.data.model.MaintenanceTask>): List<com.example.data.model.MaintenanceTask> {
        return tasks.sortedWith { a, b ->
            when {
                a.dueDate.isBlank() && b.dueDate.isBlank() -> a.title.compareTo(b.title, ignoreCase = true)
                a.dueDate.isBlank() -> 1
                b.dueDate.isBlank() -> -1
                else -> a.dueDate.compareTo(b.dueDate)
            }
        }
    }

    fun addTaskToMachine(machineId: String, task: com.example.data.model.MaintenanceTask) {
        val currentMachine = _machines.value.find { it.id == machineId } ?: return
        val updatedTasks = sortTasksByDueDate(currentMachine.tasks + task)
        val (earliestDate, earliestTask) = calculateEarliestTask(updatedTasks)

        val updatedMachine = currentMachine.copy(
            tasks = updatedTasks,
            nextDueDate = earliestDate ?: currentMachine.nextDueDate,
            nextDueMaintenance = earliestTask ?: currentMachine.nextDueMaintenance
        )
        saveMachine(updatedMachine)
    }

    fun updateTaskOnMachine(machineId: String, task: com.example.data.model.MaintenanceTask) {
        val currentMachine = _machines.value.find { it.id == machineId } ?: return
        val updatedTasks = sortTasksByDueDate(currentMachine.tasks.map { if (it.id == task.id) task else it })
        val (earliestDate, earliestTask) = calculateEarliestTask(updatedTasks)

        val updatedMachine = currentMachine.copy(
            tasks = updatedTasks,
            nextDueDate = earliestDate ?: "",
            nextDueMaintenance = earliestTask ?: ""
        )
        saveMachine(updatedMachine)
    }

    fun deleteTaskFromMachine(machineId: String, taskId: String) {
        val currentMachine = _machines.value.find { it.id == machineId } ?: return
        val updatedTasks = sortTasksByDueDate(currentMachine.tasks.filter { it.id != taskId })
        val (earliestDate, earliestTask) = calculateEarliestTask(updatedTasks)

        val updatedMachine = currentMachine.copy(
            tasks = updatedTasks,
            nextDueDate = earliestDate ?: "",
            nextDueMaintenance = earliestTask ?: ""
        )
        saveMachine(updatedMachine)
    }

    fun completeTask(
        machineId: String,
        task: com.example.data.model.MaintenanceTask,
        completedDate: String,
        performedBy: String,
        notes: String,
        nextScheduledDate: String?,
        nextTaskTitle: String?
    ) {
        val groupId = _activeGroup.value?.id ?: return
        val currentMachine = _machines.value.find { it.id == machineId } ?: return

        val entry = MaintenanceEntry(
            groupId = groupId,
            machineId = machineId,
            machineName = currentMachine.name,
            title = task.title,
            type = "Wartung",
            completedDate = completedDate,
            nextScheduledDate = nextScheduledDate ?: "",
            performedBy = performedBy,
            cost = 0.0,
            notes = notes
        )

        val updatedTasks = if (!nextScheduledDate.isNullOrBlank()) {
            currentMachine.tasks.map {
                if (it.id == task.id) {
                    it.copy(
                        title = nextTaskTitle ?: it.title,
                        dueDate = nextScheduledDate,
                        isCompleted = false
                    )
                } else it
            }
        } else {
            currentMachine.tasks.filter { it.id != task.id }
        }

        val (earliestDate, earliestTask) = calculateEarliestTask(updatedTasks)

        val updatedMachine = currentMachine.copy(
            tasks = updatedTasks,
            lastMaintenanceDate = completedDate,
            nextDueDate = earliestDate ?: "",
            nextDueMaintenance = earliestTask ?: ""
        )

        viewModelScope.launch {
            isLoading.value = true
            repository.saveMaintenance(groupId, entry, null, null)
            repository.saveMachine(groupId, updatedMachine)
            isLoading.value = false
            notification.value = UiNotification("Wartung '${task.title}' als erledigt protokolliert!")
        }
    }

    fun recordCompletedMaintenanceDirectly(
        machineId: String,
        title: String,
        completedDate: String,
        performedBy: String,
        notes: String,
        rescheduleDate: String?,
        intervalMonths: Long
    ) {
        val groupId = _activeGroup.value?.id ?: return
        val currentMachine = _machines.value.find { it.id == machineId } ?: return

        val entry = MaintenanceEntry(
            groupId = groupId,
            machineId = machineId,
            machineName = currentMachine.name,
            title = title.trim(),
            type = "Wartung",
            completedDate = completedDate.trim(),
            nextScheduledDate = rescheduleDate?.trim() ?: "",
            performedBy = performedBy.trim(),
            cost = 0.0,
            notes = notes.trim()
        )

        val updatedTasks = if (!rescheduleDate.isNullOrBlank()) {
            val nextTask = com.example.data.model.MaintenanceTask(
                id = java.util.UUID.randomUUID().toString(),
                title = title.trim(),
                dueDate = rescheduleDate.trim(),
                intervalMonths = intervalMonths,
                notes = "",
                isCompleted = false
            )
            currentMachine.tasks + nextTask
        } else {
            currentMachine.tasks
        }

        val (earliestDate, earliestTask) = calculateEarliestTask(updatedTasks)

        val updatedMachine = currentMachine.copy(
            tasks = updatedTasks,
            lastMaintenanceDate = completedDate.trim(),
            nextDueDate = earliestDate ?: currentMachine.nextDueDate,
            nextDueMaintenance = earliestTask ?: currentMachine.nextDueMaintenance
        )

        viewModelScope.launch {
            isLoading.value = true
            repository.saveMaintenance(groupId, entry, null, null)
            repository.saveMachine(groupId, updatedMachine)
            isLoading.value = false
            notification.value = UiNotification("Erledigte Wartung '$title' erfolgreich dokumentiert!")
        }
    }

    private fun calculateEarliestTask(tasks: List<com.example.data.model.MaintenanceTask>): Pair<String?, String?> {
        val pending = tasks.filter { !it.isCompleted && it.dueDate.isNotBlank() }
            .sortedBy { it.dueDate }
        val earliest = pending.firstOrNull()
        return Pair(earliest?.dueDate, earliest?.title)
    }

    fun deleteMachine(machineId: String) {
        val groupId = _activeGroup.value?.id ?: return
        viewModelScope.launch {
            isLoading.value = true
            val result = repository.deleteMachine(groupId, machineId)
            isLoading.value = false
            result.onSuccess {
                if (selectedMachineId.value == machineId) {
                    selectedMachineId.value = null
                }
                notification.value = UiNotification("Gerät gelöscht.")
            }.onFailure { e ->
                notification.value = UiNotification("Fehler: ${e.localizedMessage}", isError = true)
            }
        }
    }

    fun saveMaintenance(
        entry: MaintenanceEntry,
        rescheduleDate: String?,
        rescheduleTask: String?
    ) {
        val groupId = _activeGroup.value?.id ?: return
        viewModelScope.launch {
            isLoading.value = true
            val result = repository.saveMaintenance(groupId, entry, rescheduleDate, rescheduleTask)
            isLoading.value = false
            result.onSuccess {
                showMaintenanceDialog.value = false
                preselectedMachineForMaintenance.value = null
                editingMaintenance.value = null
                val msg = if (!rescheduleDate.isNullOrBlank()) {
                    "Wartung dokumentiert und Folgetermin für $rescheduleDate terminiert!"
                } else {
                    "Wartung erfolgreich dokumentiert."
                }
                notification.value = UiNotification(msg)
            }.onFailure { e ->
                notification.value = UiNotification("Fehler: ${e.localizedMessage}", isError = true)
            }
        }
    }

    fun deleteMaintenance(maintenanceId: String) {
        val groupId = _activeGroup.value?.id ?: return
        viewModelScope.launch {
            val result = repository.deleteMaintenance(groupId, maintenanceId)
            result.onSuccess {
                notification.value = UiNotification("Wartungseintrag gelöscht.")
            }.onFailure { e ->
                notification.value = UiNotification("Fehler: ${e.localizedMessage}", isError = true)
            }
        }
    }

    fun exportBackup(onResult: (String?) -> Unit) {
        val groupId = _activeGroup.value?.id ?: return
        viewModelScope.launch {
            isLoading.value = true
            val result = repository.exportBackup(groupId)
            isLoading.value = false
            result.onSuccess { json ->
                onResult(json)
            }.onFailure { e ->
                notification.value = UiNotification("Export fehlgeschlagen: ${e.localizedMessage}", isError = true)
                onResult(null)
            }
        }
    }

    fun importBackup(jsonString: String, onComplete: (Boolean) -> Unit) {
        val groupId = _activeGroup.value?.id ?: return
        viewModelScope.launch {
            isLoading.value = true
            val result = repository.importBackup(groupId, jsonString)
            isLoading.value = false
            result.onSuccess { (machines, maints) ->
                notification.value = UiNotification("Backup erfolgreich importiert: $machines Geräte, $maints Wartungen!")
                onComplete(true)
            }.onFailure { e ->
                notification.value = UiNotification("Import fehlgeschlagen: ${e.localizedMessage}", isError = true)
                onComplete(false)
            }
        }
    }

    // Processed and filtered list of machines
    val filteredMachines: StateFlow<List<Machine>> = combine(
        _machines,
        searchQuery,
        selectedGroupTag,
        selectedSortOption,
        selectedStatusFilter
    ) { machines, query, groupTag, sortOption, statusFilter ->
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        machines.filter { machine ->
            // Search filter
            val matchesQuery = query.isBlank() ||
                    machine.name.contains(query, ignoreCase = true) ||
                    machine.category.contains(query, ignoreCase = true) ||
                    machine.groupTag.contains(query, ignoreCase = true) ||
                    machine.location.contains(query, ignoreCase = true) ||
                    machine.nextDueMaintenance.contains(query, ignoreCase = true)

            // Group tag filter
            val matchesTag = groupTag.isNullOrBlank() || groupTag == "Alle" || machine.groupTag.equals(groupTag, ignoreCase = true)

            // Status filter
            val matchesStatus = when (statusFilter) {
                StatusFilter.ALL -> true
                StatusFilter.OVERDUE -> {
                    val dueDate = parseDateSafely(machine.nextDueDate, formatter)
                    dueDate != null && dueDate.isBefore(today)
                }
                StatusFilter.DUE_SOON -> {
                    val dueDate = parseDateSafely(machine.nextDueDate, formatter)
                    dueDate != null && !dueDate.isBefore(today) && !dueDate.isAfter(today.plusDays(30))
                }
            }

            matchesQuery && matchesTag && matchesStatus
        }.sortedWith { a, b ->
            when (sortOption) {
                SortOption.NEXT_DUE -> {
                    val dateA = parseDateSafely(a.nextDueDate, formatter)
                    val dateB = parseDateSafely(b.nextDueDate, formatter)
                    when {
                        dateA == null && dateB == null -> a.name.compareTo(b.name, ignoreCase = true)
                        dateA == null -> 1
                        dateB == null -> -1
                        else -> dateA.compareTo(dateB)
                    }
                }
                SortOption.NAME -> a.name.compareTo(b.name, ignoreCase = true)
                SortOption.CATEGORY -> {
                    val catComp = a.category.compareTo(b.category, ignoreCase = true)
                    if (catComp != 0) catComp else a.name.compareTo(b.name, ignoreCase = true)
                }
                SortOption.LAST_MAINTENANCE -> {
                    val dateA = parseDateSafely(a.lastMaintenanceDate, formatter)
                    val dateB = parseDateSafely(b.lastMaintenanceDate, formatter)
                    when {
                        dateA == null && dateB == null -> a.name.compareTo(b.name, ignoreCase = true)
                        dateA == null -> 1
                        dateB == null -> -1
                        else -> dateB.compareTo(dateA) // latest first
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Manually defined device groups for active family group
    val definedDeviceGroups: StateFlow<List<String>> = _activeGroup.map {
        it?.deviceGroups ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unique tags for filter chips: User-defined groups first, followed by any existing machine tags
    val availableGroupTags: StateFlow<List<String>> = combine(_machines, _activeGroup) { machines, group ->
        val defined = group?.deviceGroups ?: emptyList()
        val fromMachines = machines.map { it.groupTag.trim() }.filter { it.isNotBlank() && it != "Alle" }
        val allTags = (defined + fromMachines).distinct()
        listOf("Alle") + allTags
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Alle"))

    private fun parseDateSafely(dateStr: String?, formatter: DateTimeFormatter): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            LocalDate.parse(dateStr.trim(), formatter)
        } catch (e: Exception) {
            null
        }
    }

    class Factory(private val repository: MaintenanceRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository) as T
        }
    }
}
