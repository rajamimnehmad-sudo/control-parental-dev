pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CommunityContentFilter"

include(":app-user")
include(":app-admin")
include(":core-domain")
include(":core-policy")
include(":core-data")
include(":core-database")
include(":core-network")
include(":core-sync")
include(":core-security")
include(":core-update")
include(":core-telemetry")
include(":core-ui")
include(":feature-status")
include(":feature-onboarding")
include(":feature-vpn")
include(":feature-accessibility")
include(":feature-activation")
include(":feature-requests")
include(":feature-usage")
include(":feature-block")
