# Static Analysis Instructions

Use this file when fixing Detekt findings, changing style-sensitive code, or adding suppressions.

## Detekt

- Detekt is configured in `config/detekt.yml`.
- All code must pass Detekt checks.
- Prefer fixing the underlying issue instead of suppressing it.
- Use `@Suppress` only for individual, justified suppressions.
- Suppressions must name the specific rule, for example `@Suppress("TooManyFunctions")`.
- Do not suppress whole files.
- Add a short reason near the suppression when the justification is not obvious.
