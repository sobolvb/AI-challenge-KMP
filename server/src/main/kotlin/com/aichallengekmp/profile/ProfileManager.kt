package com.aichallengekmp.profile

import com.aichallengekmp.model.UserProfile
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Менеджер профилей пользователя
 */
class ProfileManager {
    private val logger = LoggerFactory.getLogger(ProfileManager::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    // Известные профили в classpath
    private val knownProfileIds = listOf("default", "developer", "student")

    /**
     * Загрузить все доступные профили
     */
    fun loadAllProfiles(): List<UserProfile> {
        val profiles = mutableListOf<UserProfile>()

        knownProfileIds.forEach { profileId ->
            loadProfile(profileId)?.let { profiles.add(it) }
        }

        logger.info("📋 Загружено профилей: ${profiles.size}")
        return profiles
    }

    /**
     * Загрузить конкретный профиль по ID
     */
    fun loadProfile(profileId: String): UserProfile? {
        return try {
            // Пытаемся загрузить из classpath
            val resourcePath = "/profiles/$profileId.json"
            val inputStream = ProfileManager::class.java.getResourceAsStream(resourcePath)

            if (inputStream == null) {
                logger.warn("⚠️ Профиль не найден в classpath: $resourcePath")
                return null
            }

            val profileJson = inputStream.bufferedReader().use { it.readText() }
            val profile = json.decodeFromString<UserProfile>(profileJson)
            logger.info("✅ Загружен профиль: ${profile.displayName} (${profile.id})")
            profile
        } catch (e: Exception) {
            logger.error("❌ Ошибка загрузки профиля $profileId: ${e.message}", e)
            null
        }
    }
}
