package com.synthbyte.scanmate.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.synthbyte.scanmate.domain.GeminiModels

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode(val storageValue: String, val label: String, val description: String) {
    SYSTEM("system", "System / Device", "Follow the phone theme automatically"),
    LIGHT("light", "Light", "Always use the light theme"),
    DARK("dark", "Dark", "Always use the dark theme");

    companion object {
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

class SettingsRepository(private val context: Context) {

    companion object {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL_ID = stringPreferencesKey("gemini_model_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val DEFAULT_WORKSPACE = stringPreferencesKey("default_workspace")
    }

    val geminiApiKeyFlow: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[GEMINI_API_KEY] }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data
        .map { preferences -> ThemeMode.fromStorage(preferences[THEME_MODE]) }

    val geminiModelIdFlow: Flow<String> = context.dataStore.data
        .map { preferences -> GeminiModels.modelIdOrDefault(preferences[GEMINI_MODEL_ID]) }

    val onboardingCompleteFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[ONBOARDING_COMPLETE] ?: false }

    val defaultWorkspaceFlow: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[DEFAULT_WORKSPACE]?.takeIf { it.isNotBlank() } ?: "Inbox" }

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[GEMINI_API_KEY] = apiKey
        }
    }

    suspend fun clearApiKey() {
        context.dataStore.edit { preferences ->
            preferences.remove(GEMINI_API_KEY)
        }
    }

    suspend fun saveGeminiModel(modelId: String) {
        context.dataStore.edit { preferences ->
            preferences[GEMINI_MODEL_ID] = GeminiModels.modelIdOrDefault(modelId)
        }
    }

    suspend fun saveThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = themeMode.storageValue
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean = true) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETE] = complete
        }
    }

    suspend fun saveDefaultWorkspace(workspace: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_WORKSPACE] = workspace.trim().ifBlank { "Inbox" }
        }
    }
}
