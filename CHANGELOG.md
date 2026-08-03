# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 1.8.1

### Added

- **A field `alias` now works on its own at any nesting depth.** `alias:` was only a synonym for the
  segment it was declared on, so an alias on a nested field could only be written in its path position
  (`reports.income.TRANSACTION_HISTORY_DAYS`) — the bare spelling the documentation advertised returned
  `Unknown field`. A bare alias now resolves to the path it stands for, unless that path reads through a
  `collection`, which is reported as the usual "yields one value per element" error naming the collection.
  Resolution order is declared name → top-level alias → dotted path → bare alias, so no existing schema
  changes meaning. `RULE-SPEC.md` §3.6 documents the contract with legal and illegal examples.

### Fixed

- **An alias inside a filter predicate or an aggregate projection read the wrong key, silently.**
  `sum(orders.orderTotal)` and `orders[ACCOUNT_TYPE == "CHECKING"]` validated clean, compiled a segment
  named after the alias and then found no such key in the input — producing a missing value or a false
  negative with no diagnostic at all. Both now resolve every segment against the members it is looked up
  in, matching what the validator already accepted.
- **Duplicate aliases were only detected among top-level fields.** The documented load-time uniqueness
  guarantee did not apply to any alias declared inside an `object` or `collection`. Uniqueness is now
  checked across the whole tree as an ERROR, and an alias that collides with a declared field name or
  dotted path is a WARNING — the declared name wins, so such an alias can never be used.
- **`alias: ""` is rejected when the schema loads** instead of quietly declaring a second name nothing
  can write.
- **The visual Builder ignored aliases entirely.** `alias` was not carried across the engine → Builder
  boundary, so an alias-authored condition row matched no field, fell back to `text` and offered the
  wrong operators. The field dropdown now offers the alias next to the canonical path, path breadcrumbs
  accept an alias segment, and the code editor's completions and highlighting cover nested paths and
  nested aliases.

## 1.8.0

### Added

- **Rules can be loaded from the classpath, so they may ship inside a jar.** A manifest is named by a
  location string, and a `classpath:` prefix is what selects the classpath — the same entry points
  (`RuleEngineBuilder.fromManifest` / `fromManifestEntry`, `RuleCatalogBuilder.fromManifest`) read
  both places:

  ```kotlin
  // src/main/resources/rules/{manifest.yaml,schema.yaml,actions.yaml,rules/*.rule}
  val packaged = RuleEngineBuilder.fromManifestEntry(
      manifestLocation = "classpath:rules/manifest.yaml",
      entryId = "transactions"
  )
  val onDisk = RuleEngineBuilder.fromManifestEntry(
      manifestLocation = "/etc/app/rules/manifest.yaml",
      entryId = "transactions"
  )
  ```

  Previously the only entry points were typed on `java.nio.file.Path` and therefore could never read
  rules packaged inside an application. A resource under `BOOT-INF/classes` of a Spring Boot executable
  jar resolves to a `jar:nested:/app.jar/!BOOT-INF/classes/!/…` URL (Boot 3.2+) or a
  `jar:file:/app.jar!/BOOT-INF/classes!/…` URL, and the JDK ships no `FileSystemProvider` for either
  nested form — so no `Path` exists at all. Rules under `BOOT-INF/lib/*.jar` are a jar inside a jar and
  have the same problem.

  The classpath route reads through `ClassLoader.getResourceAsStream` and nothing else, which is why an
  exploded build directory, a plain library jar, a Boot executable jar and a nested jar all behave
  identically. Nothing is scanned — a manifest already enumerates every file it uses — so no reflection
  or classpath-scanning dependency was added. The default loader is the thread context class loader, so
  the application's own resources are still found under `spring-boot-devtools`.

  The prefix lives in one place, `ManifestSource` (`ruleengine.manifest.source`), which turns a location
  into the manifest plus the resolver that serves its files. The `Path` overloads of `fromManifest` and
  `fromManifestEntry` are unchanged, for callers that already hold one.

  The documented Spring Boot example previously used `Path.of("config/rules/manifest.yaml")`, which
  resolves against the process working directory: it worked under `bootRun` and broke wherever the jar
  was started from another directory. `docs/integration-guide.md` now shows a `classpath:` location and
  keeps a filesystem one only for rules deliberately kept outside the jar.

- **`ManifestFileResolver` — a public seam for rules that live somewhere else entirely.** Implement it
  to serve a manifest's files from a database, an object store or a config server without reassembling
  the load pipeline by hand; both builders accept any implementation. `ManifestFile` describes what a
  resolver returns (`OnDisk`, `InMemory`, `Unavailable`), and the two built-in implementations are
  `FileSystemManifestFileResolver` and `ClasspathManifestFileResolver`.

  A resolver must reject a path that leaves the manifest's own location. Both built-in ones do, including
  the classpath resolver — `getResourceAsStream` happens to return `null` for `rules/../x` inside a jar,
  but on an exploded classpath the same name resolves, which would otherwise have read outside the
  manifest's directory.

  `FileInputSupport.readBoundedText` gained an `InputStream` overload so classpath content keeps the
  25 MB size guard. It decodes UTF-8 strictly, matching the `Path` variant.

## 1.7.0

### Added

- **Filter predicates take full expressions on both sides.** A `[...]` predicate used to be a flat
  `member op literal` row. Both sides are now value expressions, so a filter may hold whatever a
  comparison may:

  ```
  count(orders[count(items) > 2]) > 0            # an aggregate over the element's own collection
  count(orders[total * 2 > 100]) > 0             # arithmetic
  count(orders[items[price > 0].sku == "x"]) > 0 # a path that filters again
  count(orders[total > sum(items.price)]) > 0    # a computed right-hand side
  ```

  All four already evaluated correctly; what changes is that the visual Builder can now represent
  them, so such a rule is editable instead of read-only. `BuilderFilter` carries two
  `BuilderOperand`s in place of `field`/`value`/`listItems`/`valueIsPath`, and the new
  `BuilderOperand.ListLiteral` is what lets `[status in ["paid", "sent"]]` survive as a list rather
  than collapsing to one literal.

- **`and`, `or` and `not` inside a filter predicate.** These parsed and validated but then failed to
  compile, so a rule that had passed every check broke at load time.

  ```
  count(parcels[origin.hub == "HAM" and origin.scans > 2]) > 0
  count(parcels[origin.hub == "HAM" or origin.scans > 2]) > 0
  ```

  Chaining filters — `[a][b]` — still works and still means `and`. It was the documented workaround,
  and it never had an `or` equivalent.

- **`contains` inside a filter predicate.** It has a comparison operator to compile to, so rejecting
  it was arbitrary. `count(parcels[origin.hub contains "AM"]) > 0` now works.

- **Regex extractions are editable in the Builder.** `extract iban regex("DE(\\d+)", 1) tag $1` used
  to lock the whole rule out of Builder mode, because `BuilderAction` had nowhere to keep a pattern
  and regenerating the DSL without it would have deleted it from the file. Actions now carry a
  `BuilderExtraction`, and the action row grows an `extract … regex(…)` line with a source-field
  picker, a pattern box and a capture-group box.

- **Membership filters — `in` against a named source.** A collection filter can test an element's
  member against a `string_set`, a projection across another collection, or a list variable.

  ```
  sum(invoices[customerId in priorityCustomerIds].amount) > 10000
  ```

  Both sides are matched under the declared normalizers, and an empty or missing source selects
  nothing. A literal list — `country in ["de", "at"]` — is unchanged: it stays a plain field
  comparison and keeps enforcing the field's declared `operators:` list.

  Filters can now also name **document-level fields**. `customerId` belongs to the invoice and
  `priorityCustomerIds` to the document, and both have to resolve inside one predicate, so a filter
  reads through to the context the collection was read from. The element still wins.

- **Slicing — `take(path, n)` and `takeLast(path, n)`.** A bounded prefix or suffix of a collection
  in source order, composing with projection, filtering and aggregation.

  ```
  sum(take(orders, 3).total) > 5000
  count(takeLast(loginEvents, 10)[successful == false]) >= 3
  ```

  Order matters and is preserved: the second example counts failures among the last ten events, not
  the last ten failures.

- **Collection predicates — `every` and `any`.** True when every / at least one element satisfies a
  condition, short-circuiting as soon as the answer is decided. Over an empty or missing collection
  `every` is true and `any` is false.

  ```
  every(lineItems[quantity >= 1]) and not any(alerts[severity == "high"])
  ```

- **Keyed joins — `sumByKey`.** Aligns two or more collections on a shared member and returns one
  total per key: an outer join, missing values counted as zero, duplicates within a source summed,
  keys in first-seen order.

  ```
  min(sumByKey("month", salesByMonth.amount, refundsByMonth.amount)) >= 0
  ```

- **Date arithmetic — `daysBetween(from, to)`.** Signed whole calendar days, reading `date` and
  `date_time` fields and ISO-8601 literals. A missing or unreadable operand yields a missing result
  rather than an exception.

- **Absolute value — `abs(value)`.** Over a field, an aggregate, an arithmetic expression or a
  variable, preserving integer and decimal precision.

- **Per-member evaluation — `scope` in a manifest entry.** An entry may declare
  `scope: <collection>` to run its rules once per member instead of once per document. Rules are
  written from the member's point of view and fall back to the document for anything the member does
  not carry.

  `EvaluationResult` gains `members: List<MemberEvaluation>` and `RuleMatch` gains
  `scopeMember: String?`, both defaulted — an unscoped evaluation is unchanged, and so is its CLI
  output. Rejected at load time when the scope names no field or names a non-collection.

- **Field-to-field comparisons.** `amount > limit` and `orders[quantity >= threshold]` now parse. A
  legacy condition's right-hand side is a literal, so a path there previously failed with
  "Expected literal" pointing at the second field name.

- **The visual Builder edits every new form.** A `Function` operand kind covers `abs`,
  `daysBetween`, `every`, `any` and `sumByKey`, with one row per argument and each argument an
  operand in its own right — so `abs(sum(a) - sum(b))` is editable at every level. A path segment's
  drawer gained a *first / last n* control for `take` / `takeLast`, and its `where` rows accept `in`
  against a written-out list or the name of another field. Path decorations are held in order,
  because `take(orders, 3)[paid == true]` and `take(orders[paid == true], 3)` select different
  orders.

- **New sample: `subscription-billing`.** One rule set evaluated once per account, with a rule for
  each of the features above. It ships in the gallery and is executed by
  `SubscriptionBillingIntegrationTest` rather than only displayed.

### Fixed

- **Two spellings of one comparison disagreed, because only one normalized its literal.** On a field
  declaring `lowercase`, with the stored value `"paid"`:

  | Predicate | Matched before | Matches now |
  |---|---|---|
  | `orders[status equals "PAID"]` | yes | yes |
  | `orders[status == "PAID"]` | **no** | yes |

  `Compiler.compileFilterCondition` ran the literal through the field's declared normalizers;
  `ValueExpressionCompiler.compileLiteral` emitted it raw. So the symbolic spelling compared a
  normalized field value against an unnormalized literal and quietly failed to match — the same
  divergence at the top level, since it is the same literal compiler.

  A comparison now matches a text literal under the normalizers declared by the field on the other
  side, which is what the named-operator path has always done. A written-out list normalizes each item.
  Only text is affected: numbers, booleans, dates and variable reads are unchanged, and a comparison
  with no field operand has no normalizers to apply.

  **This changes which rules match.** A rule written `== "ACME"` against a `lowercase` field matched
  nothing before and matches now. If any rule depended on that non-match, it will start firing.
  `RULE-SPEC.md` and `docs/field-schema.md` documented the old behaviour as a reason to prefer the
  named operator; that advice now rests only on the declared `operators:` list, which `==` still skips.

- **A filter predicate naming a nested member silently matched nothing.** `parcels[origin.scans > 2]`
  compiled the dotted name into a *single* path segment, so the engine looked for a member literally
  called `origin.scans`, found none, and answered false for every element — after parsing, validating
  and compiling without complaint. The name is now walked one segment at a time, exactly as the same
  path is on a modern comparison. A schema that declares `origin.scans` as one flat member keeps
  resolving that way.

  This only affected the predicate spellings the parser routes through its legacy path, i.e. every
  operator except `==` and `!=`.

- **Filter predicates were barely validated.** `ValueExpressionValidator` inspected only the outermost
  node of a predicate and returned for anything else, so an unknown member went unreported for every
  operator but `==`/`!=`, and an `and`/`or`/`not` predicate was skipped entirely — including its
  modern half, which is checked everywhere else. The predicate is now walked like any other expression
  tree.

  **This can newly fail rules that previously loaded.** A misspelled member inside a filter is now an
  error rather than a predicate that quietly matched nothing. `ignoreCase` and operators with no
  comparison form (`between`, `startsWith`, `endsWith`, `regex`, `containsAny`, `containsAll`) are
  reported as diagnostics instead of only throwing at compile time.

- **Documentation claimed working filter spellings were rejected.** `RULE-SPEC.md`,
  `docs/expressions.md` and `docs/rules.md` all listed `equals` and `contains` as invalid inside
  `[...]`, and told authors to write `==` instead. `equals` has always worked there. The filter
  operator tables now match what the engine accepts.

- **An aggregate inside a filter was computed once for the whole collection.** The evaluation cache
  is keyed by the compiled node alone and was shared with the per-element contexts, so
  `orders[count(items) > 2]` answered every order with the first order's item count. Each element
  context now owns its cache.

- **A reused context served stale values.** `RuleEngine.evaluate` reset the variables but not the
  cache, so evaluating the same context twice — which the documented lifecycle allows — could return
  the first run's memoized aggregates.

- **Normalizers were not applied to nested or collection members.** Only a top-level field read its
  declared `normalizers:`; a member reached through a path compared raw text, so
  `invoices[customerId == "acme"]` did not match `"  ACME  "` even with `trim` and `lowercase`
  declared. Declared normalizers now travel with the compiled path and are applied on both sides of
  a filter comparison. **This changes evaluation** for rules whose nested or collection members
  declare normalizers — those rules now match what the same field would match at the top level.

- **Dates were unreachable from value expressions.** A `date` or `date_time` reaching a value
  expression became a missing value, so no comparison against one could hold. Dates now flow through
  as values, are compared at calendar-day precision, and serialize as ISO-8601 strings, which keeps
  result output unchanged.

## 1.6.0

### Added

- **List variables — the `add` clause and `contains`.** A `set` publishes one value; `add` collects
  several across rules, and `contains` reads the list back.

  ```
  rule "billing-from-refund" {
    description "A refund request is a billing matter."
    when
      not $topics contains "billing"
      and purpose contains "refund"
    then
      label "billing"
      add "billing" to topics
  }
  ```

  Written for labelling: many rules producing the same outcome from different evidence, where the
  expensive part of each rule is the text matching. A rule guards itself on the list, so once the
  outcome is recorded the rest stop before doing that work again — and the outcome is produced once
  instead of once per rule that could have reached it.
    - **The guard is cheap by construction.** `and` stops at its first false condition, and the engine
      evaluates the cheapest condition of an `and` first, so the list lookup runs before the text
      search whichever order they are written in. `or` is unaffected — a false first operand still
      lets the second decide.
    - **Set semantics.** Adding a value the list already holds changes nothing. Insertion order is
      kept, and the value arrives as a `List<Any?>` in `EvaluationResult.variables`.
    - **A rule may read the list it writes.** Unlike `set`, an `add` publishes its name in time for
      its own rule's condition — which is what lets the first rule of a guarded set guard itself. An
      unassigned list reads as missing, `contains` on missing is false, and `not` of that is true, so
      the first rule correctly fires. `set total = $total + amount` has no such starting point, so
      reading a `set` variable before it is assigned remains an error.
    - `contains` reads a list as membership and a text value as a substring. It is the one named
      operator a variable accepts; `field contains "literal"` is unchanged and still takes the
      named-operator path, with the field's declared `operators:` list enforced and its normalizers
      applied.
    - Several rules adding to one name is expected and produces no warning, unlike two rules `set`ting
      one name. A name written by both a `set` and an `add` is an error: a variable is either a plain
      value or a list.
    - Supported in the visual Builder — an assignment row switches between `set` and `add`, and a list
      variable offers `contains` — and in the editor's autocomplete and syntax highlighting.
    - New **Support Triage** sample demonstrating the pattern end to end.

- **An optional `else` branch on a rule.** A rule's `then` block says what to produce when its condition
  holds; an `else` block after it says what to produce when the condition is false.

  ```
  rule "order-tier" {
    description "An order of at least 1000 gets priority handling, anything smaller the standard path."
    when
      amount >= 1000
    then
      label "priority"
      set tierLevel = 2
    else
      label "standard"
      set tierLevel = 1
  }
  ```

  Use it where a business statement has two outcomes over one threshold. Expressing that without `else`
  takes two rules with the boundary written twice, and the two drift apart the first time someone changes
  only one of them.
    - The `else` block takes **exactly** what a `then` block takes — actions, `extract` clauses and `set`
      clauses — because it is parsed by the same code. A `set` in `else` publishes to the following rules
      just like one in `then`.
    - Optional, at most once per rule, and never empty: `else` with nothing in it is a parse error rather
      than a silent no-op, since it would be indistinguishable from having no `else` at all.
    - Exactly one branch produces output per record. Never both, never neither.
    - Validated at load time on both branches: an unknown action, a wrong argument type, a bad extraction
      pattern or a `$name` with no earlier `set` is reported in an `else` block exactly as in a `then`
      block. Two rules assigning the same variable is still a warning; **one rule** assigning it in both
      of its own branches is not, since only one of them ever runs.
    - `else` is now a keyword, so an action may not be named `else`. An action schema that declares one is
      reported as an error naming the declaration, not every rule that uses it.

- **The else branch in the editor.** `else` is highlighted as structure, autocompleted after a `then`
  block, and offers the same action and `set` completions inside it. In the visual Builder, **+ Else
  branch** under the THEN card adds the block and its first action; from there the ELSE card behaves like
  THEN, with its own **+ Action** and **+ Variable**. Removing the last else row drops the branch — an
  empty `else` is not a legal spelling of "absent", so the Builder does not pretend it is.

- **`stop` — a branch can end the run.** Written as the last statement of a `then` or `else` block, it
  means: collect this rule's output, then evaluate no rule declared after it.

  ```
  rule "blocked-country" {
    description "A payment to a sanctioned country is rejected outright; nothing else applies."
    when
      country in ["xx", "yy"]
    then
      label "rejected"
      stop
  }
  ```

  This is the first construct in the DSL by which one rule suppresses another. Use it for a guard whose
  verdict makes every rule below it inapplicable — a sanctioned counterparty, a missing mandatory field.
    - `stop` belongs to a **branch**, not a rule, so a rule can halt on one verdict and carry on with the
      other. An `else` block holding nothing but `stop` is valid: "halt when this condition does not hold".
    - It must be the block's **last** statement. Anything after it is a parse error — those lines would in
      fact still run, since a branch's output resolves before the halt, and a block with `stop` in the
      middle would read as if half of it were dead.
    - Reaches across rule files: an entry's files are one ordered list at runtime.
    - Compatible with variables. A variable published before the `stop` is in the result; the rules that
      would have read it are simply never reached.
    - `stop` is now a keyword, so an action may not be named `stop` — an action schema declaring one is
      reported as an error naming the declaration.
    - `EvaluationResult.stoppedBy` names the rule that halted the run, or is `null` when every rule ran.
      Without it a consumer cannot tell *"no further rule matched"* from *"no further rule ran"*.
      `EvaluateCli` emits it as `stoppedBy` when a rule halted.

- **`stop` in the Builder is a badge, not a typed word.** Each branch card has a **+ Stop** button; once
  added, `stop` shows as a removable badge pinned to the end of that branch. It is held as a flag rather
  than a row, so adding more actions or variables afterwards cannot push output below it — the generated
  DSL always writes `stop` last, which is what the parser requires. **+ Stop** disappears while the branch
  already has one.

- The Test panel reports the rules after a `stop` as **not evaluated**, with their own filter and count,
  rather than as *no match* — the run never tested them, so nothing is known about whether they would
  have fired.

- **A KYC onboarding sample (Germany).** Customer due diligence on a business customer, modelled on the
  obligations of the Geldwäschegesetz: identify the company and its representative, identify beneficial
  owners above the 25 % threshold, check the Transparenzregister, screen for sanctions and politically
  exposed persons, classify the risk. It is built to show the difference between the two constructs —
  `stop` appears exactly twice, where nothing the customer can upload would help (the order was never
  submitted, or there is a sanctions hit), while every requirement check uses an `else` branch and
  deliberately does *not* stop, so one run reports every outstanding document at once. That is what lets a
  frontend render "11 of 15 done" with the open items named, instead of repeating "further documents
  required" after each upload. `KycSampleBehaviourTest` pins that property.

- **Branch examples in the existing samples**, placed where the rules were already asking for one. Four
  turned out to fix real contradictions rather than illustrate a feature:
    - `access-control` — a blocklisted **admin** previously collected both `deny "ip-blocklist"` and
      `allow "full-access"`. The block list now ends the run, and `ip-filter.rule` moved ahead of the role
      rules. Its rate-limit rule also gained an `else` for the public default.
    - `product-recommendation` — an out-of-stock luxury item was both excluded *and* recommended to the
      premium shelf; the stock check now runs first and stops.
    - `loan-decisioning` — an applicant in arrears with good ratios got `decision "approve"` *and*
      `decision "decline"`, then was priced. The arrears knock-out now runs first and stops, as its own
      description always claimed it did.
    - `log-filter` — `suppress` did not suppress: a slow DEBUG entry was dropped *and* paged the SRE team
      at p1. The suppression rule moved to the severity file, first, and stops.
    - `financial-transactions` — a `payment-cadence` rule shows the plainest `else`: one boolean, two labels.

### Changed

- **`add` is now a reserved action name.** It is a `then`-block keyword, so an action schema declaring
  an action called `add` is rejected at load time with a rename suggestion (`append`), the same way
  `else` and `stop` already were. Unlike those two, an existing rule set *can* have been using this
  name — rename the action and the rules that write it.

- `EvaluationResult.matches` now means **every rule that produced output**, and `RuleMatch` carries a
  `branch: RuleBranch` (`THEN` / `ELSE`) saying why. A rule without an `else` block can only ever report
  `THEN`, which is the default, so nothing changes for a rule set that uses no branches. Code that reads
  `matches` as "the rules whose condition held" should filter:

  ```kotlin
  val conditionHeld = result.matches.filter { match -> match.branch == RuleBranch.THEN }
  ```

- `DecisionTree.matchedRules` keeps its existing meaning and lists **only** the rules whose condition
  held. A rule whose `else` branch fired is not in it — the trace answers "did the condition hold", which
  the `result` flag on that rule's own node already reports.
- **The documentation no longer claims rules are independent.** They are checked independently and all
  matches are returned, but evaluation order is a *guarantee* the engine makes and two constructs depend
  on it: `set` publishes only to the rules after it, and `stop` ends the run at its own position. The
  claim is corrected in `RULE-SPEC.md` §1 and §5.1, `docs/rules.md`, `docs/manifest.md` and
  `docs/performance.md` — and in the exported rule overviews, which stated it to the business reader.
- `EvaluateCli` emits `"branch": "then"|"else"` per match. Without it the JSON would read as if every
  entry were a rule whose condition held.
- Exported rule overviews (Markdown and Word) gain an *Otherwise* section per branching rule, list its
  else outcomes in the at-a-glance row as an alternative rather than a peer, and include them in the
  outcome summary. The explanatory note about branching rules is written only for a rule set that has one.
- The test panel reports an else result as its own **else** status, with its own filter and count. It
  previously had no way to express this: the roster derived "matched" from mere presence in
  `EvaluationResult.matches`, so an else-fired rule would have been reported as having matched.

### Fixed

- **The Builder corrupted a rule containing a quoted action argument.** `OperandText.quote` wrapped a value
  in quotes without escaping the ones inside it, so a `message "use the format \"HRB 123456\""` was
  re-emitted with the string ending mid-word and the rest of the rule unparseable. Because the Builder
  replaces the whole rule text on every edit, this corrupted the file rather than only rendering wrongly.
  Backslashes are escaped too.
- **A sample loaded after a project showed the project's rules against the sample's schema.** `applySample`
  never reset the project buffers, so `entryRuleSources` and All-files mode survived the switch and the
  workbench kept rendering the previous rule set — against the new schema, which made every field in it
  read as undeclared.
- **Samples now behave like projects in the workbench.** They carry their manifest into the editor, so the
  manifest run diagram has an entry to draw, and their rule files are registered in memory under the same
  manifest-relative paths a project uses. Everything that resolves a rule file by path — switching to a
  single file in the rule tree, the All-files view, every diagram, the rule-overview export — read from
  disk and silently produced nothing for a sample: switching files reported "Manifest base directory is
  not set", and All-files came up empty, which the diagrams rendered as "No valid rules to display".
- **`financial-transactions` shipped without two of its five rule files.** The gallery loaded it from a
  registry list that had drifted from the manifest, so the aggregate and boolean/date rules never appeared.
  `SampleRegistryTest` now pins the registry against each manifest, for the file list and the order.

### Removed

- **`shortCircuitByOutput` is gone** — removed from `RuleEngine`, from both `RuleEngineBuilder` entry
  points, and from the docs. **This is a breaking change** for any caller that passed it.

  The flag grouped rules by static output and closed each group at its first match. It was already
  incompatible with every feature added since 1.5.0 — variables (1.5.1), and now `else` and `stop` all
  failed the build when combined with it — because all three depend on declaration order, which grouping
  by output discards. Its own documentation noted that it was inert on the common rule set, where every
  rule emits a distinct value.

  There is no drop-in replacement, and `stop` is not one: `stop` halts the whole run, while the flag
  skipped rules within a single output group and kept evaluating the others. What replaces it in practice
  is ordering plus `stop` — put the cheap decisive guards first and the expensive rules behind them, and
  the expensive ones never run on records that were already settled.

  In exchange, `matches` is now **unconditionally** in declaration order, and `RuleEngine` lost the
  grouping path entirely.

## 1.5.1

### Added

- **Rule output variables.** A rule's `then` block can publish a named value with `set name = <value
  expression>`, and the rules after it read it as `$name`. Use it when several rules need the same
  computed value: express it once, refer to it by name.
    - The right-hand side is a full value expression — field, literal, aggregate, arithmetic, or another
      variable — so nothing new had to be added to the expression grammar. A `$name` is likewise usable
      wherever a value expression can stand, including inside a filter predicate and as an action
      argument (`score $turnover`).
    - Scope is one manifest entry and one evaluation. A variable reaches only the rules declared after
      the one that sets it (manifest file order, then in-file order), and only if that rule matched.
      Reading a variable nothing set yields a missing value, so the condition is `false` — evaluation
      never fails on it. Variables never touch the input data and never carry to the next record.
    - Validated at load time: a `$name` with no earlier `set` is an error with a "did you mean"
      suggestion, as is a variable named like a schema field; two rules assigning the same name is a
      warning.
    - **`shortCircuitByOutput` cannot be combined with variables** — it evaluates rules by output group
      rather than in declaration order. `RuleEngineBuilder` now fails the build with an explicit message
      instead of producing an order-dependent result.
    - `EvaluationResult.variables` carries the final value of each variable, and `RuleMatch.assignments`
      says which rule published what.
    - Exported rule overviews (Markdown and Word) list what each rule publishes, and state the ordering
      caveat — but only for a rule set that actually uses variables.
- **Variables in the visual editor.** The THEN block has a `+ Variable` row whose right-hand side is
  the same operand chip a condition row uses; in-scope variables appear as `$name` in every operand
  picker and in code-mode autocompletion, alongside the `set` keyword. The rule inspector lists what a
  rule publishes, and the test panel shows the value each variable took for the input you ran.
- The `warehouse-shipments` sample now computes the shipment weight once in `shipment-totals.rule` and
  reads it back as `$totalWeightKg` / `$fragileWeightKg`.

- **Three diagrams beside the existing rule tree, picked in the Diagram toolbar.** The per-rule
  condition tree is unchanged and stays the default.
  - **Manifest run** — the selected manifest entry as the single connected unit the engine actually
    runs: every rule of every listed `.rule` file on one spine, numbered in evaluation order
    (manifest file order, then in-file source order), ending in the `EvaluationResult` that collects
    them. File names are shown as quiet provenance bands labelled *grouping only — no runtime
    boundary*, because `RuleEngineBuilder` flattens an entry's files into one list and validates and
    compiles them as one set.
  - **Outcome map** — rules grouped by the output they produce. Draws the display family
    (`assessment:transit`) and the real `RuleEngine.staticOutputKeys` bucket
    (`assessment:transit:green`) as separate layers, and states each bucket's size, so it is visible
    when `shortCircuitByOutput` would have no effect on a rule set.
  - **Field flow** — schema field → rule → outcome, with selection isolating everything on a path
    through a node. Reports schema fields no rule reads, which no other view can show. Field paths
    written inside a collection filter resolve against that collection, so
    `parcels[origin.hub == "HAM"]` counts as a use of `parcels.origin.hub`.
- **Trace diagram** in Test mode, beside the existing results list. Renders each rule's recorded
  decision tree with its nesting instead of a flat condition list, coloured by result with the
  actual value beside the expected one. A false `and` is annotated with where evaluation stopped:
  `AndExpression` returns on the first false child without calling the trace collector for the rest,
  so a condition that was never evaluated is absent from the tree rather than present and undecided.
- `RuleResult.traceTree` (`ui.tester`) — the rule's decision tree as `TraceNode` / `TraceNodeType`,
  a `commonMain` mirror of the core's JVM-only `DecisionNode`. The existing flat `traceRows` is now
  derived from it in the same walk, so the two cannot disagree about what was evaluated.
- The **Usages** tabs of the schema and action editors, previously placeholders reading *"will be
  shown here in a later phase"*, now render the field flow and the outcome map respectively.

- **An optional `description` clause on a rule.** One sentence, written for a human rather than for
  the engine, placed directly after the opening `{` and before `when`:

  ```
  rule "insurance-required" {
    description "A valuable shipment needs a cover note."

    when
      shipment.declaredValue between 1000 25000
    then
      assessment "insurance:required"
  }
  ```

  It has no effect on matching and may appear at most once per rule. Omitting it produces a
  `Severity.WARNING` diagnostic, never an error, so every existing rule file keeps loading,
  compiling and evaluating unchanged. A `#` comment is not a substitute: comments are stripped by
  the lexer and never reach the engine, so they cannot appear in an export. Documented in
  `RULE-SPEC.md` §5.2 and `docs/rules.md`, and carried by every sample rule set.

- **Rule overview export, as Markdown for a wiki or as a Word document for a customer.** *Export
  Overview* in the Rule Editor toolbar writes the whole selected manifest entry — not just the open
  file — as a document for someone who has never seen the DSL: an index of every rule linked to its
  section, a table of the outcomes the rule set can produce, then one section per rule file. Each
  rule leads with its `description`, restates its condition as sentences, and shows the exact
  rule-language condition underneath for whoever has to verify it.

  The `.docx` is written directly as OOXML and zipped with `java.util.zip`, so this adds **no new
  dependency** to either module; Word, LibreOffice and Google Docs do all layout and pagination, and
  a PDF is one File → Export away. Both renderers are pure functions of their input — no timestamp
  in the Markdown, fixed ZIP entry times in the `.docx` — so regenerating an unchanged rule set
  produces byte-identical output and a wiki page shows no edit.

  The export reads the entry's rule files from disk, so unsaved edits in the open file are not
  included; the status line says so rather than letting an author hand over a version they believe
  contains their change.

- **`ruleengine.export`** — the model and renderers behind that export, usable without the UI:
  `RuleCatalogBuilder.fromManifest(path, entryId)` builds a `RuleCatalog` (in `ruleengine.export.dto`)
  from a manifest, `MarkdownCatalogRenderer` and `DocxCatalogWriter` render it. `PlainLanguageRenderer`
  restates a condition as a `PlainCondition` tree — `PlainAll` / `PlainAny` / `PlainNot` / `PlainLeaf`
  — rather than as finished text, so each output format can render the boolean structure its own way.

  The wording is deliberately literal and never infers intent: `> total * 0.25` becomes *"more than
  … multiplied by 0.25"*, not *"more than a quarter of …"*. Saying what a rule is **for** is the
  author's job, in the `description` clause. Field paths are labelled from the schema, preferring a
  declared `alias` and otherwise deriving one (`shipment.customer.tier` → *Customer › Tier*), and
  date comparisons read as points in time rather than quantities (`gte` → *"is on or after"*, and
  *"at"* instead of *"on"* for a `date_time`, whose comparison keeps the time).

- **`ValueExpressionRenderer`** (`ruleengine.dsl.ast`) — renders a parsed expression back to DSL-like
  text, used to label traced conditions, to display them in the UI diagram views and to print the
  rule-language line in an export. What it writes parses: a `BetweenLiteral` renders as
  `between 1000 25000` rather than as a `1000..25000` range the DSL does not accept.

- `DecisionNode.actual` — the value a condition actually found, alongside the `expected` it was
  compared against. Omitted from the JSON on nodes that do not report one, so existing trace output
  is unchanged for them.

- **A new `ruleengine-model` module** holding the shared vocabulary — the field and action model and
  `OperatorNames` — with **zero dependencies**, so a consumer that only needs to describe a schema
  does not pull in the engine. `ruleengine-core` exposes it as an `api` dependency, so it arrives
  transitively and an existing build needs no new coordinate. See the package move under *Changed*.

- **`OperatorNames`** (`ruleengine.core.domain`) — every operator name the engine understands, in one
  place: the canonical names, the symbolic and legacy spellings, and the alias table
  `OperatorUtils.normalizeOperator` reads. The names are shared vocabulary between the parser, the
  validator, the compiler, the trace, the export and the visual editor, and spelled as literals at
  each of those sites they drift.

- **A project is opened and saved as one thing.** The workbench treated the manifest, the schema, the
  actions and each rule file as unrelated buffers behind six independent load/save buttons: a loaded
  schema had no on-disk identity and could not be written back, a save wrote only `manifest.yaml`,
  and a second manifest could not be opened at all — the first one's rules stayed on screen while the
  base directory silently pointed at the new file. *Open Project…* now reads a manifest and
  everything it references; *Save Project* writes the rule files, the schema, the actions and the
  manifest together, with the manifest written **last** so a save that fails part-way never leaves an
  index naming files that were not written; *Save Project As…* copies the project to a new folder and
  rewrites its links for the new depth. Before anything is overwritten the workbench asks: about
  unsaved work, about writing to a file shared with other projects, and about a file that changed on
  disk since it was opened — dirtiness being a comparison against what was last read or written, so
  it can tell *edited* from *edited back to what it was*.

- **A light theme, and the workbench remembers which one you chose.** The colours were fixed
  constants, so the app was dark and that was that. `ui.theme` now defines an `AppPalette` with a
  `DarkPalette` and a `LightPalette` behind it, `ThemeController` switches between them, and every
  colour in `ui.Theme` reads from the active palette rather than from a literal — so a component
  cannot stay dark because someone spelled a hex value inline. The ☀/☾ button in the top bar
  toggles it, and `ThemePersistence` writes the choice to the OS user preferences, so it survives a
  restart instead of resetting to dark on every launch.

- **The side panels collapse, and say what they are while collapsed.** The right inspector/simulate
  panel and the Builder's rule tree each fold down to a narrow strip with their title rotated onto
  it, so the editor takes the width back on a small window without the user losing track of what was
  there or how to bring it back. The diagnostics list starts collapsed — it should not compete with
  the center panel for height on first launch — while its severity badges stay in the header, so a
  collapsed panel never hides that there are errors. The split between the schema panel and the
  editor is draggable.

- **A rule tree down the left of Builder mode** — every rule of the selected manifest entry, grouped
  under the file it is written in, each marked with its validation status. Picking a rule in another
  file opens that file and selects the rule, so navigating a multi-file entry no longer means going
  through the file dropdown first and then hunting for the rule.

- **The workbench manages every entry of a manifest, not just the first one.** A manifest may declare
  several independent entries — each with its own schema, actions and rule files — and the engine has
  always run them independently, but the editor collapsed a project to `entries.first()`, hid the
  rest, and refused a plain Save rather than silently dropping them. All entries are now held, one is
  active, and the whole workbench follows it.

  - **Pick one.** An entry dropdown sits in the top bar rather than in the Manifest area, because the
    choice governs every other area: the schema, the actions and the rule files on screen all belong
    to the entry named there. Switching replaces every buffer, so it goes through the same
    unsaved-work question as opening a project — and answering it now resumes the switch instead of
    leaving the user to repeat it.
  - **Add one.** *+ New entry…* in the dropdown, or *+ Add entry* in the Manifest area, appends an
    empty entry and makes it active; its name is typed into the entry's card. Nothing is written to
    disk: an entry the user may yet abandon should not litter the project with empty YAML. Whatever
    is then linked or written in the Schema, Actions and Rules areas attaches to that entry.
  - **Remove one.** *Remove…* asks the question that matters — **Delete files** / **Remove from
    manifest** / **Cancel** — and names the files on both sides of it. Only what the entry owns
    exclusively can be deleted: a schema a second entry also references, or one linked from outside
    the project with `../`, is kept regardless and the dialog says so. Either answer rewrites the
    manifest immediately, so the index never names a file that was just erased. The last entry cannot
    be removed, since `entries` is required.

  The Manifest area's Builder tab renders one card per entry, with the id, the schema and action
  paths and the rule-file list editable per entry, and marks the one being edited. Its **Checks** tab
  now reports duplicate entry ids — which `RuleEngineBuilder.selectEntries` rejects outright, and
  which are otherwise invisible enough to happen by accident — and reports the blank-path checks per
  entry rather than for the first one only.

### Changed

- **BREAKING: the domain model moved out of `ruleengine.core.domain` into `…domain.dto` subpackages.**
  Every type, member and default value is untouched — only the package changed, so updating an
  integration is an import rewrite:

  | Types | New package |
  |---|---|
  | `FieldSchema`, `FieldDefinition`, `FieldType` (with its `isStructure` / `isTemporal` extensions), `FieldTypeCategories`, `FieldId` | `ruleengine.core.domain.dto.field` |
  | `ActionSchema`, `ActionDefinition`, `ActionArgType` | `ruleengine.core.domain.dto.action` |
  | `RuleMatch`, `RuleAction`, `EvaluationResult`, `OperatorId`, `NormalizerId` | `ruleengine.core.domain.dto` |

  So `import ruleengine.core.domain.FieldSchema` becomes
  `import ruleengine.core.domain.dto.field.FieldSchema`. `ruleengine.core.domain` keeps the logic
  that operates on the model — `FieldPathResolver`, `FieldPathResolution`, `TemporalFormat` and the
  new `OperatorNames`.

  The two files that previously held all of it, `FieldModels.kt` and `ActionSchema.kt`, declared
  eleven and four types respectively, against the one-declaration-per-file rule the rest of the
  codebase follows; the `dto` subpackage matches `ruleengine.schema.dto`,
  `ruleengine.evaluator.context.dto` and `ruleengine.evaluator.trace.dto`, which were already laid
  out this way.

- Internal packages split so that no source directory holds more than eight files. `Compiler`,
  `Validator` and `ValidationResult` stay in `ruleengine.compiler` — the public entry points are
  unmoved — while its helpers moved to `ruleengine.compiler.support` (`OperatorSupport`,
  `LiteralValidation`, `Suggestions`, `FieldPathMessages`) and `ruleengine.compiler.value`
  (`ValueExpressionCompiler`, `ValueExpressionValidator`). In the evaluator, the `ExpressionValue`
  hierarchy moved to `ruleengine.evaluator.compiled.value.result` and `CompiledPathSegment` to
  `ruleengine.evaluator.compiled.value.path`. `ruleengine.dsl.ast` and
  `ruleengine.evaluator.context.dto` deliberately stay flat: nearly every type in them is a direct
  subclass of a sealed hierarchy, and Kotlin requires those to share a package with their parent.

- `ruleengine-ui` reorganised so every feature package keeps its models and enums in a `model`
  subpackage (`ui.builder.model`, `ui.tester.model`, `ui.project.model`, …), with behaviour grouped
  by role beside it (`ui.workbench.areas`, `ui.builder.view`, `ui.diagrams.render`). UI-internal only.

- `Compiler` internals, with no change in behaviour: the `compileDecimalCondition` /
  `compileIntegerCondition` pass-throughs are inlined into `compileCondition` next to the existing date
  branch, `compileFilterExpression` is private, the `when` over `FieldType` is exhaustive instead of ending
  in a catch-all `else`, and the four `@Suppress` annotations that covered the removed helper and the two
  wrappers are gone.

- **BREAKING for `ruleengine-ui` consumers: `FieldUsage` moved to `ruleengine.export`.** The walk that
  answers *"which field paths does this rule read"* was declared in `ui.diagrams.model` but is not a
  UI concern — the export needs the same answer, and `ruleengine-ui` must not own logic the core
  depends on. Only the package changed; `fieldsOf(rule)` and its behaviour, including resolving a
  filter's paths against the collection it filters, are untouched.

- **The numeric and date operator compilers no longer carry their own alias tables.**
  `IntegerOperator`, `DecimalOperator` and `DateOperator` each matched on `cond.operator.lowercase()`
  against their own list of spellings (`"equals", "==", "=", "eq"`, …), duplicating
  `OperatorUtils.normalizeOperator`, which `Compiler` had already applied. Each now receives the
  canonical name from its caller and matches one `OperatorNames` constant per operator, so a fourth
  spelling of an operator cannot be recognised in one place and missed in another. Behaviour is
  unchanged: the aliases they listed are exactly the ones the canonical table already maps.
  `RuleAstToBuilderMapper` held a fifth copy and now normalises through the engine, translating only
  what the Builder genuinely displays differently (`gt` → `>`).

- **The Rule Editor's action buttons moved onto their own row, under the mode tabs.** Sharing a row
  with the tabs made the two compete for width: the tabs are fixed, so the actions absorbed every
  shortfall, and which actions exist depends on the mode — there is no fixed amount to design
  around.

- **Where an action lives now follows what it acts on.** Linking a schema is no longer a toolbar
  button but a header at the top of the Schema and Actions areas, showing which file is bound and
  badging it *SHARED* when it lives outside the project or *NOT FOUND* when it does not resolve —
  replacing a schema is a statement about *that* file, and the user needs to see what they are
  replacing. The top bar keeps only the project actions, and Save Project carries a dot when there is
  work not yet on disk. Every question the workbench asks goes through one dialog with three answers
  rather than two, because *"unsaved changes"* and *"this file is shared"* both have a safe third
  option — *Discard*, *Copy into project* — that a yes/no dialog forces the user to guess at.

- **A new file the user never named is named after its entry, once a project has more than one.**
  Two entries both defaulting to `schemas/schema.yaml` would have the second overwrite the first, so
  a multi-entry project writes `schemas/<entry>-schema.yaml`, `schemas/<entry>-actions.yaml` and
  `rules/<entry>/<rule-id>.rule`. A single-entry project keeps the plain names it always had — there
  is nothing there to collide with. Paths already written in a manifest are never renamed.

- **The manifest is the session, and the manifest text is a view of it.** The Manifest area edited
  one copy of the manifest while `ProjectSaver` regenerated the file from another, so anything typed
  in that area was silently discarded on the next Save. The project session is now the single source
  of truth: the Manifest area writes onto it, and the YAML buffer and parsed model that feed the
  rule-file picker, the rule tree and the diagrams are produced from it. `ProjectSession` therefore
  carries `entries` and `activeEntryId` instead of one flattened entry; `entryId`, `schemaLink`,
  `actionsLink` and `ruleFiles` remain, as views onto the active entry.

- Every sample rule set — the five bundled in the workbench and the fixtures under
  `ruleengine-core/src/test/resources` — now carries a `description` on each rule, as do the complete
  worked examples in `RULE-SPEC.md` §5.7 and §8. Two `#` comments that had said the same thing as the
  new clause were removed rather than left to drift out of sync with it.

- `Parser` was split into `TokenCursor`, `LiteralParser` and `ThenBlockParser`, which share one read
  position. No grammar change beyond the `set` clause.
- `FieldUsage.fieldsOf` now also walks a rule's `set` expressions, so the field-flow diagram does not
  lose a field when a rule set moves an aggregate into a variable.
- A boolean literal operand in the visual builder is written back unquoted. Previously
  `isActive == true` round-tripped as a comparison against the text `"true"`.
- A `[` opening a path filter must now sit on the same line as the segment it filters, so an action's
  list argument on the line after a `set` clause is no longer read as a filter.

### Fixed

- **A compilation error names the rule it came from.** Every `CompilationException` read
  `Compilation failed for rule <unknown>`, whatever went wrong and however many rules were being compiled.
  The operator layer had always accepted a `ruleId` and passed it on, but `Compiler` fed it a private helper
  that ignored its argument and returned a constant `null`, so the id never left the `RuleAst` and the whole
  plumbing was inert. The rule id is now threaded from `Compiler.compileRule` through every condition,
  action, extraction, filter segment and value expression, so a failure points at the rule to fix instead of
  at the whole file.

- **An equality filter written with a named operator could not compile.**
  `count(parcels[status equals "paid"])` failed with `Operator 'equals' is not supported in filter segments`,
  as did the `eq` and `=` spellings. The filter compiler branches on the canonical name produced by
  `OperatorUtils.normalizeOperator` — which maps `==`, `=` and `eq` to `equals`, and `>` to `gt` — but its
  branches were written against the raw symbols. So `==`, `>`, `>=`, `<`, `<=` and the `greater_than` /
  `less_than_or_equal` aliases were keys the `when` could never receive; only `gt`, `gte`, `lt`, `lte` and
  `!=` ever matched, and `equals` fell through to the error. Every spelling of `equals`, `gt`, `gte`, `lt`
  and `lte` now works in a filter segment.

- **A non-string element of a list action argument became the parser's debug output.** `label ["a", 1]`
  compiled to the arguments `["a", "NumberLiteral(value=1)"]`, because list items were translated with a
  string cast that fell back to the AST node's `toString()`. List items now go through the same literal
  conversion as a top-level argument, at any nesting depth.

- **A boolean action argument was silently dropped.** `extract iban regex("(\d+)", 1) label true` compiled
  the argument to `null`: the translation handled strings, numbers and lists, and let everything else fall
  through to a `null` value. Booleans are translated now, and a literal the compiler genuinely cannot
  translate raises a `CompilationException` naming the action instead of producing a `null` argument.

- **`ignoreCase` in a filter segment was ignored rather than applied.**
  `count(parcels[status equals "paid" ignoreCase])` compiled to a case-*sensitive* comparison and quietly
  returned the wrong count, because the comparison used inside a filter has no case-insensitive mode. The
  modifier is now rejected at compile time with a message naming it, so the rule fails loudly instead of
  evaluating to something other than what it says. A rule that relied on it therefore no longer compiles;
  case-insensitive filtering has to be expressed with a normalizer on the filtered field.

- Conditions whose operand is an expression — an aggregate (`count(...)`, `sum(...)`, `min(...)`),
  arithmetic, or another field — are now recorded in the decision tree. `ComparisonCompiledExpression`
  accepted a `TraceCollector` and never called it, so those conditions were missing entirely and a
  rule built only from them produced a verdict with an empty trace. Filter predicates inside a path
  remain untraced by design: they run once per element, so tracing them would emit one node per row.

- **A filter comparing a nested field locked the Builder.** `count(parcels[origin.hub == "HAM"])` — a rule
  the parser, validator and evaluator all accept, and which the `warehouse-shipments` sample ships — was
  reported as *"Rule uses a function argument the Builder cannot represent"* in both Builder mode and the
  rule table. `RuleAstToBuilderMapper` read the filter's field with `path.singleOrNull()`, so any dotted
  path failed to map, which failed the whole `count(...)` operand and locked the rule. A filter field may
  now be a path of any depth; only a filter nested inside the filtered path stays unsupported, because a
  Builder filter row has no shape for it. The `where` drawer offers nested members by their dotted path, so
  such a filter is editable and not merely displayed.

- **A text value that looks like a number lost its quotes on the way back to DSL.** Editing
  `ip startsWith "10."` in the Builder wrote `ip startsWith 10.`, which the lexer reads back as a number:
  the value silently changed type. The quoting rule used `toDoubleOrNull`, which accepts `10.`, `1e5` and
  `Infinity` — none of them forms the DSL writes for a number. It now matches the canonical integer and
  decimal forms only, and quotes everything else.

- **The editor offered a `description` clause the parser rejected.** `SyntaxHighlighter` coloured
  `description` as a structure keyword and the autocompletion offered `description ""`, but
  `Parser.parseRule` expected `when` immediately after `{`, so accepting that completion produced
  `Expected 'when' block`. `RuleAst.description` had existed since the AST was written and was never
  populated by anything. The clause is now parsed, and the field carries what the author wrote.

- **A manifest entry with no files yet disappeared when the manifest was written.**
  `ManifestYamlBridge.toYaml` dropped any entry whose schema, actions and rules were all blank —
  which is exactly what a freshly added entry is, so it was deleted before it could be given
  anything. An entry is now kept as soon as it has an id.

- **Saving a multi-entry project no longer has to be a Save As.** Plain Save was refused with *"This
  manifest defines several entries. Use 'Save Project As…' to write a single-entry copy"*, because
  the saver rebuilt the manifest from the one entry it had loaded. It writes the buffers of the
  active entry and re-emits every entry, so the ones not on screen survive; *Save Project As…* copies
  the rule files of all of them and relocates each entry's external links.

- **A toolbar button squeezed for width rendered its label one letter per line.** A `Row` of buttons
  narrower than their combined width compresses the last one, and a wrapping caption then reads as a
  column of characters where a button should be — *Validate* was the one that showed it. Button
  captions are single-line and non-wrapping now, and the Rule Editor's action row scrolls instead, so
  a toolbar too full for its window stays usable rather than mangling its last control.

## 1.4.0

### Added

- **`RuleEngineBuilder`** (`ruleengine.builder`) — one call turns a manifest into ready-to-use
  engines: `RuleEngineBuilder.fromManifest(manifestPath)` resolves every referenced file relative to
  the manifest, loads the field and action schema, parses the rule files in manifest order, validates
  and compiles them, and returns a `LoadedRuleEngine` per manifest entry keyed by entry id (pass
  `entryId` to build a single entry). `LoadedRuleEngine` bundles engine, schema, action schema and
  validation warnings, and its `evaluate(input)` performs normalisation and evaluation, so callers no
  longer have to keep the schema in a second variable or wire `RuleContext` /
  `PreparedRuleContext` themselves. Any problem — missing or unreadable file, path escaping the
  manifest directory, unknown entry id, validation error — raises the new `RuleEngineBuildException`
  with a message naming the manifest, the entry, the concrete cause and every diagnostic, so a
  half-initialised engine can never be used. Documented as the quick start in the
  [Integration Guide](docs/integration-guide.md); the manual pipeline is retained as "Advanced Rule
  Engine Preparation".
- **`ManifestPathResolver`** (`ruleengine.manifest`) — moved from the UI module into core; rejects
  manifest paths that escape the manifest's own directory. `RuleEngineBuilder` applies it to every
  referenced schema, action and rule file.

### Changed

- `ManifestLoader.load` now reads through the shared 25 MB bounded reader used by the other loaders,
  so an oversized manifest raises `InputTooLargeException` instead of being read into memory.
- `EvaluateCli` manifest mode delegates to `RuleEngineBuilder`. Load failures (missing schema, broken
  rule file) now report the builder's detailed message and exit with code `2` instead of `3`;
  evaluation output is unchanged.

- **`warehouse-shipments` example bundle** — the documented worked example for nested data
  (`ruleengine-core/src/test/resources/warehouse-shipments`): a `shipment` object read by plain
  conditions plus `parcels` and `checkpoints` collections read by aggregates and filters, with two input
  files and `WarehouseShipmentsIntegrationTest` asserting the full outcome of each. Linked from
  [Field Schema](docs/field-schema.md#nested-data) and available in the UI sample gallery as **Warehouse
  Shipments**.

### Fixed

- **A nested path works in a plain condition.** A field declared through nested `fields:` blocks was only
  reachable from a value expression (`sum(...)`, `count(...)`, `==`, `!=`); a plain comparison such as
  `shipment.transitDays >= 3` or `shipment.customer.tier equals "gold"` failed with
  `Unknown field '<path>' in condition`, because `Validator`, `Compiler` and `PreparedRuleContext` each
  looked the whole dotted name up as a single flat schema key. Path resolution now lives in one place
  (`FieldPathResolver` in `ruleengine.core.domain`) and is shared by all three, so every operator, the
  declared `normalizers:` and a `format:` apply to a member of an `object` exactly as they do to a
  top-level field. A field id that spells out the path itself keeps precedence, so existing flat schemas
  are unaffected. A path that reads through a `collection` — which yields one value per element and
  silently never matched before — is now rejected with a message naming the collection and pointing at
  `count(...)` / `sum(...)` / a filter. Nested paths are also offered by editor autocomplete.

- **Builder mode: filtered and nested paths are editable again.** A path in an aggregate, calculation
  or field operand is now a row of breadcrumb pills, each picked from the members the schema declares
  at that depth, with the restrictions of the selected segment in a `where` drawer titled after it —
  so `count(orders[status == "paid"].items)` no longer needs the code editor. Field names are never
  typed: the free-text fallback that appeared for undeclared segments is gone, and a value the schema
  does not declare — a path whose root is only present in the raw context, which the engine allows
  with a warning — is marked instead of being offered as a valid choice or silently rewritten.
  Repointing a segment now drops the tail that was resolved against the old member, removing a
  segment keeps the tail, and every operand panel echoes the DSL it generates.

## Release 1.3.0

### Added

- **Nested data in the field schema** — two new field types, `collection` (a list of records) and
  `object` (a single nested record), each declaring its members under a recursive nested `fields:`
  block. Declared members are validated at any depth, so `sum(orders[status == "paid"].items.price)`
  is checked segment by segment instead of being assumed numeric. Schemas that do not declare members
  keep working exactly as before.
- **Boolean conditions** — `isActive equals true` now parses, validates, compiles and evaluates.
  Boolean values may arrive as a `Boolean` or as the strings `"true"` / `"false"`.
- **Date support** — `date` fields are usable with `equals`, `gt`, `gte`, `lt`, `lte` and `between`,
  comparing quoted ISO-8601 calendar dates. Input may be a `LocalDate`, `LocalDateTime`, `Instant` or
  ISO string; anything carrying a time is reduced to its date.
- **Implicit AND** — conditions on consecutive lines are joined with `and`, so the keyword is optional.
- **Zero-argument actions** — an action declared `argTypes: []` is written in a rule as the bare name.
  The action schema already supported this; the parser demanded a literal.
- **Advanced expressions in the visual Builder** — aggregates, arithmetic, filtered paths at every
  level, `not` and `ignoreCase` are editable as condition rows instead of showing "Advanced syntax
  detected". Aggregate and calculation operands are gated to numeric comparisons. The schema editor
  edits nested fields as indented child rows.
- **Documentation fidelity test** — every rule example in `RULE-SPEC.md`, `README.md` and `docs/` is
  parsed, validated and compiled by an automated test, so the specification handed to AI assistants
  cannot drift from what the engine accepts.
- **`date_time` field type** (aliases `datetime`, `timestamp`) — a date with a time of day, compared at
  time precision with the same six operators as `date`. Input may be a `LocalDateTime`, a `LocalDate`
  (starting at midnight), an `Instant` (resolved at UTC) or a string.
- **Every field type is offered by "+ Add field"** in the visual schema editor. The menu previously
  hardcoded seven templates and omitted `string_set`, `date` and `date_time`, so those types were
  reachable only by adding a blank field and changing its type afterwards. The list is now derived from
  one place and a test asserts it covers every type.
- **Operator chips are filtered to the field's type** in the schema editor, so a `date` field no longer
  offers `contains` and a `string_set` field no longer offers `between`. An operator already present in a
  loaded schema but invalid for its type is still shown, marked, so it can be removed.
- **Unknown operator names are rejected when the schema loads** — `operators: [greaterThan]` now fails
  with `Unknown operator 'greaterThan' for field 'amount'` instead of silently restricting the field to a
  name no rule can use. `starts_with` / `ends_with` / `startswith` / `matches` are accepted as aliases of
  the canonical names, so schemas written by earlier versions of the editor keep loading — and their
  conditions now work rather than being rejected.
- **Per-field date format** — a `date` / `date_time` field may declare `format: "dd.MM.yyyy"`, a
  `DateTimeFormatter` pattern that governs both the incoming data value and the literal written in every
  rule for that field. Omitting it keeps today's ISO-8601 behaviour, so existing schemas and rules are
  unaffected. A `format` on a non-date field, a malformed pattern, or one that cannot represent a
  complete value is rejected when the schema loads. The schema editor edits the pattern per field, and
  the Builder and autocomplete offer value placeholders in it.

### Fixed

- **RULE-SPEC.md documented three features the engine could not perform** — implicit AND, boolean
  conditions and date fields. All three are now implemented rather than removed from the spec.
- **Rule replacement in the editor** dropped or duplicated rules whose body contained a `}` — most
  often inside a regex pattern such as `"^DE\d{18}$"`. Rule bodies are now located by counting braces
  outside strings and comments instead of by a regex.
- **Filter segments were validated against the top-level schema**, so `sum(transactions[label == "risk"].amount)`
  reported a spurious `Unknown field 'label'`. Filter fields now resolve against the element being
  filtered.
- **Bundled samples did not load.** Five rules used `between 20 and 500`, which the DSL does not accept;
  one used a zero-argument action the parser rejected; one used `between` on a field whose schema
  omitted that operator. All samples are now covered by a test.
- The editor's `between` value placeholder inserted the numeric `0 100` for date fields as well, which
  could never compile; date fields now get a pair of quoted date samples.
- **The schema editor offered operators and normalizers the engine does not have.** `not_equals`,
  `not_contains`, `isEmpty` and `isNotEmpty` were selectable but have no implementation anywhere in the
  engine; `starts_with` / `ends_with` were spelled in a form the validator did not recognise; `regex` was
  missing although text fields support it. The normalizer chips were missing `collapse_whitespace` and
  `remove_punctuation`, and the YAML autocomplete offered `ascii_fold`, which does not exist and produced
  a schema that failed to load.
- Normalizer chips are no longer shown for types the engine never normalizes (anything but `text` and
  `string_set`).
- The YAML `type:` autocomplete was missing `collection` and `object`, and spelled `stringSet` where the
  editor writes `string_set`.
- The rule editor's schema **Example** button inserted `greaterThan` / `lessThan`, which are not engine
  operators, so the example it produced rejected every rule written against it.
- The Builder offered `in` on numeric fields and `contains` on `string_set` fields; the engine allows
  neither.
- `sample-schema.yaml` declared `starts_with` on its `country` field, so `country startsWith "DE"` could
  not be used with the schema the README quick-start points at.

### Updated
- Upgraded `jackson` to `3.2.1`
- Upgraded `kotlin` to `2.4.10`


## Release 1.2.0

### Added

- **Sample Gallery** in the UI workbench — a built-in library of ready-to-run rule sets covering four
  domains: Financial Transactions (fraud detection, VIP classification, rent-payment routing),
  Log Filter (severity routing, slow-request escalation, service alerts), Product Recommendation
  (premium badges, category boosts, discount eligibility), and Access Control (IP filtering,
  role checks, time restrictions). Open a sample to instantly populate the editor with schema,
  actions, and rules without touching the file system.
- **Rule Table view** (`TABLE` tab in the center panel) — a scrollable, structured overview of all
  loaded rules showing conditions, actions, and match status at a glance, complementing the
  existing Code, Diagram, and Test views.
- **Manifest file picker** — a dropdown (☰) in the rule-editor header lets you switch between
  individual rule files within a manifest entry. In Diagram and Test views an additional
  **All files** option loads every rule file of the selected entry in one step.

### Changed

- Upgraded `kotlinx-coroutines-core` to `1.11.0` (explicit override of the older version
  previously pulled in transitively by Compose).

## Release 1.1.0

### Added

- **Output-based short-circuit evaluation** in `RuleEngine`, enabled via the new
  `shortCircuitByOutput` constructor flag (defaults to `false`). When enabled, rules are
  grouped by every static output they produce (`actionName:value`); each group is evaluated
  only until its first matching rule, then evaluation moves to the next group. This avoids
  redundant (and potentially expensive, e.g. regex) evaluations for outputs that are already
  settled — a significant speedup for large rulesets where many rules map to few outputs.
  A rule with multiple static outputs belongs to multiple groups and is reported at most
  once; rules with dynamic outputs (e.g. regex extraction) are always evaluated.

### Changed

- **BREAKING:** Removed the unused `schema` parameter from the `RuleEngine` constructor.
  Update call sites from `RuleEngine(compiledRules = rules, schema = schema)` to
  `RuleEngine(compiledRules = rules)`.
- With `shortCircuitByOutput = true`, matches are returned in output-group order rather than
  rule-declaration order. Default behavior (`false`) preserves full in-order evaluation.

