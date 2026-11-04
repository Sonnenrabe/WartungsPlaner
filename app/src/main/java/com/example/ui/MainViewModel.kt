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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    val showGroupDialog = MutableStateFlow(false)
    val showManageDeviceGroupsDialog = MutableStateFlow(false)
    val showBackupDialog = MutableStateFlow(false)

    val notification = MutableStateFlow<UiNotification?>(null)
    val isLoading = MutableStateFlow(false)

    // Sync state & settings (Point 5)
    val lastSyncTime = MutableStateFlow(System.currentTimeMillis())
    val isSyncing = MutableStateFlow(false)
    val syncMode = MutableStateFlow("BEI_START_UND_STANDBY") // "BEI_START_UND_STANDBY", "ECHTZEIT", "NUR_MANUELL"

    fun triggerSync(manualClick: Boolean = false) {
        viewModelScope.launch {
            isSyncing.value = true
            val groupId = _activeGroup.value?.id
            if (!groupId.isNullOrBlank()) {
                switchActiveGroup(groupId)
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
        viewModelScope.launch {
            repository.observeUserProfile(uid).collect { profile ->
                _userProfile.value = profile
                if (profile != null && profile.activeGroupId.isNotBlank() && _activeGroup.value?.id != profile.activeGroupId) {
                    switchActiveGroup(profile.activeGroupId)
                }
            }
        }

        viewModelScope.launch {
            repository.observeUserGroups(uid).collect { groups ->
                _userGroups.value = groups
                if (_activeGroup.value == null && groups.isNotEmpty()) {
                    val targetGroupId = _userProfile.value?.activeGroupId?.takeIf { id -> groups.any { it.id == id } }
                        ?: groups.first().id
                    switchActiveGroup(targetGroupId)
                }
            }
        }
    }

    fun switchActiveGroup(groupId: String) {
        activeGroupObservationJob?.cancel()
        machinesObservationJob?.cancel()
        maintenancesObservationJob?.cancel()

        activeGroupObservationJob = viewModelScope.launch {
            repository.observeGroup(groupId).collect { group ->
                _activeGroup.value = group
            }
        }

        machinesObservationJob = viewModelScope.launch {
            repository.observeMachines(groupId).collect { list ->
                _machines.value = list
            }
        }

        maintenancesObservationJob = viewModelScope.launch {
            repository.observeMaintenances(groupId).collect { list ->
                _maintenances.value = list
            }
        }

        // Persist active group in user profile
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val current = _userProfile.value ?: UserProfile(userId = uid)
            repository.saveUserProfile(current.copy(activeGroupId = groupId))
        }
    }

    fun createNewGroup(name: String, code: String?) {
        viewModelScope.launch {
            isLoading.value = true
            val result = repository.createGroup(name, code)
            isLoading.value = false
            result.onSuccess { created ->
                switchActiveGroup(created.id)
                showGroupDialog.value = false
                notification.value = UiNotification("Gruppe '${created.name}' mit Code ${created.code} erstellt!")
            }.onFailure { e ->
                notification.value = UiNotification("Fehler beim Erstellen der Gruppe: ${e.localizedMessage}", isError = true)
            }
        }
    }

    fun joinGroupByCode(code: String) {
        viewModelScope.launch {
            isLoading.value = true
            val result = repository.joinGroupByCode(code)
            isLoading.value = false
            result.onSuccess { joined ->
                switchActiveGroup(joined.id)
                showGroupDialog.value = false
                notification.value = UiNotification("Erfolgreich der Gruppe '${joined.name}' beigetreten!")
            }.onFailure { e ->
                notification.value = UiNotification(e.localizedMessage ?: "Beitritt fehlgeschlagen", isError = true)
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

    fun addTaskToMachine(machineId: String, task: com.example.data.model.MaintenanceTask) {
        val currentMachine = _machines.value.find { it.id == machineId } ?: return
        val updatedTasks = currentMachine.tasks + task
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
        val updatedTasks = currentMachine.tasks.map { if (it.id == task.id) task else it }
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
        val updatedTasks = currentMachine.tasks.filter { it.id != taskId }
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
