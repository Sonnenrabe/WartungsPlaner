package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Machine
import com.example.data.model.MaintenanceEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MaintenanceRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createGroup_authenticatedUser_createsGroupSuccessfully() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = MaintenanceRepository(firestore)

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createGroup("Familie Schmidt", "SCHMIDT")
    }
    assertTrue(result.isSuccess)
    val group = result.getOrThrow()
    assertEquals("Familie Schmidt", group.name)
    assertEquals("SCHMIDT", group.code)
    assertTrue(group.memberUids.isNotEmpty())
  }

  @Test
  fun saveMachine_and_observe_authenticatedMember_emitsList() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = MaintenanceRepository(firestore)

    val groupResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createGroup("Familie Hoetger")
    }
    val group = groupResult.getOrThrow()

    val machineId = UUID.randomUUID().toString()
    val machine = Machine(
      id = machineId,
      groupId = group.id,
      name = "Rasenmäher Gardena",
      category = "Garten",
      groupTag = "Garten & Hof",
      location = "Schuppen",
      notes = "Elektromäher mit Akku",
      nextDueDate = "2027-04-01",
      nextDueMaintenance = "Messer prüfen"
    )

    val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveMachine(group.id, machine)
    }
    if (saveResult.isFailure) {
      throw AssertionError("saveMachine failed: ${saveResult.exceptionOrNull()?.message}", saveResult.exceptionOrNull())
    }
    assertTrue(saveResult.isSuccess)

    val machines = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.observeMachines(group.id).first { list: List<Machine> -> list.any { it.id == machineId } }
    }
    assertTrue(machines.any { it.name == "Rasenmäher Gardena" })
  }

  @Test
  fun saveMaintenance_withReschedule_updatesMachineNextDue() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = MaintenanceRepository(firestore)

    val group = repo.createGroup("Werkstatt Garage").getOrThrow()
    val machineId = UUID.randomUUID().toString()
    val machine = Machine(
      id = machineId,
      groupId = group.id,
      name = "Auto Golf 7",
      category = "Auto",
      groupTag = "Garage & KFZ",
      nextDueDate = "2026-11-01"
    )
    repo.saveMachine(group.id, machine).getOrThrow()

    val maintenance = MaintenanceEntry(
      id = UUID.randomUUID().toString(),
      groupId = group.id,
      machineId = machineId,
      machineName = "Auto Golf 7",
      title = "Ölwechsel 5W-30",
      type = "Inspektion",
      completedDate = "2026-11-02",
      performedBy = "Marco",
      cost = 75.0,
      notes = "Öl + Filter gewechselt"
    )

    val saveMaintResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveMaintenance(
        groupId = group.id,
        entry = maintenance,
        rescheduleDate = "2027-11-02",
        rescheduleTask = "Nächster Ölwechsel"
      )
    }
    assertTrue(saveMaintResult.isSuccess)

    val updatedMachines = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.observeMachines(group.id).first { list: List<Machine> ->
        list.any { it.id == machineId && it.nextDueDate == "2027-11-02" }
      }
    }
    val targetMachine = updatedMachines.first { it.id == machineId }
    assertEquals("2027-11-02", targetMachine.nextDueDate)
    assertEquals("2026-11-02", targetMachine.lastMaintenanceDate)
    assertEquals("Nächster Ölwechsel", targetMachine.nextDueMaintenance)
  }

  @Test
  fun unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = MaintenanceRepository(firestore)

    var threw = false
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.observeMachines("some_group_id").first()
      }
    } catch (e: Exception) {
      threw = true
    }
    assertTrue(threw)
  }

  @Test
  fun saveMachine_withTasksAndNotes_persistsSuccessfully() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repo = MaintenanceRepository(firestore)

    val group = repo.createGroup("Haus und Hof").getOrThrow()
    val machineId = UUID.randomUUID().toString()
    val task1 = com.example.data.model.MaintenanceTask(
      id = "task_1",
      title = "Ölwechsel 5W-30",
      dueDate = "2027-05-01",
      intervalMonths = 12L,
      notes = "4.3 Liter LL-04"
    )
    val task2 = com.example.data.model.MaintenanceTask(
      id = "task_2",
      title = "TÜV / HU",
      dueDate = "2027-10-01",
      intervalMonths = 24L
    )
    val machine = Machine(
      id = machineId,
      groupId = group.id,
      name = "Familien-Kombi",
      category = "Auto",
      groupTag = "Garage & KFZ",
      notes = "Winterreifen DOT 3821, Öl 5W-30 LL-04",
      tasks = listOf(task1, task2),
      nextDueDate = "2027-05-01",
      nextDueMaintenance = "Ölwechsel 5W-30"
    )

    val saveResult = repo.saveMachine(group.id, machine)
    assertTrue(saveResult.isSuccess)

    val noteUpdateResult = repo.updateMachineNotes(group.id, machineId, "Winterreifen DOT 3821, Reifendruck 2.4 bar")
    assertTrue(noteUpdateResult.isSuccess)

    val loadedMachines = repo.observeMachines(group.id).first { list -> list.any { it.id == machineId } }
    val loaded = loadedMachines.first { it.id == machineId }
    assertEquals("Winterreifen DOT 3821, Reifendruck 2.4 bar", loaded.notes)
    assertEquals(2, loaded.tasks.size)
    assertEquals("Ölwechsel 5W-30", loaded.tasks[0].title)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_rule@test.com"
    const val DEFAULT_TIMEOUT_MS = 6000L
  }
}
