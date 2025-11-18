
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    kotlin("plugin.serialization") version "2.2.20"
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.ktor.client)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx)
                implementation(libs.ktor.client.logging)
                // MCP SDK только для JVM (server модуль)
                // implementation(libs.mcp.kotlin)
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

