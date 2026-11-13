const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read groups or users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("groups").doc("group_1").get());
});

test("Authenticated user: can create and read their own user profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@test.com",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Cross-user isolation: Bob cannot read Alice's user profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Family Group: Alice creates group & code, queries via array-contains, Bob joins safely, Charlie cannot read machines or hijack code", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  
  // Alice creates family group and invite code
  await assertSucceeds(
    aliceDb.collection("groups").doc("group_fam1").set({
      id: "group_fam1",
      name: "Familie Hoetger",
      code: "FAM-1234",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("group_codes").doc("FAM-1234").set({
      code: "FAM-1234",
      groupId: "group_fam1",
      name: "Familie Hoetger",
      createdAt: new Date(),
    })
  );

  // Alice can list her own groups using array-contains, but unfiltered list fails
  await assertSucceeds(
    aliceDb.collection("groups").where("memberUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(aliceDb.collection("groups").get());

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Bob looks up code
  await assertSucceeds(bobDb.collection("group_codes").doc("FAM-1234").get());

  // Bob cannot tamper with ownerId when joining
  await assertFails(
    bobDb.collection("groups").doc("group_fam1").update({
      ownerId: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(),
    })
  );

  // Bob joins group by appending his UID
  await assertSucceeds(
    bobDb.collection("groups").doc("group_fam1").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(),
    })
  );

  // Now Bob can read group and list his groups
  await assertSucceeds(bobDb.collection("groups").doc("group_fam1").get());
  await assertSucceeds(
    bobDb.collection("groups").where("memberUids", "array-contains", BOB_UID).get()
  );

  // Charlie cannot hijack existing invite code or read machines subcollection
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(
    charlieDb.collection("group_codes").doc("FAM-1234").set({
      code: "FAM-1234",
      groupId: "other_group",
      name: "Evil",
    })
  );
  await assertFails(charlieDb.collection("groups").doc("group_fam1").collection("machines").get());
});

test("Machines & Maintenance: group members can create and read machines and log maintenance", async () => {
  // Seed group with Alice and Bob
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("groups").doc("group_fam1").set({
      id: "group_fam1",
      name: "Familie Hoetger",
      code: "FAM-1234",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Alice adds a machine (Rasenmäher)
  await assertSucceeds(
    aliceDb.collection("groups").doc("group_fam1").collection("machines").doc("m_lawnmower").set({
      id: "m_lawnmower",
      groupId: "group_fam1",
      name: "Honda Rasenmäher HRX",
      category: "Garten",
      groupTag: "Garten & Hof",
      location: "Gartenschuppen",
      notes: "4-Takt Benzinmotor",
      nextDueDate: "2027-04-15",
      nextDueMaintenance: "Ölwechsel und Messer schärfen",
      lastMaintenanceDate: "2026-10-10",
      memberUids: [ALICE_UID, BOB_UID],
      createdBy: ALICE_UID,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Bob can read the machine
  await assertSucceeds(
    bobDb.collection("groups").doc("group_fam1").collection("machines").doc("m_lawnmower").get()
  );

  // Bob logs a completed maintenance
  await assertSucceeds(
    bobDb.collection("groups").doc("group_fam1").collection("maintenances").doc("entry_1").set({
      id: "entry_1",
      groupId: "group_fam1",
      machineId: "m_lawnmower",
      machineName: "Honda Rasenmäher HRX",
      title: "Messer geschliffen & gereinigt",
      type: "Verschleißteil",
      completedDate: "2026-11-02",
      nextScheduledDate: "2027-04-15",
      performedBy: "Bob",
      cost: 0,
      notes: "Messer gereinigt, ausgewuchtet und geschärft",
      memberUids: [ALICE_UID, BOB_UID],
      createdBy: BOB_UID,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Charlie cannot read machine or maintenance
  await assertFails(
    charlieDb.collection("groups").doc("group_fam1").collection("machines").doc("m_lawnmower").get()
  );
  await assertFails(
    charlieDb.collection("groups").doc("group_fam1").collection("maintenances").doc("entry_1").get()
  );
});
