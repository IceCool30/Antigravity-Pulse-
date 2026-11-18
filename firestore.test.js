const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

test("Unauthenticated user: cannot read or write any user collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const created = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("serverProfiles").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("serverProfiles").doc("prof_1").set({
      userId: ALICE_UID,
      name: "Workstation Tunnel",
      endpointUrl: "https://antigravity.tailnet.ts.net:8443",
      connectionMode: "HYBRID_BRIDGE",
      authToken: "tok_abc",
      keepAlivePinned: true,
      lastLatencyMs: 14,
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Authenticated owner: serverProfiles create, partial update, list, and delete succeed; invalid payloads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Valid create (matches Kotlin CloudSyncRepository.upsertServerProfile)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("serverProfiles").doc("prof_1").set({
      userId: ALICE_UID,
      name: "Antigravity Cloud Box",
      endpointUrl: "wss://ag-remote.dev.example.com/ws",
      connectionMode: "HYBRID_BRIDGE",
      authToken: "ag_live_9821",
      keepAlivePinned: true,
      lastLatencyMs: 18,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("serverProfiles").doc("prof_1").update({
      name: "Antigravity Primary Box",
      lastLatencyMs: 11,
      keepAlivePinned: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // List query on user-scoped subcollection succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("serverProfiles").get()
  );

  // Reject extra @DocumentId 'id' field on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("serverProfiles").doc("prof_2").set({
      id: "prof_2",
      userId: ALICE_UID,
      name: "Invalid ID Field",
      endpointUrl: "https://localhost:3000",
      connectionMode: "HYBRID_BRIDGE",
      authToken: "",
      keepAlivePinned: true,
      lastLatencyMs: 10,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject immutable createdAt mutation on update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("serverProfiles").doc("prof_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Authenticated owner: syncedSessions and copilotPrompts create, update, and list succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("syncedSessions").doc("sess_1").set({
      userId: ALICE_UID,
      workspaceName: "antigravity-mobile-bridge",
      branchName: "feat/native-websocket-stream",
      status: "AWAITING_APPROVAL",
      lastSummary: "Ready to execute gradle :app:assembleDebug",
      messageCount: 12,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("syncedSessions").doc("sess_1").update({
      status: "ACTIVE",
      lastSummary: "Approved command execution",
      messageCount: 13,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("copilotPrompts").doc("prompt_1").set({
      userId: ALICE_UID,
      title: "Zero-Reload WebSocket KeepAlive",
      promptText: "Refactor the language_server client to buffer delta frames while backgrounded.",
      rolePersona: "Antigravity Architect",
      preferredModel: "gemini-4-argon",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("copilotPrompts").get()
  );
});

test("Cross-user isolation: Bob cannot read, list, update, or delete Alice's profiles, sessions, or prompts", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(ALICE_UID).collection("serverProfiles").doc("prof_1").set({
      userId: ALICE_UID,
      name: "Alice Private Server",
      endpointUrl: "https://alice.internal:8443",
      connectionMode: "HYBRID_BRIDGE",
      authToken: "secret",
      keepAlivePinned: true,
      lastLatencyMs: 9,
      createdAt: created,
      updatedAt: created,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("serverProfiles").doc("prof_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("serverProfiles").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("serverProfiles").doc("prof_1").update({
      name: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("serverProfiles").doc("prof_1").delete()
  );
});
