package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEventIgnoreDuplicate(event: PaymentEventEntity): Long

    @Query("SELECT * FROM payment_events ORDER BY receivedAt DESC")
    fun observeAllEvents(): Flow<List<PaymentEventEntity>>

    @Query("SELECT * FROM payment_events ORDER BY receivedAt DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int = 8): Flow<List<PaymentEventEntity>>

    @Query("SELECT * FROM payment_events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: Long): PaymentEventEntity?

    @Query(
        "SELECT * FROM payment_events WHERE LOWER(provider) = LOWER(:provider) AND UPPER(transactionId) = UPPER(:transactionId) LIMIT 1"
    )
    suspend fun findByProviderAndTransactionId(
        provider: String,
        transactionId: String
    ): PaymentEventEntity?

    @Query(
        "SELECT * FROM payment_events WHERE syncState IN ('LOCAL_PENDING', 'UPLOAD_FAILED') ORDER BY receivedAt ASC"
    )
    suspend fun getPendingSyncEvents(): List<PaymentEventEntity>

    @Query(
        """
        UPDATE payment_events 
        SET syncState = :newState,
            serverStatusMessage = :statusMessage,
            retryCount = :retryCount,
            lastSyncAttemptAt = :attemptAt,
            lastSyncedAt = :syncedAt
        WHERE id = :id
        """
    )
    suspend fun updateSyncStatus(
        id: Long,
        newState: String,
        statusMessage: String?,
        retryCount: Int,
        attemptAt: Long,
        syncedAt: Long?
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagnosticLog(log: DiagnosticLogEntity): Long

    @Query("SELECT * FROM parser_diagnostics ORDER BY recordedAt DESC LIMIT 50")
    fun observeDiagnosticLogs(): Flow<List<DiagnosticLogEntity>>

    @Query("DELETE FROM parser_diagnostics WHERE recordedAt < :cutoffMillis")
    suspend fun pruneOldDiagnostics(cutoffMillis: Long)
}
