# Performance

How fast the engine is, how it was measured, and which knobs actually change the number.

---

## Table of Contents

1. [The Benchmark](#1-the-benchmark)
2. [Measured Numbers](#2-measured-numbers)
3. [Where the Time Goes](#3-where-the-time-goes)
4. [Tuning](#4-tuning)
5. [Thread Safety](#5-thread-safety)
6. [Running It Yourself](#6-running-it-yourself)

---

## 1. The Benchmark

`RuleEnginePerformanceTest` builds a rule project at runtime and evaluates it repeatedly. The shape is
meant to look like a real integration rather than a micro-benchmark:

| Property | Value |
|---|---|
| Rules | 20, spread over four rule files |
| Fields | 30 top-level fields — 10 `text` (each with three normalizers), 6 `decimal`, 6 `integer`, 3 `boolean`, 2 `date`, 2 `string_set`, 1 `collection` |
| Collection size | 12 records, iterated by every aggregate rule |
| Rules that match | 16 of 20 |
| Evaluations | 100 measured, after 2 000 warmup runs |

The rule set is a mix on purpose: plain comparisons, `and` / `or` groups, `between` ranges, set
membership, date comparisons, `sum` / `max` / `avg` / `count` aggregates, a `count` over a **filtered**
path, and two rules that read `set` variables published by an earlier rule.

Four rules deliberately do **not** match. A rule set where everything matches measures only the
cheapest path through the evaluator, because a condition that fails early never evaluates the rest of
its operands.

---

## 2. Measured Numbers

Measured on an Apple M5 Max (18 cores), macOS 26.6, BellSoft Liberica JDK 25, single-threaded.

**These figures are indicative, not a guarantee.** They exist to give you an order of magnitude. Your
hardware, rule count, record width and collection sizes all move them.

### Build phase — once at startup, or per rule reload

`RuleEngineBuilder.fromManifestEntry` reads, parses, validates and compiles all 20 rules.

| | Time |
|---|---|
| First build in a fresh JVM | **~230 ms** |
| Any later build in the same JVM | **~6 ms** |

The gap is not the rule compiler getting faster. The first build also pays to class-load and
JIT-compile the lexer, parser, validator and compiler themselves. ~230 ms is what your application
startup actually costs; ~6 ms is what a hot reload costs in a warm process.

### Evaluate phase — once per record

| | median | p95 | throughput |
|---|---|---|---|
| Full path (`LoadedRuleEngine.evaluate`) | **~12 µs** | ~25 µs | **~75 000 records/s** |
| Rules only (`PreparedRuleContext` reused) | **~2 µs** | ~3 µs | **~350 000 records/s** |

Steady-state figures, measured after 2 000 warmup runs. Without that warmup the same code reports a
median three to eight times higher — worth remembering if you benchmark this yourself, or if the
engine looks slow in the first moments after startup.

---

## 3. Where the Time Goes

The two evaluate rows above are the most useful result on this page: **roughly 80 % of the per-record
cost is preparing the record, not running the rules over it.**

`LoadedRuleEngine.evaluate` does two things. First it prepares the record — normalising every `text`
field (trim, lowercase, umlaut folding), coercing numbers to `BigDecimal`/`Long`, parsing dates
against their declared format, normalising string sets. Then it runs the 20 rules over the result.

Preparation is proportional to the **schema width** (30 fields here, each normalised once). Rule
evaluation is proportional to the **rule count and the collection sizes** the aggregates walk. On this
benchmark, preparing 30 fields costs about five times what running 20 rules over them costs.

The practical consequence: if evaluation is your bottleneck, look at how many fields your schema
declares and how many normalizers they carry before you look at how many rules you have. Declaring a
field your rules never read is not free — it is normalised on every record regardless.

---

## 4. Tuning

**Build once, evaluate many.** The build is a one-time cost — never rebuild per request. Build the
engine at startup and keep it; `LoadedRuleEngine` is immutable and safe to share. For rule updates
without a restart, use the `AtomicReference` hot-reload pattern in
[Integration Guide § 7](./integration-guide.md#7-thread-safety-and-lifecycle), which at ~6 ms per
reload is cheap enough to run on a config change.

**Reuse a `PreparedRuleContext` when you evaluate the same record more than once.** This is the 5x in
the table above. It only applies to re-running the *same* record — a context holds one record's
prepared values — so it helps with what-if analysis or rule debugging, not with a stream of distinct
records. Each record still needs its own context, and contexts must not be shared between threads.

**Keep `includeTrace = false` on the hot path.** Tracing swaps the no-op collector for a recording one
that allocates a node per condition. Turn it on for explaining a decision, not for producing it. See
[Tracing](./integration-guide.md#5-tracing--decision-tree-output).

**Aggregates are cached per evaluation.** Writing `sum(items.amount)` in five rules computes it once.
Filtered paths such as `items[category == "electronics"]` iterate the collection, so they scale with
its size — see [Value Expressions § 10](./expressions.md#10-performance-notes).

**Normalizers run once per record, not once per rule** — at prepare time. Declaring three normalizers
on a field costs the same whether one rule reads it or twenty. See
[Field Schema](./field-schema.md).

**`stop` ends the run, and everything after it costs nothing.** A rule whose branch ends in `stop`
suppresses every rule declared after it for that record — not as an optimisation the engine guesses at,
but because the author said so. Put the cheap, decisive guards first (a sanctioned country, a missing
mandatory field) and the expensive rules behind them, and the expensive ones never run on the records
that were already settled. See [Rules § The `else` Branch](./rules.md#the-else-branch).

---

## 5. Thread Safety

Build once, share the engine, give each thread its own input. Concurrent evaluation needs no locking.

| Object | Shareable across threads? |
|---|---|
| `RuleEngineBuilder` | ✅ stateless `object` — concurrent builds are safe |
| `RuleEngine`, `LoadedRuleEngine` | ✅ immutable after construction |
| `FieldSchema`, `ActionSchema`, `List<CompiledRule>` | ✅ immutable |
| `RuleContext`, `PreparedRuleContext` | ❌ per evaluation |

**`set` variables are safe under concurrency.** They look like shared state, but they are not: every
`LoadedRuleEngine.evaluate` call builds its own `PreparedRuleContext`, and with it its own variable
map. Two threads evaluating the same engine with different records cannot see each other's variables.
`RuleEngineConcurrencyTest` pins this down — eight threads, distinct records, distinct variable values,
asserted on every iteration.

The one unsafe pattern is hoisting a `PreparedRuleContext` and sharing *that* between threads. Its
variable map and aggregate cache are plain maps, and every evaluation writes to both. Keep contexts
per-call, which is what `LoadedRuleEngine.evaluate` does for you.

---

## 6. Running It Yourself

```bash
./gradlew :ruleengine-core:test --tests '*RuleEnginePerformanceTest*' -i --rerun-tasks
```

The test prints the full report — both build figures, then min, median, p95, mean and throughput for
both evaluate paths. The `-i` flag is what surfaces it; `--rerun-tasks` stops Gradle from skipping an
up-to-date test.

The benchmark project is generated at runtime by `BenchmarkProject`, not checked in, so the constants
at the top of that file (`RULE_COUNT`, `ITEM_COUNT`, field lists) are the knobs. Change them to
measure a shape closer to your own.

The timing assertions in the test are deliberately loose — they catch an order-of-magnitude
regression, not a few percent. A wall-clock assertion tight enough to detect small changes would fail
on a busy CI runner for reasons that have nothing to do with the engine. What the test asserts
strictly is that all 100 evaluations produce the same correct result.
