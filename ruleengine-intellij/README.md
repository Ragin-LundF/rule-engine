# Rule Engine DSL – IntelliJ plugin

Editor support for rule engine projects in IntelliJ IDEA 2026.3 or later.

- **Rule files (`.rule`):** highlighting, completion from the same code as the desktop editor,
  Ctrl+click (Cmd+click on macOS) from a field, alias or action to its schema declaration and from a
  `$variable` to the `set` / `add … to` clauses that write it, and a **Rule Diagram**
  tool window that draws the rule at the caret.
- **Manifest, field schema and action schema YAML:** completion and validation. Ctrl+click
  (Cmd+click on macOS) on a path in a manifest opens that file.

Navigation is IntelliJ's own *Go to Declaration*, so it follows the keymap: Ctrl+click on Windows and
Linux, Cmd+click on macOS, and whatever else a custom keymap assigns.

## How a rule finds its schema

A rule is linked to the manifest that lists it, found in the rule's folder or a folder above it. One
match is linked without asking. With no match or several, a banner above the editor lets you choose.
The **Rule Manifest** tool window shows the manifest of the open file; opening a rule from there
links it to that manifest. Only the linked manifest and its two schema files are read, never the
other rules.

## Install

Without the JetBrains Marketplace, either way:

- **Manual:** download `ruleengine-intellij-<version>.zip` from a
  [GitHub release](https://github.com/Ragin-LundF/rule-engine/releases), then Settings → Plugins →
  ⚙ → *Install Plugin from Disk…*.
- **With updates:** Settings → Plugins → ⚙ → *Manage Plugin Repositories…*, add
  `https://raw.githubusercontent.com/Ragin-LundF/rule-engine/main/ruleengine-intellij/updatePlugins.xml`,
  then install *Rule Engine DSL* from the Marketplace tab.

`updatePlugins.xml` is committed and names the release zip of the current version. After bumping the
version in `gradle.properties`, regenerate it with `./gradlew -p ruleengine-intellij updatePluginsXml`
and commit it; IntelliJ offers the update once it is on `main` and the release is published.

## Build

A separate Gradle build next to the library modules, which it uses unchanged. The plugin takes the
library's version from the root `gradle.properties`:

```shell
./gradlew -p ruleengine-intellij test        # tests, run in a headless IDE
./gradlew -p ruleengine-intellij detekt      # static analysis with the repository's config/detekt.yml
./gradlew -p ruleengine-intellij runIde      # an IDE with the plugin installed
./gradlew -p ruleengine-intellij buildPlugin # build/distributions/ruleengine-intellij-<version>.zip
```

The completion code and the diagram are compiled straight from `ruleengine-ui/src` (see the include
list in `build.gradle.kts`). The diagram runs on the Compose that the IDE bundles; because the IDE
has no Compose material, `src/main/kotlin/androidx/compose/material/Text.kt` stands in for the one
material function the diagram uses.
