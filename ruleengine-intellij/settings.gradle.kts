pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "ruleengine-intellij"

// A separate build, so the existing modules and the root build stay untouched. Gradle substitutes
// the core artifact with the project from the rule-engine build next door.
includeBuild("..")
