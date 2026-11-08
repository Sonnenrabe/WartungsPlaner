package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.FamilyGroup
import com.example.data.model.GroupCodeLookup
import com.example.data.model.Machine
import com.example.data.model.MaintenanceEntry
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MaintenanceRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- USER PROFILE ---
    fun observeUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        val docRef = db.collection("users").document(userId)
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.GET, docRef.path)
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val profile = snapshot.toObject(UserProfile::class.java)
                trySend(profile)
            } else {
                trySend(null)
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val existingDoc = docRef.get().await()

        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "displayName" to profile.displayName.ifBlank { "Familienmitglied" },
            "email" to (profile.email.ifBlank { auth.currentUser?.email ?: "" }),
            "activeGroupId" to profile.activeGroupId,
            "joinedGroupIds" to profile.joinedGroupIds,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (!existingDoc.exists()) {
            payload["createdAt"] = FieldValue.serverTimestamp()
        } else {
            existingDoc.getTimestamp("createdAt")?.let { payload["createdAt"] = it }
        }

        docRef.set(payload).await()
        Unit
    }.onFailure { handleFirestoreError(it as Exception, OperationType.WRITE, "users/${auth.currentUser?.uid}") }

    // --- GROUPS & FAMILY SYNC VIA CODE ---
    fun observeUserGroups(userId: String): Flow<List<FamilyGroup>> = callbackFlow {
        val query = db.collection("groups").whereArrayContains("memberUids", userId)
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "groups")
                close(error)
                return@addSnapshotListener
            }
            val groups = snapshot?.toObjects(FamilyGroup::class.java) ?: emptyList()
            trySend(groups)
        }
        awaitClose { registration.remove() }
    }

    fun observeGroup(groupId: String): Flow<FamilyGroup?> = callbackFlow {
        if (groupId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val docRef = db.collection("groups").document(groupId)
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.GET, docRef.path)
                close(error)
                return@addSnapshotListener
            }
            val group = if (snapshot != null && snapshot.exists()) {
                snapshot.toObject(FamilyGroup::class.java)
            } else null
            trySend(group)
        }
        awaitClose { registration.remove() }
    }

    suspend fun createGroup(name: String, customCode: String? = null): Result<FamilyGroup> = runCatching {
        val uid = requireUserId()
        val groupId = UUID.randomUUID().toString()
        val rawCode = (customCode?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            ?: ("FAM-" + (1000 + (Math.random() * 9000).toInt())))
        val cleanCode = rawCode.replace("[^A-Z0-9-]".toRegex(), "")

        val groupDocRef = db.collection("groups").document(groupId)
        val groupPayload = mapOf(
            "id" to groupId,
            "name" to name.trim().ifBlank { "Meine Familie" },
            "code" to cleanCode,
            "ownerId" to uid,
            "memberUids" to listOf(uid),
            "deviceGroups" to emptyList<String>(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        groupDocRef.set(groupPayload).await()

        // Also register code in group_codes lookup directory
        val codeDocRef = db.collection("group_codes").document(cleanCode)
        val codePayload = mapOf(
            "code" to cleanCode,
            "groupId" to groupId,
            "name" to name.trim().ifBlank { "Meine Familie" },
            "createdAt" to FieldValue.serverTimestamp()
        )
        codeDocRef.set(codePayload).await()

        // Update user profile activeGroupId and joinedGroupIds
        val userDoc = db.collection("users").document(uid).get().await()
        if (userDoc.exists()) {
            val currentJoined = userDoc.get("joinedGroupIds") as? List<*> ?: emptyList<String>()
            val updatedJoined = (currentJoined.mapNotNull { it?.toString() } + groupId).distinct()
            db.collection("users").document(uid).update(
                mapOf(
                    "activeGroupId" to groupId,
                    "joinedGroupIds" to updatedJoined,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            db.collection("users").document(uid).set(
                mapOf(
                    "userId" to uid,
                    "displayName" to (auth.currentUser?.displayName ?: "Familienmitglied"),
                    "email" to (auth.currentUser?.email ?: ""),
                    "activeGroupId" to groupId,
                    "joinedGroupIds" to listOf(groupId),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        FamilyGroup(
            id = groupId,
            name = name,
            code = cleanCode,
            ownerId = uid,
            memberUids = listOf(uid),
            deviceGroups = emptyList()
        )
    }.onFailure { handleFirestoreError(it as Exception, OperationType.CREATE, "groups") }

    suspend fun addDeviceGroup(groupId: String, groupName: String): Result<Unit> = runCatching {
        val cleanName = groupName.trim()
        if (cleanName.isBlank()) throw IllegalArgumentException("Gruppenname darf nicht leer sein.")
        if (cleanName.equals("Alle", ignoreCase = true)) throw IllegalArgumentException("'Alle' ist ein reservierter Name.")

        val groupDocRef = db.collection("groups").document(groupId)
        val snapshot = groupDocRef.get().await()
        val current = (snapshot.get("deviceGroups") as? List<*>)?.mapNotNull { it?.toString()?.trim() } ?: emptyList()
        if (current.any { it.equals(cleanName, ignoreCase = true) }) {
            return@runCatching // already exists, nothing to do
        }
        val updated = current + cleanName
        groupDocRef.update(
            mapOf(
                "deviceGroups" to updated,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { handleFirestoreError(it as Exception, OperationType.UPDATE, "groups/$groupId/deviceGroups") }

    suspend fun renameDeviceGroup(groupId: String, oldName: String, newName: String): Result<Unit> = runCatching {
        val cleanNew = newName.trim()
        if (cleanNew.isBlank()) throw IllegalArgumentException("Gruppenname darf nicht leer sein.")
        if (cleanNew.equals("Alle", ignoreCase = true)) throw IllegalArgumentException("'Alle' ist ein reservierter Name.")

        val groupDocRef = db.collection("groups").document(groupId)
        val snapshot = groupDocRef.get().await()
        val current = (snapshot.get("deviceGroups") as? List<*>)?.mapNotNull { it?.toString()?.trim() } ?: emptyList()
        val updated = current.map { if (it.equals(oldName.trim(), ignoreCase = true)) cleanNew else it }.distinct()

        groupDocRef.update(
            mapOf(
                "deviceGroups" to updated,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        // Also update all machines in this group that had oldName as groupTag
        val machinesQuery = db.collection("groups").document(groupId).collection("machines")
            .whereEqualTo("groupTag", oldName.trim())
            .get().await()

        for (doc in machinesQuery.documents) {
            doc.reference.update(
                mapOf(
                    "groupTag" to cleanNew,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Unit
    }.onFailure { handleFirestoreError(it as Exception, OperationType.UPDATE, "groups/$groupId/renameDeviceGroup") }

    suspend fun deleteDeviceGroup(groupId: String, groupName: String): Result<Unit> = runCatching {
        val cleanName = groupName.trim()
        val groupDocRef = db.collection("groups").document(groupId)
        val snapshot = groupDocRef.get().await()
        val current = (snapshot.get("deviceGroups") as? List<*>)?.mapNotNull { it?.toString()?.trim() } ?: emptyList()
        val updated = current.filterNot { it.equals(cleanName, ignoreCase = true) }

        groupDocRef.update(
            mapOf(
                "deviceGroups" to updated,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        // For machines in this group, reassign to "Allgemein" or first remaining group
        val fallback = updated.firstOrNull() ?: "Allgemein"
        val machinesQuery = db.collection("groups").document(groupId).collection("machines")
            .whereEqualTo("groupTag", cleanName)
            .get().await()

        for (doc in machinesQuery.documents) {
            doc.reference.update(
                mapOf(
                    "groupTag" to fallback,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Unit
    }.onFailure { handleFirestoreError(it as Exception, OperationType.UPDATE, "groups/$groupId/deleteDeviceGroup") }

    suspend fun joinGroupByCode(code: String): Result<FamilyGroup> = runCatching {
        val uid = requireUserId()
        val cleanCode = code.trim().uppercase()
        val codeDoc = db.collection("group_codes").document(cleanCode).get().await()
        if (!codeDoc.exists()) {
            throw IllegalArgumentException("Keine Gruppe mit dem Code '$cleanCode' gefunden. Bitte prüfe den Code.")
        }

        val targetGroupId = codeDoc.getString("groupId")
            ?: throw IllegalStateException("Ungültiger Gruppeneintrag.")

        val groupDocRef = db.collection("groups").document(targetGroupId)
        val groupDoc = groupDocRef.get().await()
        if (!groupDoc.exists()) {
            throw IllegalArgumentException("Die Gruppe existiert nicht mehr.")
        }

        val currentMembers = (groupDoc.get("memberUids") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val updatedMembers = if (uid !in currentMembers) {
            val nextList = currentMembers + uid
            groupDocRef.update(
                mapOf(
                    "memberUids" to nextList,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            nextList
        } else {
            currentMembers
        }

        // Update user profile
        val userDoc = db.collection("users").document(uid).get().await()
        if (userDoc.exists()) {
            val currentJoined = (userDoc.get("joinedGroupIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            val updatedJoined = (currentJoined + targetGroupId).distinct()
            db.collection("users").document(uid).update(
                mapOf(
                    "activeGroupId" to targetGroupId,
                    "joinedGroupIds" to updatedJoined,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            db.collection("users").document(uid).set(
                mapOf(
                    "userId" to uid,
                    "displayName" to (auth.currentUser?.displayName ?: "Familienmitglied"),
                    "email" to (auth.currentUser?.email ?: ""),
                    "activeGroupId" to targetGroupId,
                    "joinedGroupIds" to listOf(targetGroupId),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        val groupObj = groupDoc.toObject(FamilyGroup::class.java)
        groupObj?.copy(memberUids = updatedMembers) ?: FamilyGroup(id = targetGroupId, name = codeDoc.getString("name") ?: "", code = cleanCode, memberUids = updatedMembers)
    }.onFailure { handleFirestoreError(it as Exception, OperationType.UPDATE, "groups/join") }

    // --- MACHINES ---
    fun observeMachines(groupId: String): Flow<List<Machine>> = callbackFlow {
        if (groupId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val query = db.collection("groups").document(groupId)
            .collection("machines")

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "groups/$groupId/machines")
                close(error)
                return@addSnapshotListener
            }
            val machines = snapshot?.toObjects(Machine::class.java) ?: emptyList()
            trySend(machines)
        }
        awaitClose { registration.remove() }
    }

    suspend fun saveMachine(groupId: String, machine: Machine): Result<String> = runCatching {
        val uid = requireUserId()
        val isNew = machine.id.isBlank()
        val machineId = if (isNew) UUID.randomUUID().toString() else machine.id
        val docRef = db.collection("groups").document(groupId).collection("machines").document(machineId)
        val existingDoc = if (!isNew) docRef.get().await() else null

        // Ensure current group's member list is attached for flat authorization
        val groupDoc = db.collection("groups").document(groupId).get().await()
        val groupMembers = (groupDoc.get("memberUids") as? List<*>)?.mapNotNull { it?.toString() } ?: listOf(uid)

        val tasksPayload = machine.tasks.map { task ->
            mapOf(
                "id" to (task.id.ifBlank { UUID.randomUUID().toString() }),
                "title" to task.title.trim(),
                "dueDate" to task.dueDate.trim(),
                "intervalMonths" to task.intervalMonths,
                "notes" to task.notes.trim(),
                "isCompleted" to task.isCompleted
            )
        }

        val payload = mutableMapOf<String, Any>(
            "id" to machineId,
            "groupId" to groupId,
            "name" to machine.name.trim(),
            "category" to machine.category.trim(),
            "groupTag" to machine.groupTag.trim().ifBlank { "Allgemein" },
            "location" to machine.location.trim(),
            "notes" to machine.notes.trim(),
            "nextDueDate" to machine.nextDueDate.trim(),
            "nextDueMaintenance" to machine.nextDueMaintenance.trim(),
            "lastMaintenanceDate" to machine.lastMaintenanceDate.trim(),
            "tasks" to tasksPayload,
            "memberUids" to groupMembers,
            "createdBy" to (if (existingDoc?.exists() == true) existingDoc.getString("createdBy") ?: uid else uid),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (isNew || existingDoc?.exists() != true) {
            payload["createdAt"] = FieldValue.serverTimestamp()
        } else {
            existingDoc.getTimestamp("createdAt")?.let { payload["createdAt"] = it }
        }

        docRef.set(payload).await()
        machineId
    }.onFailure { handleFirestoreError(it as Exception, OperationType.WRITE, "groups/$groupId/machines") }

    suspend fun updateMachineNotes(groupId: String, machineId: String, notes: String): Result<Unit> = runCatching {
        val docRef = db.collection("groups").document(groupId).collection("machines").document(machineId)
        docRef.update(
            mapOf(
                "notes" to notes.trim(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { handleFirestoreError(it as Exception, OperationType.UPDATE, "groups/$groupId/machines/$machineId/notes") }

    suspend fun deleteMachine(groupId: String, machineId: String): Result<Unit> = runCatching {
        val docRef = db.collection("groups").document(groupId).collection("machines").document(machineId)
        docRef.delete().await()
        Unit
    }.onFailure { handleFirestoreError(it as Exception, OperationType.DELETE, "groups/$groupId/machines/$machineId") }

    // --- MAINTENANCES ---
    fun observeMaintenances(groupId: String, machineId: String? = null): Flow<List<MaintenanceEntry>> = callbackFlow {
        if (groupId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        var query: Query = db.collection("groups").document(groupId)
            .collection("maintenances")

        if (!machineId.isNullOrBlank()) {
            query = query.whereEqualTo("machineId", machineId)
        }

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "groups/$groupId/maintenances")
                close(error)
                return@addSnapshotListener
            }
            val entries = snapshot?.toObjects(MaintenanceEntry::class.java) ?: emptyList()
            trySend(entries)
        }
        awaitClose { registration.remove() }
    }

    suspend fun saveMaintenance(
        groupId: String,
        entry: MaintenanceEntry,
        rescheduleDate: String? = null,
        rescheduleTask: String? = null
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val isNew = entry.id.isBlank()
        val maintenanceId = if (isNew) UUID.randomUUID().toString() else entry.id
        val docRef = db.collection("groups").document(groupId).collection("maintenances").document(maintenanceId)
        val existingDoc = if (!isNew) docRef.get().await() else null

        val groupDoc = db.collection("groups").document(groupId).get().await()
        val groupMembers = (groupDoc.get("memberUids") as? List<*>)?.mapNotNull { it?.toString() } ?: listOf(uid)

        val payload = mutableMapOf<String, Any>(
            "id" to maintenanceId,
            "groupId" to groupId,
            "machineId" to entry.machineId,
            "machineName" to entry.machineName.trim(),
            "title" to entry.title.trim(),
            "type" to entry.type.trim(),
            "completedDate" to entry.completedDate.trim(),
            "nextScheduledDate" to (rescheduleDate?.trim() ?: entry.nextScheduledDate.trim()),
            "performedBy" to entry.performedBy.trim(),
            "cost" to entry.cost,
            "notes" to entry.notes.trim(),
            "memberUids" to groupMembers,
            "createdBy" to (if (existingDoc?.exists() == true) existingDoc.getString("createdBy") ?: uid else uid),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (isNew || existingDoc?.exists() != true) {
            payload["createdAt"] = FieldValue.serverTimestamp()
        } else {
            existingDoc.getTimestamp("createdAt")?.let { payload["createdAt"] = it }
        }

        docRef.set(payload).await()

        // If a rescheduleDate was selected, update the parent machine's nextDueDate and lastMaintenanceDate
        if (entry.machineId.isNotBlank()) {
            val machineRef = db.collection("groups").document(groupId).collection("machines").document(entry.machineId)
            val machineDoc = machineRef.get().await()
            if (machineDoc.exists()) {
                val machineUpdates = mutableMapOf<String, Any>(
                    "lastMaintenanceDate" to entry.completedDate.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!rescheduleDate.isNullOrBlank()) {
                    machineUpdates["nextDueDate"] = rescheduleDate.trim()
                    machineUpdates["nextDueMaintenance"] = (rescheduleTask?.trim()?.takeIf { it.isNotBlank() } ?: entry.title.trim())
                }
                machineRef.update(machineUpdates).await()
            }
        }

        maintenanceId
    }.onFailure { handleFirestoreError(it as Exception, OperationType.WRITE, "groups/$groupId/maintenances") }

    suspend fun deleteMaintenance(groupId: String, maintenanceId: String): Result<Unit> = runCatching {
        val docRef = db.collection("groups").document(groupId).collection("maintenances").document(maintenanceId)
        docRef.delete().await()
        Unit
    }.onFailure { handleFirestoreError(it as Exception, OperationType.DELETE, "groups/$groupId/maintenances/$maintenanceId") }

    // --- JSON BACKUP EXPORT & IMPORT ---
    suspend fun exportBackup(groupId: String): Result<String> = runCatching {
        val uid = requireUserId()
        val groupDoc = db.collection("groups").document(groupId).get().await()
        val groupName = groupDoc.getString("name") ?: "WartungsPlaner"
        val groupCode = groupDoc.getString("code") ?: ""

        val machinesSnapshot = db.collection("groups").document(groupId).collection("machines").get().await()
        val maintenancesSnapshot = db.collection("groups").document(groupId).collection("maintenances").get().await()

        val jsonRoot = JSONObject()
        jsonRoot.put("version", 1)
        jsonRoot.put("exportTimestamp", System.currentTimeMillis())
        jsonRoot.put("groupName", groupName)
        jsonRoot.put("groupCode", groupCode)

        val machinesArr = JSONArray()
        for (doc in machinesSnapshot.documents) {
            val m = JSONObject().apply {
                put("id", doc.getString("id") ?: doc.id)
                put("name", doc.getString("name") ?: "")
                put("category", doc.getString("category") ?: "")
                put("groupTag", doc.getString("groupTag") ?: "")
                put("location", doc.getString("location") ?: "")
                put("notes", doc.getString("notes") ?: "")
                put("nextDueDate", doc.getString("nextDueDate") ?: "")
                put("nextDueMaintenance", doc.getString("nextDueMaintenance") ?: "")
                put("lastMaintenanceDate", doc.getString("lastMaintenanceDate") ?: "")

                val rawTasks = doc.get("tasks") as? List<*> ?: emptyList<Any>()
                val tasksJsonArr = JSONArray()
                for (item in rawTasks) {
                    if (item is Map<*, *>) {
                        val taskObj = JSONObject().apply {
                            put("id", item["id"]?.toString() ?: "")
                            put("title", item["title"]?.toString() ?: "")
                            put("dueDate", item["dueDate"]?.toString() ?: "")
                            put("intervalMonths", (item["intervalMonths"] as? Number)?.toLong() ?: 0L)
                            put("notes", item["notes"]?.toString() ?: "")
                            put("isCompleted", item["isCompleted"] as? Boolean ?: false)
                        }
                        tasksJsonArr.put(taskObj)
                    }
                }
                put("tasks", tasksJsonArr)
            }
            machinesArr.put(m)
        }
        jsonRoot.put("machines", machinesArr)

        val rawDeviceGroups = (groupDoc.get("deviceGroups") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val deviceGroupsArr = JSONArray()
        for (dg in rawDeviceGroups) {
            deviceGroupsArr.put(dg)
        }
        jsonRoot.put("deviceGroups", deviceGroupsArr)

        val maintArr = JSONArray()
        for (doc in maintenancesSnapshot.documents) {
            val m = JSONObject().apply {
                put("id", doc.getString("id") ?: doc.id)
                put("machineId", doc.getString("machineId") ?: "")
                put("machineName", doc.getString("machineName") ?: "")
                put("title", doc.getString("title") ?: "")
                put("type", doc.getString("type") ?: "")
                put("completedDate", doc.getString("completedDate") ?: "")
                put("nextScheduledDate", doc.getString("nextScheduledDate") ?: "")
                put("performedBy", doc.getString("performedBy") ?: "")
                put("cost", doc.getDouble("cost") ?: 0.0)
                put("notes", doc.getString("notes") ?: "")
            }
            maintArr.put(m)
        }
        jsonRoot.put("maintenances", maintArr)

        jsonRoot.toString(2)
    }

    suspend fun importBackup(groupId: String, jsonString: String): Result<Pair<Int, Int>> = runCatching {
        val root = JSONObject(jsonString)
        val machinesArr = root.optJSONArray("machines") ?: JSONArray()
        val maintArr = root.optJSONArray("maintenances") ?: JSONArray()

        val deviceGroupsArr = root.optJSONArray("deviceGroups")
        if (deviceGroupsArr != null && deviceGroupsArr.length() > 0) {
            val importedGroups = mutableListOf<String>()
            for (g in 0 until deviceGroupsArr.length()) {
                val gName = deviceGroupsArr.optString(g, "").trim()
                if (gName.isNotBlank() && gName !in importedGroups) {
                    importedGroups.add(gName)
                }
            }
            if (importedGroups.isNotEmpty()) {
                val groupDocRef = db.collection("groups").document(groupId)
                val snapshot = groupDocRef.get().await()
                val current = (snapshot.get("deviceGroups") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val combined = (current + importedGroups).distinct()
                groupDocRef.update(
                    mapOf(
                        "deviceGroups" to combined,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }

        var importedMachines = 0
        var importedMaintenances = 0

        for (i in 0 until machinesArr.length()) {
            val obj = machinesArr.getJSONObject(i)
            val rawTasksArr = obj.optJSONArray("tasks") ?: JSONArray()
            val parsedTasks = mutableListOf<com.example.data.model.MaintenanceTask>()
            for (t in 0 until rawTasksArr.length()) {
                val tObj = rawTasksArr.getJSONObject(t)
                parsedTasks.add(
                    com.example.data.model.MaintenanceTask(
                        id = tObj.optString("id", UUID.randomUUID().toString()),
                        title = tObj.optString("title", ""),
                        dueDate = tObj.optString("dueDate", ""),
                        intervalMonths = tObj.optLong("intervalMonths", 0L),
                        notes = tObj.optString("notes", ""),
                        isCompleted = tObj.optBoolean("isCompleted", false)
                    )
                )
            }

            val m = Machine(
                id = obj.optString("id", UUID.randomUUID().toString()),
                groupId = groupId,
                name = obj.optString("name", "Importiertes Gerät"),
                category = obj.optString("category", "Sonstiges"),
                groupTag = obj.optString("groupTag", "Importiert"),
                location = obj.optString("location", ""),
                notes = obj.optString("notes", ""),
                nextDueDate = obj.optString("nextDueDate", ""),
                nextDueMaintenance = obj.optString("nextDueMaintenance", ""),
                lastMaintenanceDate = obj.optString("lastMaintenanceDate", ""),
                tasks = parsedTasks
            )
            saveMachine(groupId, m).getOrThrow()
            importedMachines++
        }

        for (i in 0 until maintArr.length()) {
            val obj = maintArr.getJSONObject(i)
            val entry = MaintenanceEntry(
                id = obj.optString("id", UUID.randomUUID().toString()),
                groupId = groupId,
                machineId = obj.optString("machineId", ""),
                machineName = obj.optString("machineName", ""),
                title = obj.optString("title", "Wartung"),
                type = obj.optString("type", "Inspektion"),
                completedDate = obj.optString("completedDate", ""),
                nextScheduledDate = obj.optString("nextScheduledDate", ""),
                performedBy = obj.optString("performedBy", ""),
                cost = obj.optDouble("cost", 0.0),
                notes = obj.optString("notes", "")
            )
            saveMaintenance(groupId, entry).getOrThrow()
            importedMaintenances++
        }

        Pair(importedMachines, importedMaintenances)
    }
}
