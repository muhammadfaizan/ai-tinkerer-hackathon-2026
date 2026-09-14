package com.example.intune.data

import android.content.Context
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.goalDataStore by preferencesDataStore("goals")
private val goalsKey = stringSetPreferencesKey("items")

class GoalsRepository(private val context: Context) {
    val goals = context.goalDataStore.data.map { it[goalsKey]?.toList().orEmpty() }

    suspend fun save(goals: List<String>) {
        context.goalDataStore.edit { it[goalsKey] = goals.map(String::trim).filter(String::isNotBlank).take(3).toSet() }
    }
}
