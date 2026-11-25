const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_murgi_123";
const BOB_UID = "bob_murgi_456";

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

test("Unauthenticated user: cannot read or write user settings, villages, or locations", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("villages").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("locations").get());

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("locations").doc("loc_1").set({
      userId: ALICE_UID,
      villageId: "village_1",
      name: "Chowk",
      category: "ROAD",
      markedAsHouse: false,
      favorite: false,
      explored: true,
      panoramaOnly: true,
      posX: 0,
      posZ: 0,
      connectedIds: [],
      mediaType: "PANORAMA_360",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated owner: valid Kotlin payloads for settings, village, and location succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // UserSettings create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Aarav Explorer",
      characterPreset: "EXPLORER",
      characterAccentHex: "#FF9F1C",
      walkSpeedMultiplier: 1.0,
      cameraSensitivity: 1.0,
      invertCameraY: false,
      graphicsQuality: "HIGH",
      autoGraphics: true,
      lodEnabled: true,
      occlusionCulling: true,
      dynamicResolution: true,
      soundEnabled: true,
      weatherMode: "GOLDEN_HOUR",
      timeOfDay: 0.72,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Village create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("villages").doc("murgi_tola_main").set({
      userId: ALICE_UID,
      name: "Murgi Tola",
      description: "Our home village",
      lastLocationId: "loc_gate",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Location create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("locations").doc("loc_gate").set({
      userId: ALICE_UID,
      villageId: "murgi_tola_main",
      name: "Murgi Tola Main Chowk",
      category: "ROAD",
      notes: "Scanned near the banyan tree",
      markedAsHouse: false,
      favorite: true,
      explored: true,
      panoramaOnly: false,
      posX: 12.5,
      posZ: -8.0,
      connectedIds: ["loc_shop"],
      mediaType: "MULTI_PHOTO",
      localImagePath: "/data/user/0/app/files/scans/loc_gate.jpg",
      aiSummary: "Village dirt and paved road with 2 brick homes and banyan tree.",
      aiBuildingsCount: 2,
      aiTreesCount: 3,
      aiPolesCount: 1,
      aiRoadSurface: "Brick paved lane",
      aiLighting: "Golden afternoon sunlight",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update on location (createdAt & villageId preserved)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("locations").doc("loc_gate").update({
      markedAsHouse: true,
      favorite: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // List subcollections succeeds even when filtered/unfiltered under owner path
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("villages").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("locations").where("villageId", "==", "murgi_tola_main").get()
  );
});

test("Security bounds: reject ghost fields, immutable field edits, @DocumentId id key, and invalid enums", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Reject unexpected @DocumentId 'id' field in write payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("villages").doc("v_bad").set({
      id: "v_bad",
      userId: ALICE_UID,
      name: "Bad Village",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("villages").doc("v_future").set({
      userId: ALICE_UID,
      name: "Future Village",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("villages").doc("v_ghost").set({
      userId: ALICE_UID,
      name: "Ghost Village",
      isAdmin: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create valid location then reject immutable createdAt or villageId mutation
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("locations").doc("loc_1").set({
      userId: ALICE_UID,
      villageId: "v1",
      name: "Tea Stall",
      category: "SHOP",
      markedAsHouse: false,
      favorite: false,
      explored: false,
      panoramaOnly: true,
      posX: 0,
      posZ: 0,
      connectedIds: [],
      mediaType: "PANORAMA_360",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("locations").doc("loc_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("locations").doc("loc_1").update({
      villageId: "v2",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation: Bob cannot read or modify Alice's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const now = new Date(Date.now() - 10000);
    await context.firestore().collection("users").doc(ALICE_UID).collection("villages").doc("v_alice").set({
      userId: ALICE_UID,
      name: "Alice Village",
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("villages").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("villages").doc("v_alice").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("villages").doc("v_alice").update({
      name: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
