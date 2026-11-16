package com.example.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SupabaseAuthApi {

    @POST("auth/v1/token")
    suspend fun signInWithGoogleIdToken(
        @Query("grant_type") grantType: String = "id_token",
        @Body request: SupabaseGoogleAuthRequest
    ): Response<SupabaseSessionResponse>

    @POST("auth/v1/token")
    suspend fun refreshSession(
        @Query("grant_type") grantType: String = "refresh_token",
        @Body request: SupabaseRefreshRequest
    ): Response<SupabaseSessionResponse>
}

interface BDPayHubBackendApi {

    @GET("api/v1/merchant/profile")
    suspend fun getMerchantProfile(
        @Header("Authorization") bearerAccessToken: String
    ): Response<MerchantProfileResponse>

    @POST("api/v1/devices/register")
    suspend fun registerDevice(
        @Header("Authorization") bearerAccessToken: String,
        @Body request: DeviceRegistrationRequest
    ): Response<DeviceRegistrationResponse>

    @GET("api/v1/devices/{deviceId}/status")
    suspend fun getDeviceStatus(
        @Header("Authorization") bearerDeviceCredential: String,
        @Path("deviceId") deviceId: String
    ): Response<DeviceStatusResponse>

    /**
     * Section 10.3: Payment event ingestion
     * POST /api/v1/transactions/ingest
     * Authorization: Bearer DEVICE_CREDENTIAL
     */
    @POST("api/v1/transactions/ingest")
    suspend fun ingestTransactionEvent(
        @Header("Authorization") bearerDeviceCredential: String,
        @Body request: TransactionIngestRequest
    ): Response<TransactionIngestResponse>
}
