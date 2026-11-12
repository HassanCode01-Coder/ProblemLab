package com.hassandev.problemlab

import kotlinx.serialization.Serializable
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

@Serializable
data class Sector(
    val id: String,
    val name: String,
    val slug: String
)

suspend fun fetchSectors(): List<Sector> {
    return try {
        supabase.postgrest["sectors"]
            .select(columns = Columns.list("id", "name", "slug"))
            .decodeList<Sector>()
    } catch (e: Exception) {
        emptyList()
    }
}