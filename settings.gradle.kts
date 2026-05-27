rootProject.name = "sample-leaderboard-plugin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        // grounds-push is published to GitHub Packages.
        maven {
            url = uri("https://maven.pkg.github.com/groundsgg/grounds-push")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}
