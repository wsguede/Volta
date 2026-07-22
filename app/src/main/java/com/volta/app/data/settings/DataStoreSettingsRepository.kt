package com.volta.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.volta.app.domain.stitching.OutputResolution
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override val outputResolution: Flow<OutputResolution> = dataStore.data.map { preferences ->
        preferences[OUTPUT_RESOLUTION_KEY]
            ?.let { name -> runCatching { OutputResolution.valueOf(name) }.getOrNull() }
            ?: OutputResolution.STANDARD
    }

    override suspend fun setOutputResolution(resolution: OutputResolution) {
        dataStore.edit { preferences ->
            preferences[OUTPUT_RESOLUTION_KEY] = resolution.name
        }
    }

    private companion object {
        val OUTPUT_RESOLUTION_KEY = stringPreferencesKey("output_resolution")
    }
}
