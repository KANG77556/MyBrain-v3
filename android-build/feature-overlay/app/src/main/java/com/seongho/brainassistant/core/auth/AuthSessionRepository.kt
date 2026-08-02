package com.seongho.brainassistant.core.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PersistedAuthSession(
    val email: String,
    val displayName: String,
)

interface AuthSessionRepository {
    val session: Flow<PersistedAuthSession?>
    suspend fun save(session: PersistedAuthSession)
    suspend fun clear()
}

private val Context.authSessionDataStore by preferencesDataStore(name = "auth_session")

class DataStoreAuthSessionRepository(context: Context) : AuthSessionRepository {
    private val dataStore = context.applicationContext.authSessionDataStore

    override val session: Flow<PersistedAuthSession?> = dataStore.data.map { values ->
        val email = values[EMAIL]?.trim().orEmpty()
        val displayName = values[DISPLAY_NAME]?.trim().orEmpty()
        if (email.isBlank() || displayName.isBlank()) null else PersistedAuthSession(email, displayName)
    }

    override suspend fun save(session: PersistedAuthSession) {
        dataStore.edit {
            it[EMAIL] = session.email
            it[DISPLAY_NAME] = session.displayName
        }
    }

    override suspend fun clear() {
        dataStore.edit {
            it.remove(EMAIL)
            it.remove(DISPLAY_NAME)
        }
    }

    private companion object {
        val EMAIL = stringPreferencesKey("email")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
    }
}
