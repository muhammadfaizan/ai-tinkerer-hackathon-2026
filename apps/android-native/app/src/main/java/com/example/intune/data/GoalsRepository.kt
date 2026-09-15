package com.example.intune.data

import android.content.Context
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.goalDataStore by preferencesDataStore("goals")
private val goalsKey = stringSetPreferencesKey("items")
private val lastNotifiedAtKey = longPreferencesKey("last_notified_at")

class GoalsRepository(private val context: Context) {
    val goals = context.goalDataStore.data.map { it[goalsKey]?.toList().orEmpty() }

    suspend fun save(goals: List<String>) {
        context.goalDataStore.edit { it[goalsKey] = goals.map(String::trim).filter(String::isNotBlank).take(3).toSet() }
    }

    suspend fun lastNotifiedAt(): Long = context.goalDataStore.data.first()[lastNotifiedAtKey] ?: 0L

    suspend fun saveLastNotifiedAt(timestamp: Long) {
        context.goalDataStore.edit { it[lastNotifiedAtKey] = timestamp }
    }
}
