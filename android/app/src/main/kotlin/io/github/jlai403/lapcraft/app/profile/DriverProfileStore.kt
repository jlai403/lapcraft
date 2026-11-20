package io.github.jlai403.lapcraft.app.profile

import io.github.jlai403.lapcraft.core.profile.DriverProfile

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DriverProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun owner(): DriverProfile? {
        val psnId = ownerPsnId() ?: return null
        return profile(psnId) ?: DriverProfile(psnId, psnId, null).also(::save)
    }

    fun setOwner(profile: DriverProfile) {
        val normalized = normalize(profile)
        if (normalized.psnId.isEmpty()) return
        save(normalized)
        prefs.edit().putString(KEY_OWNER, normalized.psnId).apply()
    }

    fun profiles(): List<DriverProfile> = readProfiles().sortedBy { it.nickname.lowercase() }

    fun profile(psnId: String): DriverProfile? {
        val key = psnId.trim().lowercase()
        if (key.isEmpty()) return null
        return readProfiles().firstOrNull { it.psnId.lowercase() == key }
    }

    fun save(profile: DriverProfile) {
        val normalized = normalize(profile)
        if (normalized.psnId.isEmpty()) return
        val profiles = readProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.psnId.equals(normalized.psnId, ignoreCase = true) }
        val existing = profiles.getOrNull(index)
        val merged = if (existing == null) {
            normalized
        } else {
            normalized.copy(
                dr = normalized.dr ?: existing.dr,
                sr = normalized.sr ?: existing.sr,
                fetchedAtMs = normalized.fetchedAtMs ?: existing.fetchedAtMs,
            )
        }
        if (index >= 0) profiles[index] = merged else profiles.add(merged)
        writeProfiles(profiles)
    }

    fun remove(psnId: String) {
        val target = psnId.trim()
        if (target.isEmpty()) return
        writeProfiles(readProfiles().filterNot { it.psnId.equals(target, ignoreCase = true) })
        if (prefs.getString(KEY_OWNER, null)?.equals(target, ignoreCase = true) == true) {
            prefs.edit().remove(KEY_OWNER).apply()
        }
    }

    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) {
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isEmpty()) {
                prefs.edit().remove(KEY_API_KEY).apply()
            } else {
                prefs.edit().putString(KEY_API_KEY, trimmed).apply()
            }
        }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun normalize(profile: DriverProfile): DriverProfile {
        val psnId = profile.psnId.trim()
        val nickname = profile.nickname.trim().ifEmpty { psnId }
        val countryCode = profile.countryCode
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.length == 2 && it.all { char -> char in 'A'..'Z' } }
        val dr = profile.dr?.trim()?.uppercase()?.takeIf { it.isNotEmpty() && it.length <= 3 }
        val sr = profile.sr?.trim()?.uppercase()?.takeIf { it.isNotEmpty() && it.length <= 3 }
        return DriverProfile(psnId, nickname, countryCode, dr, sr, profile.fetchedAtMs)
    }

    private fun ownerPsnId(): String? {
        val current = prefs.getString(KEY_OWNER, null)?.trim().orEmpty()
        if (current.isNotEmpty()) return current
        return null
    }

    private fun readProfiles(): List<DriverProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<DriverProfile>>(raw) }.getOrDefault(emptyList())
    }

    private fun writeProfiles(profiles: List<DriverProfile>) {
        prefs.edit().putString(KEY_PROFILES, json.encodeToString(profiles)).apply()
    }

    private companion object {
        const val PREFS = "driver_profile"
        const val KEY_OWNER = "owner_psn"
        const val KEY_PROFILES = "profiles_json"
        const val KEY_LEGACY_PSN = "psn_id"
        const val KEY_LEGACY_NICKNAME = "nickname"
        const val KEY_LEGACY_COUNTRY = "country_code"
        const val KEY_API_KEY = "gridstats_api_key"
    }
}
