package com.example.core.config

import com.example.BuildConfig
import com.example.domain.model.AppEnvironment

/**
 * Environment-specific configuration for BDPayHub Rust + Axum REST API and Supabase Auth.
 * Supports Development, Staging, and Production without hardcoding private secrets.
 */
object EnvironmentConfig {

    const val APP_VERSION = BuildConfig.VERSION_NAME
    const val PARSER_VERSION = BuildConfig.PARSER_ENGINE_VERSION

    fun getApiBaseUrl(environment: AppEnvironment, customOverrideUrl: String? = null): String {
        if (!customOverrideUrl.isNullOrBlank()) {
            return customOverrideUrl.trimEnd('/') + "/"
        }
        val base = when (environment) {
            AppEnvironment.DEVELOPMENT -> BuildConfig.DEFAULT_DEV_API_URL
            AppEnvironment.STAGING -> BuildConfig.DEFAULT_STAGING_API_URL
            AppEnvironment.PRODUCTION -> BuildConfig.DEFAULT_PROD_API_URL
        }
        return base.trimEnd('/') + "/"
    }

    fun getSupabaseUrl(customOverrideUrl: String? = null): String {
        if (!customOverrideUrl.isNullOrBlank()) {
            return customOverrideUrl.trimEnd('/') + "/"
        }
        return BuildConfig.DEFAULT_SUPABASE_URL.trimEnd('/') + "/"
    }

    fun isHttpsUrl(url: String): Boolean {
        val trimmed = url.trim().lowercase()
        return trimmed.startsWith("https://")
    }
}
