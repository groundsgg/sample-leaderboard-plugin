import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.google.protobuf.gradle.id

plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.0.0-beta12"
    id("gg.grounds.push") version "0.12.0"
    id("com.google.protobuf") version "0.9.4"
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
    // needs at runtime. grpc-stub/grpc-protobuf come transitively (api).
    implementation("gg.grounds:library-grpc-contracts-sdk:main-SNAPSHOT")

    // Proto sources for code generation. The contracts modules ship the
    // .proto only, so protoc generates the typed LeaderboardService stub +
    // messages and the MatchEnded message here at build time.
    protobuf("gg.grounds:library-grpc-contracts-leaderboard:main-SNAPSHOT")
    protobuf("gg.grounds:library-grpc-contracts-events:1.0.0")

    // @javax.annotation.Generated on grpc-java's generated stubs.
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
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

protobuf {
    // Match the protobuf-java runtime that grpc 1.68.1 pulls (3.25.5) so the
    // generated code doesn't reference 4.28+ APIs (RuntimeVersion).
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.68.1"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
            }
        }
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.build { dependsOn(tasks.shadowJar) }

groundsPush {
    apiUrl.set(providers.environmentVariable("GROUNDS_API_URL").orElse("https://platform.grnds.io"))
}
