package app.sorta.files.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class UserPrefs(private val context: Context) {
    private object K {
        val THEME = stringPreferencesKey("theme")
        val DEFAULT_VIEW = stringPreferencesKey("default_view")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden")
        val ADS_REMOVED = booleanPreferencesKey("ads_removed")
        val LAST_TAB = stringPreferencesKey("last_tab")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val theme: Flow<ThemeMode> = context.dataStore.data.map {
        it[K.THEME]?.let { v -> ThemeMode.valueOf(v) } ?: ThemeMode.SYSTEM
    }
    val defaultViewGrid: Flow<Boolean> = context.dataStore.data.map { it[K.DEFAULT_VIEW] == "grid" }
    val showHidden: Flow<Boolean> = context.dataStore.data.map { it[K.SHOW_HIDDEN] ?: false }
    val adsRemoved: Flow<Boolean> = context.dataStore.data.map { it[K.ADS_REMOVED] ?: false }
    val lastTab: Flow<String?> = context.dataStore.data.map { it[K.LAST_TAB] }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[K.ONBOARDING_DONE] ?: false }

    suspend fun setTheme(m: ThemeMode) = context.dataStore.edit { it[K.THEME] = m.name }
    suspend fun setDefaultViewGrid(grid: Boolean) =
        context.dataStore.edit { it[K.DEFAULT_VIEW] = if (grid) "grid" else "list" }
    suspend fun setShowHidden(v: Boolean) = context.dataStore.edit { it[K.SHOW_HIDDEN] = v }
    suspend fun setAdsRemoved(v: Boolean) = context.dataStore.edit { it[K.ADS_REMOVED] = v }
    suspend fun setLastTab(v: String) = context.dataStore.edit { it[K.LAST_TAB] = v }
    suspend fun setOnboardingDone(v: Boolean) = context.dataStore.edit { it[K.ONBOARDING_DONE] = v }
}
