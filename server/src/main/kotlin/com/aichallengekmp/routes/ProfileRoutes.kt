package com.aichallengekmp.routes

import com.aichallengekmp.di.AppContainer
import com.aichallengekmp.model.UserProfile
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ProfilesResponse(
    val profiles: List<UserProfile>,
    val currentProfileId: String
)

fun Route.profileRoutes() {
    route("/profiles") {
        // Получить список всех профилей
        get {
            val profiles = AppContainer.availableProfiles
            val currentProfileId = AppContainer.currentProfile.id

            call.respond(
                ProfilesResponse(
                    profiles = profiles,
                    currentProfileId = currentProfileId
                )
            )
        }

        // Получить текущий профиль
        get("/current") {
            call.respond(AppContainer.currentProfile)
        }
    }
}
