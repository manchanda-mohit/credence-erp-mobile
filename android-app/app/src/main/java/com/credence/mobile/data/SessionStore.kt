package com.credence.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "credence_session")

/**
 * Remembers the signed-in user's identity locally (username/fullName/
 * role only — never a password) so a reopened app can silently restore
 * the session via CredenceRepository.restoreSession(), the same
 * "remembered login" pattern Index.html already implements with
 * localStorage for the web app. See LoginViewModel for how this is used
 * on app start.
 */
class SessionStore(private val context: Context) {
    private object Keys {
        val USERNAME = stringPreferencesKey("username")
        val FULL_NAME = stringPreferencesKey("full_name")
        val ROLE = stringPreferencesKey("role")
    }

    val savedUser: Flow<LoginUser?> = context.dataStore.data.map { prefs ->
        val username = prefs[Keys.USERNAME]
        if (username.isNullOrBlank()) {
            null
        } else {
            LoginUser(
                username = username,
                fullName = prefs[Keys.FULL_NAME] ?: "",
                role = prefs[Keys.ROLE] ?: ""
            )
        }
    }

    suspend fun save(user: LoginUser) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USERNAME] = user.username
            prefs[Keys.FULL_NAME] = user.fullName
            prefs[Keys.ROLE] = user.role
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun currentUsername(): String? = savedUser.first()?.username
}
