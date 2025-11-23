import org.gradle.declarative.dsl.schema.FqName.Empty.packageName

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.sqldelight)
    kotlin("plugin.serialization") version "2.2.20"
    application
}

group = "com.aichallengekmp"
version = "1.0.0"

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.aichallengekmp.database")
        }
    }
}

application {
    mainClass.set("com.aichallengekmp.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)

    implementation("ch.qos.logback:logback-classic:1.5.6")
    // Ktor Server
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx)

    // Ktor Client
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client)
    implementation(libs.ktor.client.logging)

    // SQLDelight
    implementation(libs.sqldelight.driver.sqlite)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.runtime)
    implementation("io.ktor:ktor-server-websockets:3.0.3")

    // Koin - УДАЛЕН из-за несовместимости с Ktor 3.x
    // Используем manual DI вместо Koin

    // Tests
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)

    // MCP SDK - теперь работает, т.к. убрали Koin!
    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.7.7")
    implementation("io.modelcontextprotocol:kotlin-sdk:0.7.7")
}