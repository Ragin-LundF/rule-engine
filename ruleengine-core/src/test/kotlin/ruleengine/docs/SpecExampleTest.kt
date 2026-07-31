package ruleengine.docs

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.ActionSchema
import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Checks that every rule the documentation shows actually parses, validates and compiles.
 *
 * RULE-SPEC.md is handed to AI assistants under a "use nothing that is not listed here" contract, so a
 * wrong example there does not read as a typo — it becomes a generator for rules that fail at load.
 * This test is what keeps the document honest: it extracts the fenced code blocks, builds a schema
 * from the schema blocks the docs themselves declare, and runs every rule block through the real
 * pipeline.
 *
 * When this fails, the documentation is wrong — fix the document, not the test.
 */
class SpecExampleTest {

    /** Tests run with the module directory as the working directory. */
    private val repoRoot: Path = Path.of("..")

    /**
     * Fields that appear in illustrative snippets without a schema block of their own.
     *
     * Every entry is a small admission that the docs show a field they never declare. Keep this list
     * short: declaring the field in the document is always the better fix.
     */
    private val supplementaryFields: List<FieldDefinition> = listOf(
        FieldDefinition(id = FieldId(value = "country"), type = FieldType.TEXT),
        FieldDefinition(id = FieldId(value = "counterparty"), type = FieldType.TEXT),
        FieldDefinition(id = FieldId(value = "reference"), type = FieldType.TEXT),
        FieldDefinition(id = FieldId(value = "user_email"), type = FieldType.TEXT),
        FieldDefinition(id = FieldId(value = "purposeNorm"), type = FieldType.TEXT),
        FieldDefinition(id = FieldId(value = "isActive"), type = FieldType.BOOLEAN),
        FieldDefinition(id = FieldId(value = "createdAt"), type = FieldType.DATE),
        FieldDefinition(
            id = FieldId(value = "transactions"),
            type = FieldType.COLLECTION,
            fields = listOf(
                FieldDefinition(id = FieldId(value = "amount"), type = FieldType.DECIMAL),
                FieldDefinition(id = FieldId(value = "label"), type = FieldType.TEXT),
            ).associateBy { it.id },
        ),
    )

    /** Actions used in snippets but not always declared in an action block. */
    private val supplementaryActions: Set<String> =
        setOf("label", "category", "flag", "score", "alert", "reject", "notify", "discount", "badge")

    // ── extraction ────────────────────────────────────────────────────────────

    private data class FencedBlock(val language: String, val content: String)

    private fun fencedBlocks(markdown: String): List<FencedBlock> {
        val blocks = mutableListOf<FencedBlock>()
        var inside = false
        var language = ""
        val body = StringBuilder()

        markdown.lineSequence().forEach { line ->
            if (line.trimStart().startsWith(prefix = "```")) {
                if (inside) {
                    blocks += FencedBlock(language = language, content = body.toString())
                    body.clear()
                    inside = false
                } else {
                    language = line.trim().removePrefix(prefix = "```").trim()
                    inside = true
                }
                return@forEach
            }
            if (inside) body.appendLine(line)
        }
        return blocks
    }

    /** A block is a rule snippet when it declares at least one rule and is not a placeholder template. */
    private fun isRuleBlock(block: FencedBlock): Boolean =
        block.content.contains(other = "rule \"") && !block.content.contains(other = "<")

    private fun isFieldSchemaBlock(block: FencedBlock): Boolean =
        block.language == "yaml" &&
            block.content.contains(other = "fields:") &&
            !block.content.contains(other = "<")

    private fun isActionSchemaBlock(block: FencedBlock): Boolean =
        block.language == "yaml" &&
            block.content.trimStart().startsWith(prefix = "actions:") &&
            !block.content.contains(other = "<")

    // ── schema assembly ───────────────────────────────────────────────────────

    /** Merges every field schema the document declares, plus the supplementary fields. */
    private fun mergedSchema(blocks: List<FencedBlock>): FieldSchema {
        val fields = mutableMapOf<FieldId, FieldDefinition>()
        supplementaryFields.forEach { fields[it.id] = it }

        blocks.filter { isFieldSchemaBlock(block = it) }.forEach { block ->
            // A block may legitimately fail to load — the integration guide shows a schema using a
            // custom normalizer that only exists after runtime registration. Those are skipped here;
            // `RULE-SPEC schema examples all load` covers the document that must be self-contained.
            val loaded = runCatching { FieldSchemaLoader.loadFromString(content = block.content) }
                .getOrNull() ?: return@forEach
            loaded.fields.forEach { (id, definition) -> fields[id] = merge(existing = fields[id], added = definition) }
        }

        return FieldSchema(name = "docs-merged", fields = fields)
    }

    /**
     * Combines two declarations of the same field.
     *
     * The documents show the same field with different operator lists depending on what a section is
     * illustrating, so the operator sets are unioned: a rule is legitimate when any declaration of the
     * field allows its operator. Everything else about the later declaration wins.
     */
    private fun merge(existing: FieldDefinition?, added: FieldDefinition): FieldDefinition {
        if (existing == null) return added
        return added.copy(
            operators = existing.operators + added.operators,
            fields = existing.fields + added.fields,
        )
    }

    private fun mergedActions(blocks: List<FencedBlock>): ActionSchema {
        val declared = blocks.filter { isActionSchemaBlock(block = it) }.flatMap { block ->
            val loaded = runCatching { ActionSchemaLoader.loadFromString(content = block.content) }
                .getOrNull() ?: return@flatMap emptyList()
            loaded.actions.values
        }
        val supplements = supplementaryActions
            .filterNot { name -> declared.any { it.name == name } }
            .map { name ->
                ruleengine.core.domain.ActionDefinition(
                    name = name,
                    argTypes = listOf(ruleengine.core.domain.ActionArgType.STRING),
                )
            }
        return ActionSchema(actions = (declared + supplements).associateBy { it.name })
    }

    // ── the check ─────────────────────────────────────────────────────────────

    /** Every markdown file that documents the DSL, so the running example can span files. */
    private fun documentationFiles(): List<Path> = buildList {
        add(repoRoot.resolve("RULE-SPEC.md"))
        add(repoRoot.resolve("README.md"))
        Files.list(repoRoot.resolve("docs")).use { stream ->
            addAll(stream.filter { it.toString().endsWith(suffix = ".md") }.sorted().toList())
        }
    }.filter { Files.exists(it) }

    /**
     * The schema every document's examples are checked against.
     *
     * The docs share one running example — the transaction fields are declared in `field-schema.md`
     * and RULE-SPEC and then used in snippets elsewhere — so schema blocks are collected across all
     * files rather than per file.
     */
    private fun documentationSchema(): Pair<FieldSchema, ActionSchema> {
        val blocks = documentationFiles().flatMap { fencedBlocks(markdown = Files.readString(it)) }
        return mergedSchema(blocks = blocks) to mergedActions(blocks = blocks)
    }

    /**
     * Parses, validates and compiles every rule block in [fileName] against the documentation-wide
     * schema.
     */
    private fun checkDocument(fileName: String) {
        val path = repoRoot.resolve(fileName)
        assertTrue(actual = Files.exists(path), message = "$fileName not found at ${path.toAbsolutePath()}")

        val (schema, actions) = documentationSchema()
        val blocks = fencedBlocks(markdown = Files.readString(path))
        val ruleBlocks = blocks.filter { isRuleBlock(block = it) }

        assertTrue(
            actual = ruleBlocks.isNotEmpty(),
            message = "$fileName contains no rule examples — has the extraction broken?",
        )

        ruleBlocks.forEachIndexed { index, block ->
            val label = "$fileName rule block #${index + 1}"

            val asts = runCatching { Parser(input = block.content).parseRules() }.getOrElse { cause ->
                error("$label does not parse: ${cause.message}\n${block.content}")
            }

            // Rule ids repeat across a document's examples, so validate each block on its own.
            val errors = Validator.validate(asts = asts, schema = schema, actions = actions)
                .diagnostics.filter { it.severity == Severity.ERROR }
            assertTrue(
                actual = errors.isEmpty(),
                message = "$label does not validate: ${errors.map { it.message }}\n${block.content}",
            )

            runCatching { Compiler.compileRules(asts = asts, schema = schema) }.getOrElse { cause ->
                error("$label does not compile: ${cause.message}\n${block.content}")
            }
        }
    }

    @Test
    fun `every rule example in RULE-SPEC is valid`() {
        checkDocument(fileName = "RULE-SPEC.md")
    }

    /**
     * RULE-SPEC promises that everything an AI needs is listed in the document itself, so every
     * schema and action example in it must load with no outside registration.
     */
    @Test
    fun `RULE-SPEC schema examples all load`() {
        val blocks = fencedBlocks(markdown = Files.readString(repoRoot.resolve("RULE-SPEC.md")))

        blocks.filter { isFieldSchemaBlock(block = it) }.forEach { block ->
            runCatching { FieldSchemaLoader.loadFromString(content = block.content) }.getOrElse { cause ->
                error("A RULE-SPEC field schema example does not load: ${cause.message}\n${block.content}")
            }
        }
        blocks.filter { isActionSchemaBlock(block = it) }.forEach { block ->
            runCatching { ActionSchemaLoader.loadFromString(content = block.content) }.getOrElse { cause ->
                error("A RULE-SPEC action schema example does not load: ${cause.message}\n${block.content}")
            }
        }
    }

    @Test
    fun `every rule example in the README is valid`() {
        checkDocument(fileName = "README.md")
    }

    @Test
    fun `every rule example in the docs folder is valid`() {
        Files.list(repoRoot.resolve("docs")).use { stream ->
            stream.filter { it.toString().endsWith(suffix = ".md") }.sorted().toList()
        }.forEach { doc ->
            val blocks = fencedBlocks(markdown = Files.readString(doc))
            if (blocks.none { isRuleBlock(block = it) }) return@forEach
            checkDocument(fileName = "docs/${doc.fileName}")
        }
    }
}
