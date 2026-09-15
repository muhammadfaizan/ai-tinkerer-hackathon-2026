package com.example.intune.data

import android.content.Context
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.goalDataStore by preferencesDataStore("goals")
private val goalsKey = stringSetPreferencesKey("items")
private val lastNotifiedAtKey = longPreferencesKey("last_notified_at")
private val soundEnabledKey = booleanPreferencesKey("sound_enabled")
private val routinePermissionPromptedKey = booleanPreferencesKey("routine_permission_prompted")

class GoalsRepository(private val context: Context) {
    val goals = context.goalDataStore.data.map { it[goalsKey]?.toList().orEmpty() }
    val soundEnabled = context.goalDataStore.data.map { it[soundEnabledKey] ?: true }
    val routinePermissionPrompted = context.goalDataStore.data.map { it[routinePermissionPromptedKey] ?: false }

    suspend fun save(goals: List<String>) {
        context.goalDataStore.edit { it[goalsKey] = goals.map(String::trim).filter(String::isNotBlank).take(3).toSet() }
    }

    suspend fun lastNotifiedAt(): Long = context.goalDataStore.data.first()[lastNotifiedAtKey] ?: 0L

    suspend fun saveLastNotifiedAt(timestamp: Long) {
        context.goalDataStore.edit { it[lastNotifiedAtKey] = timestamp }
    }

    suspend fun clearLastNotifiedAt() {
        context.goalDataStore.edit { it.remove(lastNotifiedAtKey) }
    }

    suspend fun saveSoundEnabled(enabled: Boolean) {
        context.goalDataStore.edit { it[soundEnabledKey] = enabled }
    }

    suspend fun markRoutinePermissionPrompted() {
        context.goalDataStore.edit { it[routinePermissionPromptedKey] = true }
    }
}
