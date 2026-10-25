package com.volta.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.volta.app.domain.settings.SettingsRepository
import com.volta.app.domain.stitching.OutputResolution
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber

class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private val settingsData: Flow<Preferences> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                Timber.e(throwable, "Failed to read settings; falling back to defaults")
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }

    override val outputResolution: Flow<OutputResolution> = settingsData
        .map { preferences ->
            preferences[OUTPUT_RESOLUTION_KEY]?.let { name ->
                runCatching { OutputResolution.valueOf(name) }
                    .onFailure { Timber.w(it, "Unrecognized stored output resolution: $name") }
                    .getOrNull()
            } ?: OutputResolution.STANDARD
        }

    override val autoCheckUpdates: Flow<Boolean> = settingsData
        .map { preferences -> preferences[AUTO_CHECK_UPDATES_KEY] ?: true }
        .catchUpdateSettingError(default = true)

    override val lastUpdateCheckMillis: Flow<Long?> = settingsData
        .map { preferences -> preferences[LAST_UPDATE_CHECK_MILLIS_KEY] }
        .catchUpdateSettingError(default = null)

    override suspend fun setOutputResolution(resolution: OutputResolution) {
        try {
            dataStore.edit { preferences ->
                preferences[OUTPUT_RESOLUTION_KEY] = resolution.name
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist output resolution: $resolution")
        }
    }

    override suspend fun setAutoCheckUpdates(enabled: Boolean) {
        try {
            dataStore.edit { preferences ->
                preferences[AUTO_CHECK_UPDATES_KEY] = enabled
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist auto-check updates: $enabled")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun setLastUpdateCheckMillis(millis: Long) {
        try {
            dataStore.edit { preferences ->
                preferences[LAST_UPDATE_CHECK_MILLIS_KEY] = millis
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist last update check time")
        }
    }

    /**
     * The updater must never crash the app, so its settings fall back to [default] on any read
     * error, not only on [IOException].
     */
    private fun <T> Flow<T>.catchUpdateSettingError(default: T): Flow<T> = catch { throwable ->
        if (throwable is CancellationException) throw throwable
        Timber.e(throwable, "Failed to read update settings; falling back to defaults")
        emit(default)
    }

    private companion object {
        val OUTPUT_RESOLUTION_KEY = stringPreferencesKey("output_resolution")
        val AUTO_CHECK_UPDATES_KEY = booleanPreferencesKey("auto_check_updates")
        val LAST_UPDATE_CHECK_MILLIS_KEY = longPreferencesKey("last_update_check_millis")
    }
}
