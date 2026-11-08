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

rootProject.name = "StreamMind"

include(":app")
include(":core:core-common")
include(":feature:player")
include(":feature:transcript")
include(":feature:ai-chapters")
include(":feature:ai-qa")
include(":feature:accessibility")
include(":data:data-local")
include(":data:data-remote")
