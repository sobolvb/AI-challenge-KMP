package com.aichallengekmp.model

import kotlinx.serialization.Serializable

/**
 * Профиль пользователя с персональными настройками
 */
@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val displayName: String,
    val role: String? = null,
    val preferences: Preferences = Preferences(),
    val workContext: WorkContext = WorkContext(),
    val communication: CommunicationStyle = CommunicationStyle()
)

@Serializable
data class Preferences(
    val language: String = "ru",
    val timezone: String = "Europe/Moscow",
    val codeStyle: String = "kotlin",
    val favoriteTools: List<String> = emptyList(),
    val habits: List<String> = emptyList()
)

@Serializable
data class WorkContext(
    val currentProjects: List<String> = emptyList(),
    val techStack: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val goals: List<String> = emptyList()
)

@Serializable
data class CommunicationStyle(
    val tone: String = "professional", // professional, casual, friendly
    val verbosity: String = "balanced", // concise, balanced, detailed
    val useEmojis: Boolean = false,
    val explainComplexThings: Boolean = true
)
