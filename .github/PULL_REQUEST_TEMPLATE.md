## What changes

One or two sentences. Say what a reader would notice, not which files you touched.

## Evidence

- [ ] `./gradlew :app:testModernDebugUnitTest` green (must be a real run, not a cached task)
- [ ] `./gradlew :app:compileModernDebugAndroidTestKotlin` green — the JVM suite does **not**
      compile the instrumented source set, so a deleted string key or a renamed enum entry
      only shows up here
- [ ] `./gradlew :app:lintModernRelease` with no new warnings (baseline: 43 warnings / 1 hint / 0 errors)
- [ ] `./gradlew :app:assembleModernDebug` green
- [ ] Architecture gates still green (`AGENTS.md` lists which invariant each one owns)

If a gate had to change, say which invariant moved and why in the description.
A green gate is not evidence that a feature works — for anything in the hook/injection
domain, attach the target-app read-back (device matrix result), not just the unit test.

## Scope discipline

- Version numbers in the description, not in code comments.
- No new resource key without all four locales (`values`, `values-zh`, `values-ja`,
  `values-ko`) — `values-ja`/`values-ko` are generated: edit `scripts/v42_table_*.mjs`
  and re-run `node scripts/v42_generate.mjs`, then confirm it printed `DONE`.
- No file under 5 MB, no build output, no log, no report, no device capture committed.
  Local material belongs in `dev-local/` (gitignored).
