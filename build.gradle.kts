import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.0.0-beta12"
    id("gg.grounds.push") version "0.7.0"
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven {
        url = uri("https://maven.pkg.github.com/groundsgg/*")
        credentials {
            username = providers.gradleProperty("github.user").orNull
                ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("github.token").orNull
                ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    // Paper runtime — provided by the server, never bundled.
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // Grounds SDK (gRPC channel cache + JWT auto-auth + typed NATS).
    // Bundled into the shadow jar so the plugin pod has everything it
    // needs at runtime.
    implementation("gg.grounds:library-grpc-contracts-sdk:main-SNAPSHOT")
    implementation("gg.grounds:library-grpc-contracts-leaderboard:main-SNAPSHOT")
    // Event proto definitions — MatchEnded for the lifecycle
    // subscription below.
    implementation("gg.grounds:library-grpc-contracts-events:1.0.0")

    // Protoc-generated stubs for LeaderboardService land here at
    // compile time via the protobuf plugin (added in a follow-up — for
    // now the demo references the proto types directly through
    // library-grpc-contracts-leaderboard's bundled descriptors).
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.build { dependsOn(tasks.shadowJar) }

groundsPush {
    apiUrl.set(providers.environmentVariable("GROUNDS_API_URL").orElse("https://platform.grnds.io"))
}
