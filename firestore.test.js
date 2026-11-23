const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_user_123";
const BOB_UID = "bob_user_456";

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

test("Unauthenticated users cannot read or write any user collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("readings").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tariffs").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("auditLogs").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("readings").doc("r1").set({
      userId: ALICE_UID,
      readingKwh: 12450,
      readingTimestampMs: Date.now(),
      chargingStatus: "NOT_CHARGING",
      meterReset: false,
      edited: false,
      entryCreatedAt: new Date(Date.now() - 1000),
      lastModifiedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create, update, list, and delete meter readings with Kotlin-aligned payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const readingsCol = aliceDb.collection("users").doc(ALICE_UID).collection("readings");

  await assertSucceeds(
    readingsCol.doc("reading_1").set({
      userId: ALICE_UID,
      readingKwh: 12450.5,
      readingTimestampMs: Date.now() - 5000,
      chargingStatus: "CHARGING",
      meterReset: false,
      weatherTempC: 28.4,
      weatherCondition: "Partly Cloudy",
      weatherHumidity: 76,
      weatherWindKmh: 11.2,
      weatherRainMm: 0.0,
      weatherLocationName: "Colombo",
      remarks: "Evening EV top-up",
      edited: false,
      entryCreatedAt: created,
      lastModifiedAt: created,
    })
  );

  // List succeeds
  await assertSucceeds(readingsCol.get());

  // Partial update preserving entryCreatedAt succeeds
  await assertSucceeds(
    readingsCol.doc("reading_1").update({
      readingKwh: 12451.0,
      remarks: "Corrected reading",
      edited: true,
      lastModifiedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject mutating entryCreatedAt
  await assertFails(
    readingsCol.doc("reading_1").update({
      entryCreatedAt: new Date(Date.now() - 500),
      lastModifiedAt: new Date(Date.now() - 500),
    })
  );

  // Reject @DocumentId `id` inside payload
  await assertFails(
    readingsCol.doc("reading_2").set({
      id: "reading_2",
      userId: ALICE_UID,
      readingKwh: 12468,
      readingTimestampMs: Date.now(),
      chargingStatus: "FULLY_CHARGED",
      meterReset: false,
      edited: false,
      entryCreatedAt: created,
      lastModifiedAt: created,
    })
  );

  // Reject negative readingKwh and invalid chargingStatus
  await assertFails(
    readingsCol.doc("reading_bad_num").set({
      userId: ALICE_UID,
      readingKwh: -5,
      readingTimestampMs: Date.now(),
      chargingStatus: "NOT_CHARGING",
      meterReset: false,
      edited: false,
      entryCreatedAt: created,
      lastModifiedAt: created,
    })
  );
  await assertFails(
    readingsCol.doc("reading_bad_status").set({
      userId: ALICE_UID,
      readingKwh: 100,
      readingTimestampMs: Date.now(),
      chargingStatus: "INVALID_STATE",
      meterReset: false,
      edited: false,
      entryCreatedAt: created,
      lastModifiedAt: created,
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    readingsCol.doc("reading_future").set({
      userId: ALICE_UID,
      readingKwh: 12500,
      readingTimestampMs: Date.now(),
      chargingStatus: "NOT_CHARGING",
      meterReset: false,
      edited: false,
      entryCreatedAt: new Date(Date.now() + 60000),
      lastModifiedAt: new Date(Date.now() + 60000),
    })
  );

  // Delete succeeds for owner
  await assertSucceeds(readingsCol.doc("reading_1").delete());
});

test("Cross-user isolation blocks reading, listing, and writing another user's data", async () => {
  const past = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const bobDb = ctx.firestore().collection("users").doc(BOB_UID);
    await bobDb.set({
      userId: BOB_UID,
      displayName: "Bob",
      createdAt: past,
      updatedAt: past,
    });
    await bobDb.collection("readings").doc("bob_r1").set({
      userId: BOB_UID,
      readingKwh: 500,
      readingTimestampMs: Date.now() - 5000,
      chargingStatus: "NOT_CHARGING",
      meterReset: false,
      edited: false,
      entryCreatedAt: past,
      lastModifiedAt: past,
    });
    await bobDb.collection("tariffs").doc("bob_t1").set({
      userId: BOB_UID,
      name: "Bob Tariff",
      currency: "LKR",
      ratePerKwh: 30,
      useTieredRates: false,
      tiers: [],
      fixedMonthlyCharge: 400,
      otherCharges: 0,
      effectiveFromEpochMs: Date.now() - 86400000,
      createdAt: past,
      updatedAt: past,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("readings").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("readings").doc("bob_r1").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("readings").doc("bob_r1").update({
      remarks: "Hijacked",
      lastModifiedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tariffs").get());
});

test("Tariffs, Settings, and immutable AuditLogs enforce schema & immutability", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);
  const userDoc = aliceDb.collection("users").doc(ALICE_UID);

  // Settings
  await assertSucceeds(
    userDoc.collection("settings").doc("preferences").set({
      userId: ALICE_UID,
      themeMode: "DARK_NAVY",
      weatherAutoFetch: true,
      defaultCurrency: "LKR",
      manualLocationName: "Kandy",
      manualLatitude: 7.2906,
      manualLongitude: 80.6337,
      updatedAt: past,
    })
  );

  // Tariff with slab tiers
  await assertSucceeds(
    userDoc.collection("tariffs").doc("t1").set({
      userId: ALICE_UID,
      name: "Domestic Block 2026",
      currency: "LKR",
      ratePerKwh: 32.5,
      useTieredRates: true,
      tiers: [
        { fromKwh: 0, toKwh: 60, ratePerKwh: 20 },
        { fromKwh: 60, toKwh: 120, ratePerKwh: 35 },
      ],
      fixedMonthlyCharge: 500,
      otherCharges: 150,
      effectiveFromEpochMs: Date.now() - 86400000,
      notes: "Custom user tariff",
      createdAt: past,
      updatedAt: past,
    })
  );

  // AuditLog create succeeds, update/delete fail
  await assertSucceeds(
    userDoc.collection("auditLogs").doc("log_1").set({
      userId: ALICE_UID,
      readingId: "reading_1",
      action: "CREATE",
      newReadingKwh: 12450,
      newTimestampMs: Date.now() - 5000,
      reason: "Initial reading",
      loggedAt: past,
    })
  );
  await assertFails(
    userDoc.collection("auditLogs").doc("log_1").update({ reason: "Tampered" })
  );
  await assertFails(userDoc.collection("auditLogs").doc("log_1").delete());
});
