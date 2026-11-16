# BDPayHub — Android Payment Event Collection & Verification Agent

**Tagline:** *Every Payment. Verified.*

## 1. Project Overview
BDPayHub for Android is a non-custodial, privacy-preserving payment-event collection agent built for merchants using the BDPayHub SaaS infrastructure in Bangladesh.
- **What it does:** Authenticates merchants via Supabase Google Sign-In, registers and authorizes the Android device using Android Keystore-backed credentials, parses supported bKash, Nagad, Rocket (`16216`), and upay notifications **100% locally on-device**, queues structured payment events in a persistent Room offline queue, and submits them over HTTPS to the BDPayHub Rust + Axum backend (`POST /api/v1/transactions/ingest`).
- **What it never does:** The app never receives, holds, transfers, or settles customer money. It never uploads raw SMS messages or personal conversations, and it never marks a payment as verified on the client alone.

---

## 2. Architecture Explanation
The application follows **Clean Architecture + MVVM** with unidirectional data flow (`StateFlow`):
- **Presentation Layer (`com.example.presentation.*`)**: Jetpack Compose UI screens, reusable Material 3 components, and `BDPayHubViewModel`.
- **Domain Layer (`com.example.domain.*`)**: Immutable domain models (`ParsedPaymentEvent`, `SyncState`, `DeviceAuthStatus`), repository contracts (`BDPayHubRepository`), and business semantics.
- **Data Layer (`com.example.data.*`)**: Provider-specific SMS parsers (`BkashSmsParser`, `NagadSmsParser`, `RocketSmsParser`, `UpaySmsParser`) and `BDPayHubRepositoryImpl`.
- **Core Layer (`com.example.core.*`)**: Android Keystore AES-256-GCM encryption (`KeystoreCredentialStore`), Room database (`BDPayHubDatabase`), DataStore preferences (`AppPreferencesDataStore`), Retrofit/OkHttp networking (`BDPayHubBackendApi`), and sensitive-field log redaction (`RedactingLogger`).
- **Service Layer (`com.example.service.*`)**: Event-driven `SmsReceiver`, `BootCompletedReceiver`, `DeviceSyncWorker` (WorkManager), and lockscreen-private `NotificationHelper`.

---

## 3. Folder Structure
```text
app/src/main/java/com/example/
├── core/
│   ├── common/          # AppResult, AppErrorType, RedactingLogger
│   ├── config/          # EnvironmentConfig (Dev, Staging, Production)
│   ├── database/        # Room Entities, PaymentEventDao, BDPayHubDatabase
│   ├── datastore/       # AppPreferencesDataStore
│   ├── di/              # AppContainer
│   ├── network/         # Retrofit APIs, DTOs, SecurityHeadersAndRedactingInterceptor
│   └── security/        # SecureCredentialStore, KeystoreCredentialStore (AES-256-GCM)
├── data/
│   ├── repository/      # BDPayHubRepositoryImpl
│   └── sms/             # BkashSmsParser, NagadSmsParser, RocketSmsParser, UpaySmsParser
├── domain/
│   ├── model/           # DomainModels (ParsedPaymentEvent, SyncState, etc.)
│   └── repository/      # BDPayHubRepository contract
├── service/
│   ├── BootCompletedReceiver.kt
│   ├── DeviceSyncWorker.kt
│   ├── NotificationHelper.kt
│   └── SmsReceiver.kt
├── presentation/
│   ├── components/      # Badges, cards, banners
│   ├── dashboard/       # DashboardScreen
│   ├── devices/         # DeviceAndParserScreen
│   ├── onboarding/      # AuthAndOnboardingScreen
│   ├── settings/        # SettingsScreen
│   ├── transactions/    # TransactionHistoryScreen & Details
│   └── BDPayHubViewModel.kt
├── BDPayHubApplication.kt
└── MainActivity.kt
```

---

## 4. Technology Stack
- **Language:** Kotlin 2.2
- **UI:** Jetpack Compose + Material Design 3 + Custom Bundled Fonts (`Space Grotesk`, `Plus Jakarta Sans`, `JetBrains Mono`)
- **Async:** Kotlin Coroutines + `StateFlow`
- **Local Persistence:** Room Database (offline queue & diagnostics) + Jetpack DataStore (preferences)
- **Background Sync:** AndroidX WorkManager (`CoroutineWorker` with exponential backoff)
- **Networking & Serialization:** Retrofit 2 + OkHttp 4 + Kotlinx Serialization JSON
- **Security:** Android Keystore (`AES/GCM/NoPadding` 256-bit) + Network Security Config (HTTPS-only)
- **Testing:** JUnit 4, Robolectric, Compose UI Test, MockWebServer, Kotlin Coroutines Test

---

## 5. Environment Setup
Environment URLs are configured via `BuildConfig` and `.env` / `.env.example` (managed via the Secrets panel in AI Studio):
- `DEVELOPMENT`: `https://dev-api.bdpayhub.com`
- `STAGING`: `https://staging-api.bdpayhub.com`
- `PRODUCTION`: `https://api.bdpayhub.com`

---

## 6. Supabase Google Sign-In Configuration
- Merchants sign in exclusively using Google OAuth via Supabase Auth (`POST /auth/v1/token?grant_type=id_token`).
- Email/password, phone/password, and separate local accounts are prohibited.
- If the authenticated Google account has no merchant profile on the backend (`HTTP 404`), the app displays the **Merchant Registration Required** screen directing the user to complete registration on the BDPayHub website.

---

## 7. Backend API Integration
- **Merchant Profile:** `GET /api/v1/merchant/profile` (`Authorization: Bearer SUPABASE_ACCESS_TOKEN`)
- **Device Registration:** `POST /api/v1/devices/register` (`Authorization: Bearer SUPABASE_ACCESS_TOKEN`)
- **Device Status:** `GET /api/v1/devices/{deviceId}/status` (`Authorization: Bearer DEVICE_CREDENTIAL`)
- **Transaction Ingestion:** `POST /api/v1/transactions/ingest` (`Authorization: Bearer DEVICE_CREDENTIAL`)

---

## 8. Device Authorization Flow
1. Merchant signs in with Google.
2. App requests device registration with a user-defined device label, Android version, and app version (never using IMEI or restricted hardware identifiers).
3. Backend verifies subscription limits and issues a revocable `device_credential`.
4. App encrypts `device_credential` with Android Keystore (`AES/GCM/NoPadding`) before storing.
5. If a device is revoked (`HTTP 403`), event ingestion and background synchronization stop immediately while preserving local event history.

---

## 9. SMS Permission Setup & Distribution Policy
- Requests only `RECEIVE_SMS` (along with `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, and `RECEIVE_BOOT_COMPLETED`).
- Displays a transparent consent screen explaining which sender IDs are parsed locally and guaranteeing that raw SMS and personal messages are never uploaded.
- **Distribution Note:** Google Play restricts `RECEIVE_SMS` permissions to eligible default handlers or approved financial companion exceptions. Evaluate Play Store policy eligibility prior to submission, or distribute via managed enterprise/merchant channels.

---

## 10. Supported Provider SMS Patterns & Known Limitations
- **bKash (`bKash`):** Personal Send Money (`SEND_MONEY_RECEIVED`), Personal Agent Cash-In (`AGENT_CASH_IN_RECEIVED`), Agent Customer Cash-Out (`CUSTOMER_CASH_OUT_RECEIVED`).
- **Nagad (`NAGAD`):** Personal P2P (`SEND_MONEY_RECEIVED`), Personal Agent Cash-In with `Uddokta` (`AGENT_CASH_IN_RECEIVED`), Agent Customer Cash-Out (`CUSTOMER_CASH_OUT_RECEIVED`).
- **Rocket (`16216`):** Personal P2P (`SEND_MONEY_RECEIVED`), Personal Agent Cash-In (`AGENT_CASH_IN_RECEIVED`), Agent Customer Cash-Out (`CUSTOMER_CASH_OUT_RECEIVED`) using 12-digit Rocket account numbers.
- **upay (`upay`):** Personal Received Payment (`SEND_MONEY_RECEIVED`) and Personal Agent Cash-In (`AGENT_CASH_IN_RECEIVED`).
- **Unsupported Format Limitation:** upay Agent Cash-Out notifications are intentionally unsupported and logged to local diagnostics without uploading raw SMS.

---

## 11. Parser Testing & Verification
Run the automated test suite via Gradle:
```bash
gradle :app:testDebugUnitTest
```
The test suite covers all 11 official SMS patterns, negative validation cases, Keystore encryption, log redaction, duplicate prevention, offline queueing, device revocation, and Compose UI journeys.
