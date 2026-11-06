import dev.detekt.gradle.Detekt
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.util.Properties

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.compose") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.20.0"
    id("dev.detekt") version "2.0.0-alpha.6"
}

group = "io.github.ragin-lundf"
// The plugin ships with the library release it belongs to, so it takes the library's version. The
// release workflow passes the tag's version as -PpluginVersion, as the library publish passes -Pversion.
val libraryVersion = Properties().apply { rootDir.resolve("../gradle.properties").reader().use(::load) }
    .getProperty("version")
version = providers.gradleProperty("pluginVersion").getOrElse(libraryVersion)

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// The editor's completion logic and the single-rule diagram live in ruleengine-ui. They are compiled
// here straight from those sources instead of being copied or moved, so the UI module stays unchanged.
// The diagram renders on the Compose that the IDE bundles.
val uiSources = rootDir.resolve("../ruleengine-ui/src")
sourceSets.main {
    kotlin.srcDir(uiSources.resolve("jvmMain/kotlin"))
    kotlin.srcDir(uiSources.resolve("commonMain/kotlin"))
    kotlin.include(
        "ruleengine/intellij/**",
        "ui/dsl/DslContext.kt",
        "ui/dsl/model/**",
        "ui/autocompletion/Builders.kt",
        "ui/autocompletion/WhenCompletions.kt",
        "ui/autocompletion/ThenCompletions.kt",
        "ui/autocompletion/Model.kt",
        "ui/autocompletion/FieldLookup.kt",
        "ui/autocompletion/model/**",
        "ui/builder/OperatorOptions.kt",
        "ui/builder/model/catalog/CatalogFieldInfo.kt",
        "ui/util/Words.kt",
        "ui/diagrams/ExpressionRenderer.kt",
        "ui/diagrams/AstHelpers.kt",
        "ui/diagrams/Colors.kt",
        "ui/diagrams/DarkDiagramPalette.kt",
        "ui/diagrams/LightDiagramPalette.kt",
        "ui/diagrams/model/DiagramPalette.kt",
        "ui/diagrams/render/Connectors.kt",
        "ui/diagrams/render/DiagramAtoms.kt",
        "ui/diagrams/render/DiagramEntry.kt",
        "ui/diagrams/render/Nodes.kt",
        "ui/theme/**",
        // The IDE bundles Compose foundation but not material; the diagram's `Text` comes from here.
        "androidx/compose/material/**",
    )
}

dependencies {
    implementation("io.github.ragin-lundf:ruleengine-core")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("com.intellij.modules.json")
        composeUI()
        testFramework(TestFrameworkType.Platform)
    }
}

// The repository's shared detekt rules. Only the plugin's own sources: the files compiled in from
// ruleengine-ui are analysed by that module.
detekt {
    config.setFrom(rootDir.resolve("../config/detekt.yml"))
    buildUponDefaultConfig = true
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "25"
    reports {
        html.required = true
        sarif.required = true
    }
}

tasks.named("check") {
    dependsOn(tasks.withType<Detekt>())
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "263"
        }
    }
}

// A custom plugin repository instead of the JetBrains Marketplace: the release workflow attaches the
// zip and this file to the GitHub release, and the IDE reads
// https://github.com/Ragin-LundF/rule-engine/releases/latest/download/updatePlugins.xml for updates.
val releaseTag = providers.gradleProperty("releaseTag").orElse("v$version")
val updatePluginsXml by tasks.registering {
    group = "intellij platform"
    description = "Writes updatePlugins.xml, the custom plugin repository file the IDE reads for updates."
    val output = layout.buildDirectory.file("distributions/updatePlugins.xml")
    val zipName = tasks.buildPlugin.flatMap { task -> task.archiveFileName }
    val pluginVersion = version.toString()
    val tag = releaseTag
    inputs.property("zipName", zipName)
    inputs.property("version", pluginVersion)
    inputs.property("tag", tag)
    outputs.file(output)
    doLast {
        val url = "https://github.com/Ragin-LundF/rule-engine/releases/download/${tag.get()}/${zipName.get()}"
        output.get().asFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <plugins>
                <plugin id="io.github.ragin-lundf.ruleengine" url="$url" version="$pluginVersion">
                    <idea-version since-build="263"/>
                    <name>Rule Engine DSL</name>
                </plugin>
            </plugins>
            """.trimIndent() + "\n",
        )
    }
}
