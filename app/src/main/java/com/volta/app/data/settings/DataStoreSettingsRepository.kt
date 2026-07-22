package com.volta.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.volta.app.domain.settings.SettingsRepository
import com.volta.app.domain.stitching.OutputResolution
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber

class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override val outputResolution: Flow<OutputResolution> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                Timber.e(throwable, "Failed to read settings; falling back to defaults")
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences ->
            preferences[OUTPUT_RESOLUTION_KEY]?.let { name ->
                runCatching { OutputResolution.valueOf(name) }
                    .onFailure { Timber.w(it, "Unrecognized stored output resolution: $name") }
                    .getOrNull()
            } ?: OutputResolution.STANDARD
        }

    override suspend fun setOutputResolution(resolution: OutputResolution) {
        try {
            dataStore.edit { preferences ->
                preferences[OUTPUT_RESOLUTION_KEY] = resolution.name
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to persist output resolution: $resolution")
        }
    }

    private companion object {
        val OUTPUT_RESOLUTION_KEY = stringPreferencesKey("output_resolution")
    }
}
