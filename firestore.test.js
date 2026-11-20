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
      authToken: "",
      keepAlivePinned: true,
      lastLatencyMs: 14,
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Authenticated owner: valid serverProfiles CRUD succeeds; javascript URLs and plaintext authTokens are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Valid create (no plaintext authToken, valid https URL)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("serverProfiles").doc("prof_1").set({
      userId: ALICE_UID,
      name: "Antigravity Cloud Box",
      endpointUrl: "https://ag-remote.dev.example.com",
      connectionMode: "HYBRID_BRIDGE",
      authToken: "",
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

  // Reject plaintext authToken upload to cloud
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("serverProfiles").doc("prof_secret").set({
      userId: ALICE_UID,
      name: "Leaked Token",
      endpointUrl: "https://localhost:3000",
      connectionMode: "HYBRID_BRIDGE",
      authToken: "secret_bearer_token",
      keepAlivePinned: true,
      lastLatencyMs: 10,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject javascript: XSS endpointUrl
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("serverProfiles").doc("prof_xss").set({
      userId: ALICE_UID,
      name: "Malicious URL",
      endpointUrl: "javascript:alert(1)",
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

test("Authenticated owner: copilotPrompts CRUD succeeds while removed syncedSessions collection is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Unused syncedSessions collection is strictly denied
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("syncedSessions").doc("sess_1").set({
      userId: ALICE_UID,
      workspaceName: "removed",
      branchName: "main",
      status: "ACTIVE",
      lastSummary: "removed",
      messageCount: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("copilotPrompts").doc("prompt_1").set({
      userId: ALICE_UID,
      title: "Zero-Reload Check",
      promptText: "Run unit tests and summarize failures.",
      rolePersona: "Antigravity Remote",
      preferredModel: "default",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("copilotPrompts").get()
  );
});

test("Cross-user isolation: Bob cannot read, list, update, or delete Alice's profiles or prompts", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(ALICE_UID).collection("serverProfiles").doc("prof_1").set({
      userId: ALICE_UID,
      name: "Alice Private Server",
      endpointUrl: "https://alice.internal:8443",
      connectionMode: "HYBRID_BRIDGE",
      authToken: "",
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

test("Retired aiStudioPresets collection is strictly denied by global default-deny rules", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("aiStudioPresets").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("aiStudioPresets").doc("preset_1").set({
      userId: ALICE_UID,
      title: "Retired Preset",
      systemInstruction: "",
      starterPrompt: "Hello",
      modelId: "gemini-3.8-flash",
      temperature: 1.0,
      createdAt: created,
      updatedAt: created,
    })
  );
});
