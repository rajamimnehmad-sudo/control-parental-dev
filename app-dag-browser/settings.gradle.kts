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
        maven {
            name = "Mozilla"
            url = uri("https://maven.mozilla.org/maven2/")
            content {
                includeGroup("org.mozilla.geckoview")
            }
        }
    }
}

rootProject.name = "DagBrowser"
include(":gloshia-visual-core")
project(":gloshia-visual-core").projectDir = file("../gloshia-visual-core")
