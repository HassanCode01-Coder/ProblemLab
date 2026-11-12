package com.hassandev.problemlab

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.functions.Functions
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.plugins.HttpTimeout
import io.github.jan.supabase.annotations.SupabaseInternal

@OptIn(SupabaseInternal::class)
val supabase = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_KEY
) {
    requestTimeout = 60.seconds
    httpConfig {
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }
    }
    install(Postgrest)
    install(Storage)
    install(Functions)
    install(Auth) {
        host = "login"
        scheme = "problemlab"
    }
}