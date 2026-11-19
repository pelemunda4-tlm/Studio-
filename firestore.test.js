const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_producer_1";
const BOB_UID = "bob_producer_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated users cannot read or write any DAW collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );
  await assertFails(
    unauthDb.collection("shared_patterns").where("shared", "==", true).get()
  );
  await assertFails(
    unauthDb.collection("shared_projects").doc("share_001").get()
  );
});

test("Owner can create, update, list, version, and delete their own CloudProject", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const projectRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("projects")
    .doc("proj_001");

  await assertSucceeds(
    projectRef.set({
      userId: ALICE_UID,
      title: "Sambalpuri Fusion Anthem",
      bpm: 118.0,
      timeSignatureNum: 4,
      timeSignatureDen: 4,
      musicalKey: "D",
      scaleName: "Indian Raga Yaman",
      trackCount: 6,
      versionNumber: 1,
      snapshotPayload: '{"tracks":6}',
      createdAt: created,
      updatedAt: created,
    })
  );

  // List projects
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("projects").get()
  );

  // Partial update preserving createdAt and userId
  await assertSucceeds(
    projectRef.update({
      title: "Sambalpuri Fusion v2",
      versionNumber: 2,
      notesSummary: "Added Mandar and Bansuri solo",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Create version history snapshot
  await assertSucceeds(
    projectRef.collection("versions").doc("ver_002").set({
      userId: ALICE_UID,
      projectId: "proj_001",
      versionNumber: 2,
      label: "Mandar Mixdown",
      snapshotPayload: '{"tracks":6,"v":2}',
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field / shadow update
  await assertFails(
    projectRef.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    projectRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject @DocumentId "id" key injection
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("projects")
      .doc("proj_bad")
      .set({
        id: "proj_bad",
        userId: ALICE_UID,
        title: "Bad Project",
        bpm: 120,
        timeSignatureNum: 4,
        timeSignatureDen: 4,
        musicalKey: "C",
        scaleName: "Major",
        trackCount: 2,
        versionNumber: 1,
        snapshotPayload: "{}",
        createdAt: created,
        updatedAt: created,
      })
  );
});

test("Cross-user access to private projects and versions is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("projects")
      .doc("bob_proj")
      .set({
        userId: BOB_UID,
        title: "Bob Private Master",
        bpm: 128,
        timeSignatureNum: 4,
        timeSignatureDen: 4,
        musicalKey: "A",
        scaleName: "Natural Minor",
        trackCount: 8,
        versionNumber: 1,
        snapshotPayload: "{}",
        createdAt: new Date(Date.now() - 5000),
        updatedAt: new Date(Date.now() - 5000),
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("projects")
      .doc("bob_proj")
      .get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("projects").get()
  );
});

test("Shared patterns enforce ownership on writes and query filters on reads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("shared_patterns").doc("pat_sambalpuri_1").set({
      userId: ALICE_UID,
      authorName: "Alice",
      name: "Dalkhai Folk Groove",
      style: "Sambalpuri Folk",
      timeSignatureNum: 4,
      timeSignatureDen: 4,
      bpm: 112,
      stepsJson: '{"steps":[1,0,1,0]}',
      shared: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Private pattern owned by Alice
  await assertSucceeds(
    aliceDb.collection("shared_patterns").doc("pat_private").set({
      userId: ALICE_UID,
      authorName: "Alice",
      name: "Secret Beat",
      style: "Nagpuri Folk",
      timeSignatureNum: 7,
      timeSignatureDen: 8,
      bpm: 125,
      stepsJson: '{"steps":[1,1,0]}',
      shared: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can query shared == true patterns
  await assertSucceeds(
    bobDb.collection("shared_patterns").where("shared", "==", true).get()
  );

  // Bob cannot run unfiltered query or read Alice's private pattern
  await assertFails(bobDb.collection("shared_patterns").get());
  await assertFails(bobDb.collection("shared_patterns").doc("pat_private").get());
  await assertFails(
    bobDb.collection("shared_patterns").doc("pat_sambalpuri_1").update({
      name: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Shared projects allow get by shareCode but restrict list to ownerId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("shared_projects").doc("TW-SHARE-99").set({
      ownerId: ALICE_UID,
      ownerName: "Alice",
      projectTitle: "Worship Live Stem",
      bpm: 74,
      musicalKey: "G",
      snapshotPayload: '{"tracks":4}',
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    bobDb.collection("shared_projects").doc("TW-SHARE-99").get()
  );
  await assertSucceeds(
    aliceDb.collection("shared_projects").where("ownerId", "==", ALICE_UID).get()
  );
  await assertFails(bobDb.collection("shared_projects").get());
});
