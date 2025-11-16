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
    implementation("io.ktor:ktor-client-cio:2.3.8")

    // SQLDelight
    implementation(libs.sqldelight.driver.sqlite)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.runtime)

    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    // Tests
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}