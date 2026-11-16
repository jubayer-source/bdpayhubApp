package com.example.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.MerchantProfile
import com.example.presentation.OnboardingStep
import com.example.presentation.components.NonCustodialTrustBanner

/**
 * Complete 6-stage Authentication, Device Authorization, and SMS Permission Consent Onboarding flow
 * defined in Sections 4, 5, and 6.
 */
@Composable
fun AuthAndOnboardingScreen(
    step: OnboardingStep,
    isLoading: Boolean,
    deviceNameInput: String,
    merchantProfile: MerchantProfile?,
    smsPermissionGranted: Boolean,
    onStepChange: (OnboardingStep) -> Unit,
    onGoogleSignIn: (simulateUnregistered: Boolean) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onAuthorizeDevice: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onSkipSmsPermissionGracefully: () -> Unit,
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("onboarding_screen_root")
    ) {
        Column(modifier = Modifier.widthIn(max = 560.dp)) {
            // Top step indicator bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "BDPayHub Logo",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "BDPayHub",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Every Payment. Verified.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                TextButton(
                    onClick = onFinishOnboarding,
                    modifier = Modifier.testTag("return_to_dashboard_button")
                ) {
                    Text("Dashboard →")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (step) {
                OnboardingStep.WELCOME -> WelcomeStepContent(
                    onProceedToSignIn = { onStepChange(OnboardingStep.GOOGLE_SIGN_IN) }
                )

                OnboardingStep.GOOGLE_SIGN_IN -> GoogleSignInStepContent(
                    onSignInVerifiedMerchant = { onGoogleSignIn(false) },
                    onSignInUnregisteredUser = { onGoogleSignIn(true) },
                    onBack = { onStepChange(OnboardingStep.WELCOME) }
                )

                OnboardingStep.AUTH_LOADING,
                OnboardingStep.ACCOUNT_VALIDATION -> AuthValidationStepContent(
                    isAccountValidation = step == OnboardingStep.ACCOUNT_VALIDATION
                )

                OnboardingStep.NO_MERCHANT_PROFILE -> NoMerchantProfileStepContent(
                    onBackToSignIn = { onStepChange(OnboardingStep.GOOGLE_SIGN_IN) }
                )

                OnboardingStep.DEVICE_AUTHORIZATION -> DeviceAuthorizationStepContent(
                    merchantProfile = merchantProfile,
                    deviceNameInput = deviceNameInput,
                    isLoading = isLoading,
                    onDeviceNameChange = onDeviceNameChange,
                    onAuthorizeDevice = onAuthorizeDevice,
                    onBack = { onStepChange(OnboardingStep.GOOGLE_SIGN_IN) }
                )

                OnboardingStep.SMS_PERMISSION_CONSENT -> SmsPermissionConsentStepContent(
                    smsPermissionGranted = smsPermissionGranted,
                    onRequestSmsPermission = onRequestSmsPermission,
                    onContinueWithoutPermission = onSkipSmsPermissionGracefully
                )

                OnboardingStep.ONBOARDING_SUCCESS -> OnboardingSuccessStepContent(
                    deviceName = deviceNameInput,
                    smsPermissionGranted = smsPermissionGranted,
                    onEnterDashboard = onFinishOnboarding
                )
            }
        }
    }
}

@Composable
private fun WelcomeStepContent(
    onProceedToSignIn: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(
                text = "Authorized Merchant Payment-Event Agent",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "BDPayHub securely parses supported bKash, Nagad, Rocket, and upay payment notifications locally on your device and synchronizes structured payment events with the BDPayHub Rust backend for independent verification.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            NonCustodialTrustBanner()
            Spacer(modifier = Modifier.height(20.dp))

            FeatureBullet(
                title = "1. Supabase Google OAuth Only",
                subtitle = "Uses your existing BDPayHub merchant Google account with backend JWT verification."
            )
            Spacer(modifier = Modifier.height(10.dp))
            FeatureBullet(
                title = "2. Android Keystore Device Credential",
                subtitle = "Hardware-backed AES-256-GCM encryption for device authorization credentials."
            )
            Spacer(modifier = Modifier.height(10.dp))
            FeatureBullet(
                title = "3. 100% On-Device SMS Parsing",
                subtitle = "Raw SMS inbox contents and personal messages are never collected or uploaded."
            )

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onProceedToSignIn,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("welcome_get_started_button")
            ) {
                Text("Proceed to Merchant Sign-In")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun GoogleSignInStepContent(
    onSignInVerifiedMerchant: () -> Unit,
    onSignInUnregisteredUser: () -> Unit,
    onBack: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Merchant Authentication",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sign in with the same Google account registered on your BDPayHub website dashboard. Email/password, phone/password, and separate local accounts are disabled by security policy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onSignInVerifiedMerchant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_sign_in_button")
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Continue with Google (Merchant Account)",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onSignInUnregisteredUser,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("google_sign_in_unregistered_button")
            ) {
                Text("Test Google Account Without Merchant Profile")
            }

            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Supabase OAuth session JWT is sent via Authorization: Bearer header and validated independently by the BDPayHub Rust backend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthValidationStepContent(
    isAccountValidation: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = if (isAccountValidation) {
                    "Validating Merchant Profile with Backend..."
                } else {
                    "Authenticating with Supabase Google OAuth..."
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Verifying JWT signature, active subscription plan, and configured wallet bindings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoMerchantProfileStepContent(
    onBackToSignIn: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(18.dp))
            .testTag("no_merchant_profile_card")
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "No Merchant Profile",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Merchant Registration Required",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "The authenticated Google account does not have an existing BDPayHub merchant profile. Per security policy, merchant profiles cannot be created from the Android agent.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Please complete merchant registration and wallet verification on the BDPayHub website first, then sign in here using that Google account.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onBackToSignIn,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("back_to_google_signin_button")
            ) {
                Text("Sign In with Another Google Account")
            }
        }
    }
}

@Composable
private fun DeviceAuthorizationStepContent(
    merchantProfile: MerchantProfile?,
    deviceNameInput: String,
    isLoading: Boolean,
    onDeviceNameChange: (String) -> Unit,
    onAuthorizeDevice: () -> Unit,
    onBack: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Device Registration & Authorization",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (merchantProfile != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Verified Merchant: ${merchantProfile.businessName}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Plan: ${merchantProfile.planName} • Allowed Devices: ${merchantProfile.maxAllowedDevices}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = deviceNameInput,
                onValueChange = onDeviceNameChange,
                label = { Text("Authorized Device Label") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("device_name_input")
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "No IMEI or hardware phone numbers are collected. The backend issues a revocable device credential encrypted in Android Keystore (AES-256-GCM).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onAuthorizeDevice,
                enabled = !isLoading && deviceNameInput.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("authorize_device_button")
            ) {
                Icon(Icons.Default.Devices, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isLoading) "Authorizing with Backend..." else "Register & Authorize This Device")
            }
        }
    }
}

@Composable
private fun SmsPermissionConsentStepContent(
    smsPermissionGranted: Boolean,
    onRequestSmsPermission: () -> Unit,
    onContinueWithoutPermission: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .testTag("sms_permission_consent_card")
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Sms,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "SMS Notification Consent & Privacy",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Before requesting RECEIVE_SMS permission, please review how BDPayHub handles notifications:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))
            FeatureBullet(
                title = "Supported Senders Only",
                subtitle = "Processes notifications strictly from bKash ('bKash'), Nagad ('NAGAD'), Rocket ('16216'), and upay ('upay')."
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeatureBullet(
                title = "100% Local On-Device Parsing",
                subtitle = "Extracts structured fields (Provider, Type, Amount, Sender/Agent Number, TrxID, Timestamp, Fee/Comm, Balance)."
            )
            Spacer(modifier = Modifier.height(8.dp))
            FeatureBullet(
                title = "Zero Raw SMS or Personal Message Upload",
                subtitle = "Unrelated personal SMS messages and raw SMS inbox contents are never stored or transmitted."
            )

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRequestSmsPermission,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("grant_sms_permission_button")
            ) {
                Icon(Icons.Default.Security, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (smsPermissionGranted) {
                        "SMS Permission Granted — Continue"
                    } else {
                        "I Understand — Request SMS Permission"
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onContinueWithoutPermission,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("skip_sms_permission_button")
            ) {
                Text("Continue Without SMS Permission (Manual / Sandbox Mode)")
            }
        }
    }
}

@Composable
private fun OnboardingSuccessStepContent(
    deviceName: String,
    smsPermissionGranted: Boolean,
    onEnterDashboard: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Onboarding Complete",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Agent Onboarding Complete",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$deviceName is authorized and backed by Android Keystore encryption. SMS permission status: ${if (smsPermissionGranted) "Active" else "Not Granted"}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onEnterDashboard,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("finish_onboarding_button")
            ) {
                Text("Open BDPayHub Dashboard")
            }
        }
    }
}

@Composable
private fun FeatureBullet(
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
