package ruleengine.performance

import java.nio.file.Files
import java.nio.file.Path

/**
 * Generates the rule project the performance and concurrency tests measure.
 *
 * The project is written to a temporary directory instead of `src/test/resources` so its shape stays
 * a knob rather than a fixture: [RULE_COUNT] rules over a [FIELD_COUNT]-field record is the size the
 * benchmark reports, and changing the constants here changes what is measured without touching a
 * checked-in file.
 *
 * The generated rules are deliberately not all satisfiable — [MATCHING_RULE_COUNT] of them match the
 * record [input] produces. A rule set where everything matches would measure only the cheapest path
 * through the evaluator, since a condition that fails early never evaluates its remaining operands.
 *
 * `rules/00-totals.rule` is listed first in the manifest because it publishes the `set` variables the
 * aggregate rules read, and a variable only reaches the rules declared after it.
 */
internal object BenchmarkProject {

    /** Top-level fields in the generated schema: 29 scalars plus the `items` collection. */
    const val FIELD_COUNT = 30

    /** Rules in the generated project, spread over four rule files. */
    const val RULE_COUNT = 20

    /** How many of the [RULE_COUNT] rules match the record [input] builds. */
    const val MATCHING_RULE_COUNT = 16

    /** Records in the `items` collection every aggregate rule iterates. */
    const val ITEM_COUNT = 12

    private const val TEXT_RULE_COUNT = 6

    private val TEXT_FIELDS = listOf(
        "purpose", "counterparty", "iban", "sepaCode", "country",
        "channel", "merchant", "reference", "segment", "notes",
    )
    private val DECIMAL_FIELDS = listOf("amount", "balance", "feeAmount", "limitAmount", "exchangeRate", "discount")
    private val INTEGER_FIELDS = listOf("count", "riskScore", "ageDays", "retries", "priority", "quantity")
    private val BOOLEAN_FIELDS = listOf("recurring", "verified", "flagged")
    private val DATE_FIELDS = listOf("bookingDate", "valueDate")
    private val SET_FIELDS = listOf("tags", "categories")

    /**
     * Writes schema, actions, rule files and manifest into [dir] and returns the manifest path.
     */
    fun write(dir: Path): Path {
        val rulesDir = dir.resolve("rules")
        Files.createDirectories(rulesDir)

        Files.writeString(dir.resolve("schema.yaml"), schemaYaml())
        Files.writeString(dir.resolve("actions.yaml"), ACTIONS_YAML)
        Files.writeString(rulesDir.resolve("00-totals.rule"), TOTALS_RULE)
        Files.writeString(rulesDir.resolve("10-text.rule"), textRules())
        Files.writeString(rulesDir.resolve("20-scalar.rule"), SCALAR_RULES)
        Files.writeString(rulesDir.resolve("30-aggregate.rule"), AGGREGATE_RULES)

        val manifest = dir.resolve("manifest.yaml")
        Files.writeString(manifest, MANIFEST_YAML)
        return manifest
    }

    /**
     * Builds one record for the generated schema.
     *
     * [seed] shifts every numeric value, so two seeds drive different `set` variables — which is what
     * lets the concurrency test tell one thread's evaluation apart from another's.
     */
    fun input(seed: Int): Map<String, Any?> {
        val values = mutableMapOf<String, Any?>()
        TEXT_FIELDS.forEachIndexed { index, name ->
            values[name] = "$name-${if (index % 2 == 0) "alpha" else "beta"}-$seed"
        }
        DECIMAL_FIELDS.forEachIndexed { index, name -> values[name] = (index + 1) * 100 + seed }
        INTEGER_FIELDS.forEachIndexed { index, name -> values[name] = (index + 1) * 10 + seed }
        BOOLEAN_FIELDS.forEachIndexed { index, name -> values[name] = index % 2 == 0 }
        values[DATE_FIELDS[0]] = "2026-03-01"
        values[DATE_FIELDS[1]] = "2026-03-05"
        values[SET_FIELDS[0]] = listOf("premium", "retail")
        values[SET_FIELDS[1]] = listOf("retail", "bulk")
        values["items"] = items(seed = seed)
        return values
    }

    /**
     * The `totalValue` the `totals` rule publishes for [seed] — the sum of every item's unit price.
     */
    fun expectedTotalValue(seed: Int): Int = items(seed = seed).sumOf { item -> item["unitPrice"] as Int }

    private fun items(seed: Int): List<Map<String, Any?>> = (0 until ITEM_COUNT).map { index ->
        mapOf(
            "sku" to "sku-$index",
            "unitPrice" to (index + 1) * 10 + seed,
            "quantity" to (index % 5) + 1,
            "category" to if (index % 3 == 0) "electronics" else "retail",
            "damaged" to (index % 4 == 0),
        )
    }

    private fun schemaYaml(): String = buildString {
        appendLine("schema: benchmark-v1")
        appendLine()
        appendLine("fields:")
        appendLine()
        TEXT_FIELDS.forEach { name ->
            appendLine("  $name:")
            appendLine("    type: text")
            appendLine("    normalizers:")
            appendLine("      - trim")
            appendLine("      - lowercase")
            appendLine("      - german_umlaut_fold")
        }
        DECIMAL_FIELDS.forEach { name -> append(scalar(name = name, type = "decimal")) }
        INTEGER_FIELDS.forEach { name -> append(scalar(name = name, type = "integer")) }
        BOOLEAN_FIELDS.forEach { name -> append(scalar(name = name, type = "boolean")) }
        DATE_FIELDS.forEach { name -> append(scalar(name = name, type = "date")) }
        SET_FIELDS.forEach { name -> append(scalar(name = name, type = "string_set")) }
        append(ITEMS_FIELD)
    }

    private fun scalar(name: String, type: String): String = "  $name:\n    type: $type\n"

    /**
     * One rule per text field, each reading a field the record fills with either an `alpha` or a
     * `beta` value, so half of them match.
     */
    private fun textRules(): String = TEXT_FIELDS.take(TEXT_RULE_COUNT).joinToString(separator = "\n\n") { name ->
        """
        rule "text-$name" {
          description "Matches the $name field against a token and a prefix that never occurs."
          when
            $name contains "alpha"
            or $name startsWith "unmatched-prefix"

          then
            label "text:$name"
        }
        """.trimIndent()
    } + "\n"

    // trimMargin rather than trimIndent: the block is nested under `fields:`, so its two-space
    // indent is meaningful YAML that trimIndent would strip away.
    private val ITEMS_FIELD = """
        |  items:
        |    type: collection
        |    fields:
        |      sku:
        |        type: text
        |      unitPrice:
        |        type: decimal
        |      quantity:
        |        type: integer
        |      category:
        |        type: text
        |      damaged:
        |        type: boolean
        |
    """.trimMargin()

    private val ACTIONS_YAML = """
        actions:
          label:
            argTypes: [string]
          category:
            argTypes: [string]
          flag:
            argTypes: [string]

    """.trimIndent()

    private val MANIFEST_YAML = """
        name: benchmark-project

        entries:
          - id: benchmark
            schema: schema.yaml
            actions: actions.yaml
            rules:
              # First, so the totals it publishes reach every rule that follows.
              - rules/00-totals.rule
              - rules/10-text.rule
              - rules/20-scalar.rule
              - rules/30-aggregate.rule

    """.trimIndent()

    private val TOTALS_RULE = """
        rule "totals" {
          description "Publishes the item totals the aggregate rules read instead of aggregating again."
          when
            count(items) > 0

          then
            set totalValue = sum(items.unitPrice)
            set damagedCount = count(items[damaged == true])
        }

    """.trimIndent()

    private val SCALAR_RULES = """
        rule "amount-in-range" {
          description "Two decimal comparisons combined with and."
          when
            amount between 10 100000
            and balance gte 0

          then
            category "amount:ok"
        }

        rule "fee-or-discount" {
          description "An or group whose first operand fails, so the second decides the outcome."
          when
            feeAmount lt 50
            or discount gt 0

          then
            flag "fee:reviewed"
        }

        rule "count-and-score" {
          description "Two integer comparisons, one of them a between range."
          when
            count between 1 500
            and riskScore gte 10

          then
            label "volume:normal"
        }

        rule "priority-recurring" {
          description "An integer comparison combined with a boolean equality."
          when
            priority lte 500
            and recurring equals true

          then
            label "priority:recurring"
        }

        rule "booking-window" {
          description "Two date comparisons against ISO-8601 literals."
          when
            bookingDate >= "2020-01-01"
            and valueDate <= "2030-12-31"

          then
            category "window:current"
        }

        rule "tag-match" {
          description "Set membership over the two string_set fields."
          when
            tags containsAny ["premium", "vip"]
            and categories containsAll ["retail"]

          then
            flag "tags:premium"
        }

    """.trimIndent()

    private val AGGREGATE_RULES = """
        rule "high-value-load" {
          description "Reads the totalValue variable the totals rule published."
          when
            ${'$'}totalValue > 500

          then
            category "value:high"
        }

        rule "damaged-present" {
          description "Reads the damagedCount variable the totals rule published."
          when
            ${'$'}damagedCount > 0

          then
            flag "condition:damaged"
        }

        rule "damaged-ratio" {
          description "Does not match the generated record, so the benchmark also measures a failing rule."
          when
            ${'$'}damagedCount > count(items) * 0.25

          then
            flag "condition:mostly-damaged"
        }

        rule "expensive-item" {
          description "A max aggregate over the items collection."
          when
            max(items.unitPrice) >= 50

          then
            label "item:expensive"
        }

        rule "cheap-average" {
          description "An avg aggregate over the items collection."
          when
            avg(items.unitPrice) < 100000

          then
            label "item:average-low"
        }

        rule "bulk-order" {
          description "A sum aggregate over the items collection."
          when
            sum(items.quantity) > 20

          then
            category "order:bulk"
        }

        rule "electronics-pair" {
          description "A count over a filtered path, the most expensive shape in the set."
          when
            count(items[category == "electronics"]) >= 2

          then
            label "category:electronics"
        }

    """.trimIndent()
}
