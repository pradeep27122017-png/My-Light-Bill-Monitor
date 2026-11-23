package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AppThemeMode
import com.example.data.model.AuditAction
import com.example.data.model.ChargingStatus
import com.example.data.model.MeterReading
import com.example.data.model.ReadingAuditLog
import com.example.data.model.SyncMetadataState
import com.example.data.model.TariffConfiguration
import com.example.data.model.TariffTier
import com.example.data.model.UserProfile
import com.example.data.model.UserSettings
import com.example.data.model.WeatherSnapshot
import com.example.data.remote.awaitTask
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

data class ReadingsSnapshotResult(
    val readings: List<MeterReading>,
    val syncState: SyncMetadataState
)

class EnergyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val path = "users/${user.uid}"
        return try {
            val docRef = db.collection("users").document(user.uid)
            val existing = docRef.get().awaitTask()
            val displayName = (user.displayName?.takeIf { it.isNotBlank() } ?: user.email ?: "Energy User").take(100)
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to user.uid,
                    "displayName" to displayName,
                    "email" to user.email?.take(200),
                    "photoUrl" to user.photoUrl?.toString()?.take(500),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload).awaitTask()
            } else {
                val updatePayload = mapOf(
                    "displayName" to displayName,
                    "email" to user.email?.take(200),
                    "photoUrl" to user.photoUrl?.toString()?.take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.update(updatePayload).awaitTask()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap -> snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    unwrapAndLog(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    fun observeSettings(userId: String): Flow<UserSettings?> = flow {
        val path = "users/$userId/settings/preferences"
        emitAll(
            db.collection("users").document(userId)
                .collection("settings").document("preferences")
                .snapshots()
                .map { snap -> snap.toObject(UserSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    unwrapAndLog(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    fun observeReadingsWithSync(userId: String): Flow<ReadingsSnapshotResult> = flow {
        val path = "users/$userId/readings"
        emitAll(
            db.collection("users").document(userId)
                .collection("readings")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    val items = snapshot.toObjects(
                        MeterReading::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.readingTimestampMs }
                    val pendingDocs = snapshot.documents.count { it.metadata.hasPendingWrites() }
                    ReadingsSnapshotResult(
                        readings = items,
                        syncState = SyncMetadataState(
                            hasPendingWrites = snapshot.metadata.hasPendingWrites() || pendingDocs > 0,
                            isFromCache = snapshot.metadata.isFromCache(),
                            pendingReadingCount = pendingDocs,
                            lastSyncedAtMs = if (!snapshot.metadata.hasPendingWrites() && !snapshot.metadata.isFromCache()) {
                                System.currentTimeMillis()
                            } else null
                        )
                    )
                }
                .catch { error ->
                    unwrapAndLog(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeTariffs(userId: String): Flow<List<TariffConfiguration>> = flow {
        val path = "users/$userId/tariffs"
        emitAll(
            db.collection("users").document(userId)
                .collection("tariffs")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc -> parseTariffDoc(doc) }
                        .sortedByDescending { it.effectiveFromEpochMs }
                }
                .catch { error ->
                    unwrapAndLog(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeAuditLogs(userId: String): Flow<List<ReadingAuditLog>> = flow {
        val path = "users/$userId/auditLogs"
        emitAll(
            db.collection("users").document(userId)
                .collection("auditLogs")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ReadingAuditLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.loggedAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    unwrapAndLog(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun addMeterReading(
        readingKwh: Double,
        readingTimestampMs: Long,
        chargingStatus: ChargingStatus,
        meterReset: Boolean,
        previousMeterFinalKwh: Double?,
        meterStartKwh: Double?,
        weather: WeatherSnapshot?,
        remarks: String?
    ): Result<String> {
        val uidPreview = auth.currentUser?.uid ?: "unauthenticated"
        var path = "users/$uidPreview/readings"
        return try {
            val uid = requireUserId()
            val userDoc = db.collection("users").document(uid)
            val readingRef = userDoc.collection("readings").document()
            val auditRef = userDoc.collection("auditLogs").document()
            path = readingRef.path

            val cleanRemarks = remarks?.trim()?.take(500)?.takeIf { it.isNotEmpty() }
            val readingPayload = mapOf(
                "userId" to uid,
                "readingKwh" to readingKwh,
                "readingTimestampMs" to readingTimestampMs,
                "chargingStatus" to chargingStatus.firestoreValue,
                "meterReset" to meterReset,
                "previousMeterFinalKwh" to previousMeterFinalKwh,
                "meterStartKwh" to meterStartKwh,
                "weatherTempC" to weather?.temperatureC,
                "weatherCondition" to weather?.condition?.take(80),
                "weatherHumidity" to weather?.humidityPercent?.toLong(),
                "weatherWindKmh" to weather?.windSpeedKmh,
                "weatherRainMm" to weather?.rainMm,
                "weatherLocationName" to weather?.locationName?.take(120),
                "remarks" to cleanRemarks,
                "edited" to false,
                "entryCreatedAt" to FieldValue.serverTimestamp(),
                "lastModifiedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            val auditAction = if (meterReset) AuditAction.METER_RESET else AuditAction.CREATE
            val auditReason = if (meterReset) {
                "Meter reset/replacement: ${cleanRemarks ?: "No reason provided"}".take(300)
            } else {
                (cleanRemarks ?: "New meter reading logged").take(300)
            }
            val auditPayload = mapOf(
                "userId" to uid,
                "readingId" to readingRef.id,
                "action" to auditAction.firestoreValue,
                "previousReadingKwh" to previousMeterFinalKwh,
                "newReadingKwh" to readingKwh,
                "newTimestampMs" to readingTimestampMs,
                "reason" to auditReason,
                "loggedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            db.runBatch { batch ->
                batch.set(readingRef, readingPayload)
                batch.set(auditRef, auditPayload)
            }.awaitTask()

            Result.success(readingRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMeterReading(
        original: MeterReading,
        newReadingKwh: Double,
        newTimestampMs: Long,
        newChargingStatus: ChargingStatus,
        meterReset: Boolean,
        previousMeterFinalKwh: Double?,
        meterStartKwh: Double?,
        remarks: String?,
        editReason: String
    ): Result<Unit> {
        val uidPreview = auth.currentUser?.uid ?: "unauthenticated"
        var path = "users/$uidPreview/readings/${original.id}"
        return try {
            val uid = requireUserId()
            require(original.id.isNotBlank()) { "Reading ID must not be blank" }
            val userDoc = db.collection("users").document(uid)
            val readingRef = userDoc.collection("readings").document(original.id)
            val auditRef = userDoc.collection("auditLogs").document()
            path = readingRef.path

            val cleanRemarks = remarks?.trim()?.take(500) ?: ""
            val updates = mutableMapOf<String, Any>(
                "readingKwh" to newReadingKwh,
                "readingTimestampMs" to newTimestampMs,
                "chargingStatus" to newChargingStatus.firestoreValue,
                "meterReset" to meterReset,
                "edited" to true,
                "lastModifiedAt" to FieldValue.serverTimestamp()
            )
            if (cleanRemarks.isNotEmpty()) {
                updates["remarks"] = cleanRemarks
            } else if (original.remarks != null) {
                updates["remarks"] = FieldValue.delete()
            }
            if (previousMeterFinalKwh != null) {
                updates["previousMeterFinalKwh"] = previousMeterFinalKwh
            } else if (original.previousMeterFinalKwh != null) {
                updates["previousMeterFinalKwh"] = FieldValue.delete()
            }
            if (meterStartKwh != null) {
                updates["meterStartKwh"] = meterStartKwh
            } else if (original.meterStartKwh != null) {
                updates["meterStartKwh"] = FieldValue.delete()
            }

            val auditPayload = mapOf(
                "userId" to uid,
                "readingId" to original.id,
                "action" to AuditAction.UPDATE.firestoreValue,
                "previousReadingKwh" to original.readingKwh,
                "newReadingKwh" to newReadingKwh,
                "previousTimestampMs" to original.readingTimestampMs,
                "newTimestampMs" to newTimestampMs,
                "reason" to (editReason.trim().take(300).ifBlank { "Reading edited by user" }),
                "loggedAt" to FieldValue.serverTimestamp()
            )

            db.runBatch { batch ->
                batch.update(readingRef, updates)
                batch.set(auditRef, auditPayload)
            }.awaitTask()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMeterReading(
        reading: MeterReading,
        deleteReason: String = "Deleted by user"
    ): Result<Unit> {
        val uidPreview = auth.currentUser?.uid ?: "unauthenticated"
        var path = "users/$uidPreview/readings/${reading.id}"
        return try {
            val uid = requireUserId()
            require(reading.id.isNotBlank()) { "Reading ID must not be blank" }
            val userDoc = db.collection("users").document(uid)
            val readingRef = userDoc.collection("readings").document(reading.id)
            val auditRef = userDoc.collection("auditLogs").document()
            path = readingRef.path

            val auditPayload = mapOf(
                "userId" to uid,
                "readingId" to reading.id,
                "action" to AuditAction.DELETE.firestoreValue,
                "previousReadingKwh" to reading.readingKwh,
                "previousTimestampMs" to reading.readingTimestampMs,
                "reason" to deleteReason.trim().take(300).ifBlank { "Deleted by user" },
                "loggedAt" to FieldValue.serverTimestamp()
            )

            db.runBatch { batch ->
                batch.delete(readingRef)
                batch.set(auditRef, auditPayload)
            }.awaitTask()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun saveTariff(tariff: TariffConfiguration): Result<String> {
        val uidPreview = auth.currentUser?.uid ?: "unauthenticated"
        var path = "users/$uidPreview/tariffs"
        return try {
            val uid = requireUserId()
            val tariffsCol = db.collection("users").document(uid).collection("tariffs")
            val docRef = if (tariff.id.isNotBlank()) tariffsCol.document(tariff.id) else tariffsCol.document()
            path = docRef.path

            val tiersList = tariff.tiers.take(10).map { tier ->
                val map = mutableMapOf<String, Any>(
                    "fromKwh" to tier.fromKwh,
                    "ratePerKwh" to tier.ratePerKwh
                )
                if (tier.toKwh != null) map["toKwh"] = tier.toKwh
                map
            }

            if (tariff.id.isBlank()) {
                val payload = mapOf(
                    "userId" to uid,
                    "name" to tariff.name.trim().take(80).ifBlank { "Electricity Tariff" },
                    "currency" to tariff.currency.trim().uppercase().take(10).ifBlank { "LKR" },
                    "ratePerKwh" to tariff.ratePerKwh.coerceAtLeast(0.0),
                    "useTieredRates" to tariff.useTieredRates,
                    "tiers" to tiersList,
                    "fixedMonthlyCharge" to tariff.fixedMonthlyCharge.coerceAtLeast(0.0),
                    "otherCharges" to tariff.otherCharges.coerceAtLeast(0.0),
                    "effectiveFromEpochMs" to tariff.effectiveFromEpochMs,
                    "notes" to tariff.notes?.trim()?.take(300)?.takeIf { it.isNotEmpty() },
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload).awaitTask()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "name" to tariff.name.trim().take(80).ifBlank { "Electricity Tariff" },
                    "currency" to tariff.currency.trim().uppercase().take(10).ifBlank { "LKR" },
                    "ratePerKwh" to tariff.ratePerKwh.coerceAtLeast(0.0),
                    "useTieredRates" to tariff.useTieredRates,
                    "tiers" to tiersList,
                    "fixedMonthlyCharge" to tariff.fixedMonthlyCharge.coerceAtLeast(0.0),
                    "otherCharges" to tariff.otherCharges.coerceAtLeast(0.0),
                    "effectiveFromEpochMs" to tariff.effectiveFromEpochMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val cleanNotes = tariff.notes?.trim()?.take(300)
                if (!cleanNotes.isNullOrEmpty()) {
                    updates["notes"] = cleanNotes
                } else {
                    updates["notes"] = FieldValue.delete()
                }
                docRef.update(updates).awaitTask()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTariff(tariffId: String): Result<Unit> {
        val uidPreview = auth.currentUser?.uid ?: "unauthenticated"
        var path = "users/$uidPreview/tariffs/$tariffId"
        return try {
            val uid = requireUserId()
            val ref = db.collection("users").document(uid).collection("tariffs").document(tariffId)
            path = ref.path
            ref.delete().awaitTask()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun saveSettings(
        themeMode: AppThemeMode,
        weatherAutoFetch: Boolean,
        manualLocationName: String?,
        manualLatitude: Double?,
        manualLongitude: Double?,
        defaultCurrency: String
    ): Result<Unit> {
        val uidPreview = auth.currentUser?.uid ?: "unauthenticated"
        var path = "users/$uidPreview/settings/preferences"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
                .collection("settings").document("preferences")
            path = docRef.path
            val existing = docRef.get().awaitTask()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "themeMode" to themeMode.firestoreValue,
                    "weatherAutoFetch" to weatherAutoFetch,
                    "manualLocationName" to manualLocationName?.trim()?.take(120)?.takeIf { it.isNotEmpty() },
                    "manualLatitude" to manualLatitude,
                    "manualLongitude" to manualLongitude,
                    "defaultCurrency" to defaultCurrency.trim().uppercase().take(10).ifBlank { "LKR" },
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.set(payload, SetOptions.merge()).awaitTask()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "themeMode" to themeMode.firestoreValue,
                    "weatherAutoFetch" to weatherAutoFetch,
                    "defaultCurrency" to defaultCurrency.trim().uppercase().take(10).ifBlank { "LKR" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!manualLocationName.isNullOrBlank() && manualLatitude != null && manualLongitude != null) {
                    updates["manualLocationName"] = manualLocationName.trim().take(120)
                    updates["manualLatitude"] = manualLatitude
                    updates["manualLongitude"] = manualLongitude
                } else {
                    updates["manualLocationName"] = FieldValue.delete()
                    updates["manualLatitude"] = FieldValue.delete()
                    updates["manualLongitude"] = FieldValue.delete()
                }
                docRef.update(updates).awaitTask()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun waitForPendingWrites(): Result<Unit> {
        return try {
            db.waitForPendingWrites().awaitTask()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "waitForPendingWrites")
            Result.failure(e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTariffDoc(doc: DocumentSnapshot): TariffConfiguration? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        val rawTiers = data["tiers"] as? List<*> ?: emptyList<Any>()
        val tiers = rawTiers.mapNotNull { entry ->
            val m = entry as? Map<String, Any?> ?: return@mapNotNull null
            TariffTier(
                fromKwh = (m["fromKwh"] as? Number)?.toDouble() ?: 0.0,
                toKwh = (m["toKwh"] as? Number)?.toDouble(),
                ratePerKwh = (m["ratePerKwh"] as? Number)?.toDouble() ?: 0.0
            )
        }
        return TariffConfiguration(
            id = doc.id,
            userId = data["userId"] as? String ?: "",
            name = data["name"] as? String ?: "Electricity Tariff",
            currency = data["currency"] as? String ?: "LKR",
            ratePerKwh = (data["ratePerKwh"] as? Number)?.toDouble() ?: 0.0,
            useTieredRates = data["useTieredRates"] as? Boolean ?: false,
            tiers = tiers,
            fixedMonthlyCharge = (data["fixedMonthlyCharge"] as? Number)?.toDouble() ?: 0.0,
            otherCharges = (data["otherCharges"] as? Number)?.toDouble() ?: 0.0,
            effectiveFromEpochMs = (data["effectiveFromEpochMs"] as? Number)?.toLong() ?: 0L,
            notes = data["notes"] as? String,
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    private fun unwrapAndLog(error: Throwable, op: OperationType, path: String, expectedUid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }
}
