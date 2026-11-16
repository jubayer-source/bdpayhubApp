package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.common.AppErrorType
import com.example.core.common.AppResult
import com.example.core.common.RedactingLogger
import com.example.core.database.BDPayHubDatabase
import com.example.core.datastore.AppPreferencesDataStore
import com.example.core.network.NetworkClientFactory
import com.example.core.security.KeystoreCredentialStore
import com.example.data.repository.BDPayHubRepositoryImpl
import com.example.domain.model.DeviceAuthStatus
import com.example.domain.model.SyncState
import com.example.domain.repository.SmsIngestionOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Automated Repository, MockWebServer Networking, Offline Queue, and Security Test Suite
 * covering all requirements in Sections 10, 11, 12, 16, and 20.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BDPayHubRepositoryAndSecurityTest {

    private lateinit var context: Context
    private lateinit var mockWebServer: MockWebServer
    private lateinit var database: BDPayHubDatabase
    private lateinit var credentialStore: KeystoreCredentialStore
    private lateinit var preferencesDataStore: AppPreferencesDataStore
    private lateinit var repository: BDPayHubRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockWebServer = MockWebServer()
        mockWebServer.start()

        database = BDPayHubDatabase.createInMemory(context)
        credentialStore = KeystoreCredentialStore(context)
        credentialStore.clearAllCredentials()
        preferencesDataStore = AppPreferencesDataStore(context)

        val baseUrl = mockWebServer.url("/").toString()
        val okHttp = NetworkClientFactory.createOkHttpClient(fallbackInterceptor = null)
        val supabaseApi = NetworkClientFactory.createSupabaseAuthApi(baseUrl, okHttp)
        val backendApi = NetworkClientFactory.createBackendApi(baseUrl, okHttp)

        repository = BDPayHubRepositoryImpl(
            supabaseAuthApiProvider = { supabaseApi },
            backendApiProvider = { backendApi },
            paymentEventDao = database.paymentEventDao(),
            credentialStore = credentialStore,
            preferencesDataStore = preferencesDataStore
        )
    }

    @After
    fun tearDown() {
        database.close()
        mockWebServer.shutdown()
    }

    @Test
    fun `security test - credentials are encrypted in storage and never stored in plaintext`() {
        val secretAccessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.super_secret_payload.sig"
        val secretDeviceCred = "bdp_dev_cred_top_secret_998877"

        credentialStore.saveSupabaseSession(
            accessToken = secretAccessToken,
            refreshToken = "refresh_secret_123",
            expiresAtEpochSeconds = 9999999999L,
            userEmail = "merchant@bdpayhub.com",
            merchantId = "merch_1"
        )
        credentialStore.saveDeviceCredential(
            deviceId = "dev_1",
            deviceCredential = secretDeviceCred,
            deviceName = "Counter Device"
        )

        val rawAccessPersisted = credentialStore.inspectRawPersistedValue(KeystoreCredentialStore.KEY_ACCESS_TOKEN)
        val rawDevicePersisted = credentialStore.inspectRawPersistedValue(KeystoreCredentialStore.KEY_DEVICE_CREDENTIAL)

        assertNotNull(rawAccessPersisted)
        assertNotNull(rawDevicePersisted)
        assertTrue(rawAccessPersisted!!.startsWith("ENCv1:"))
        assertTrue(rawDevicePersisted!!.startsWith("ENCv1:"))
        assertFalse(rawAccessPersisted.contains(secretAccessToken))
        assertFalse(rawDevicePersisted.contains(secretDeviceCred))

        // Verify decryption round-trip
        assertEquals(secretAccessToken, credentialStore.getAccessToken())
        assertEquals(secretDeviceCred, credentialStore.getDeviceCredential())
    }

    @Test
    fun `security test - RedactingLogger masks tokens, phone numbers, and raw SMS`() {
        val rawLog = "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig for 01712345678 message: You have received Tk 500.00 from 01712345678"
        val sanitized = RedactingLogger.sanitize(rawLog)

        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertFalse(sanitized.contains("01712345678"))
        assertFalse(sanitized.contains("You have received Tk 500.00"))
        assertTrue(sanitized.contains("[REDACTED_TOKEN]") || sanitized.contains("[REDACTED_JWT]"))
    }

    @Test
    fun `authenticate, register device, parse SMS, prevent duplicate, and ingest to backend`() = runBlocking {
        // 1. Mock Supabase OAuth token exchange + Backend Merchant Profile
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "access_token": "jwt_valid_token_123",
                  "refresh_token": "refresh_valid_123",
                  "expires_in": 3600,
                  "token_type": "bearer",
                  "user": { "id": "u1", "email": "merchant@bdpayhub.com" }
                }
                """.trimIndent()
            )
        )
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "merchant_id": "merch_bd_100",
                  "business_name": "Sylhet Mart",
                  "google_email": "merchant@bdpayhub.com",
                  "plan_name": "Starter Plan",
                  "max_allowed_devices": 1,
                  "active_device_count": 1,
                  "monthly_event_quota": 1000,
                  "used_events_count": 10,
                  "is_subscription_active": true,
                  "configured_wallets": [
                    {
                      "provider": "bkash",
                      "wallet_number": "01700998877",
                      "account_type": "PERSONAL",
                      "is_configured": true,
                      "is_active": true,
                      "backend_verified": true
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val authResult = repository.authenticateWithGoogle("google_id_token")
        assertTrue(authResult is AppResult.Success)
        assertEquals("Sylhet Mart", (authResult as AppResult.Success).data.businessName)

        // 2. Mock Device Registration
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "device_id": "dev_bd_555",
                  "merchant_id": "merch_bd_100",
                  "device_name": "Pixel Agent",
                  "authorization_status": "AUTHORIZED",
                  "device_credential": "cred_secret_xyz",
                  "registered_at": 1763339400000,
                  "message": "Authorized"
                }
                """.trimIndent()
            )
        )
        val regResult = repository.registerAndAuthorizeDevice("Pixel Agent", "Android 16")
        assertTrue(regResult is AppResult.Success)

        // 3. Process bKash P2P SMS locally — must start as LOCAL_PENDING (never auto-marked successful on client!)
        val sms = "You have received Tk 500.00 from 01712345678. Ref COURSE2026. Fee Tk 0.00. Balance Tk 2,500.00. TrxID TRX123456789 at 17/11/2026 00:30"
        val ingestOutcome = repository.processIncomingSms("bKash", sms)
        assertTrue(ingestOutcome is SmsIngestionOutcome.Queued)
        val queuedRecord = (ingestOutcome as SmsIngestionOutcome.Queued).event
        assertEquals(SyncState.LOCAL_PENDING, queuedRecord.syncState)
        assertFalse(queuedRecord.syncState.isServerVerifiedPayment)
        assertEquals("01700998877", queuedRecord.receiverNumber) // Resolved from verified merchant wallet

        // 4. Duplicate submission with same TrxID must be blocked locally
        val duplicateOutcome = repository.processIncomingSms("bKash", sms)
        assertTrue(duplicateOutcome is SmsIngestionOutcome.DuplicateIgnored)

        // 5. Sync with backend -> verify payload has NO raw SMS and status updates from backend response
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "event_id": "evt_001",
                  "transaction_id": "TRX123456789",
                  "processing_status": "SERVER_MATCHED",
                  "duplicate": false,
                  "matched_intent_id": "intent_99",
                  "message": "Verified and matched with intent_99",
                  "processed_at": 1763339500000
                }
                """.trimIndent()
            )
        )
        val syncResult = repository.syncPendingEvents()
        assertTrue(syncResult is AppResult.Success)
        assertEquals(1, (syncResult as AppResult.Success).data.matchedCount)

        // Verify the HTTP request sent to /api/v1/transactions/ingest
        mockWebServer.takeRequest() // auth
        mockWebServer.takeRequest() // profile
        mockWebServer.takeRequest() // device register
        val ingestHttpReq = mockWebServer.takeRequest()
        assertEquals("/api/v1/transactions/ingest", ingestHttpReq.path)
        assertEquals("Bearer cred_secret_xyz", ingestHttpReq.getHeader("Authorization"))
        val requestBody = ingestHttpReq.body.readUtf8()
        assertFalse("Raw SMS must NEVER be uploaded", requestBody.contains("You have received Tk"))
        assertTrue(requestBody.contains("\"transaction_id\":\"TRX123456789\""))

        val updatedRecord = repository.getEventById(queuedRecord.localId)
        assertEquals(SyncState.SERVER_MATCHED, updatedRecord?.syncState)
    }

    @Test
    fun `device revocation stops accepted event ingestion and halts sync while preserving history`() = runBlocking {
        credentialStore.saveSupabaseSession("tok", "ref", 9999999999L, "m@bdpayhub.com", "merch_1")
        credentialStore.saveDeviceCredential("dev_1", "cred_1", "Agent 1")
        preferencesDataStore.setDeviceAuthStatus(DeviceAuthStatus.AUTHORIZED)

        // Queue one valid event before revocation
        val sms1 = "You have received Tk 600.00 from 01677889900. Fee Tk 0.00. Balance Tk 5,600.00. TrxID UP4492018A at 17/11/2026 12:05."
        val firstOutcome = repository.processIncomingSms("upay", sms1)
        assertTrue(firstOutcome is SmsIngestionOutcome.Queued)

        // Backend returns 403 DEVICE_REVOKED during sync
        mockWebServer.enqueue(
            MockResponse().setResponseCode(403).setBody(
                """{"error_code":"DEVICE_REVOKED","message":"Revoked"}"""
            )
        )
        val syncOutcome = repository.syncPendingEvents()
        assertTrue(syncOutcome is AppResult.Error)
        assertEquals(AppErrorType.DEVICE_REVOKED, (syncOutcome as AppResult.Error).errorType)

        // Verify local event was NOT deleted
        val eventsInHistory = repository.allEventsFlow.first()
        assertEquals(1, eventsInHistory.size)
        assertNotEquals(0, eventsInHistory.size)

        // Verify subsequent SMS ingestion is rejected while device is REVOKED
        val sms2 = "You have received Tk 800.00 from 01677889900. Fee Tk 0.00. Balance Tk 6,400.00. TrxID UP9999999B at 17/11/2026 12:10."
        val secondOutcome = repository.processIncomingSms("upay", sms2)
        assertTrue(secondOutcome is SmsIngestionOutcome.RejectedByDeviceState)
    }
}
