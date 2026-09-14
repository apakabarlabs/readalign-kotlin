pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        kotlin("jvm") version "2.3.20"
        kotlin("plugin.serialization") version "2.4.20"
    }
}

dependencyResolutionManagement {
    // Declared here and not in the build file, so that a build including this project as
    // a module of its own settles where dependencies come from without being argued with.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "readalign-kotlin"
