package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val activeGroupId: String = "",
    val joinedGroupIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FamilyGroup(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val deviceGroups: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GroupCodeLookup(
    val code: String = "",
    val groupId: String = "",
    val name: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MaintenanceTask(
    val id: String = "",
    val title: String = "",
    val dueDate: String = "",
    val intervalMonths: Long = 0,
    val notes: String = "",
    val isCompleted: Boolean = false
)

@IgnoreExtraProperties
data class Machine(
    val id: String = "",
    val groupId: String = "",
    val name: String = "",
    val category: String = "Sonstiges",
    val groupTag: String = "Allgemein",
    val location: String = "",
    val notes: String = "",
    val nextDueDate: String = "",
    val nextDueMaintenance: String = "",
    val lastMaintenanceDate: String = "",
    val tasks: List<MaintenanceTask> = emptyList(),
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MaintenanceEntry(
    val id: String = "",
    val groupId: String = "",
    val machineId: String = "",
    val machineName: String = "",
    val title: String = "",
    val type: String = "Inspektion",
    val completedDate: String = "",
    val nextScheduledDate: String = "",
    val performedBy: String = "",
    val cost: Double = 0.0,
    val notes: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
