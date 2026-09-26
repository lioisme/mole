# Contributing

Thanks for reading this before sending a patch. This repository is small in
feature count but strict in invariants: most architectural rules are enforced by
**tests that scan source text**, and they fail with messages that do not explain
themselves. This page is the human-facing summary; the machine-facing one is
[`AGENTS.md`](AGENTS.md).

## First, get oriented

| What you want to know | Where it lives |
|---|---|
| What the product does | [README.md](README.md) |
| Security surfaces, defaults, known physical limits | [SECURITY.md](SECURITY.md) |
| Full capability list (88 categories, listen 43 / feature 45) | [docs/capability-inventory.md](docs/capability-inventory.md) |
| Why two module entry points and two flavors | [docs/DUAL_FRAMEWORK_SUPPORT.md](docs/DUAL_FRAMEWORK_SUPPORT.md), [docs/xposed-api-compat.md](docs/xposed-api-compat.md) |
| Record layer design | [docs/record-layer-redesign.md](docs/record-layer-redesign.md) |
| "It looks green but isn't doing anything" — toolchain traps | [docs/pitfalls/toolchain.md](docs/pitfalls/toolchain.md) |
| How a decision was reached years ago | `git log`, and [docs/history/](docs/history/) for the original briefs |
| Rules an agent must not violate | [`AGENTS.md`](AGENTS.md) |

## Environment

- JDK 17+ (21 works), Android SDK platform 36, build-tools ≥ 34.
- Windows is the primary dev platform; `gradlew` (bash) works elsewhere.
- Local, non-committed material goes in `dev-local/` (gitignored): signing keys,
  build outputs, logs, acceptance reports, device captures, scratch notes.
  Nothing else in this repository may hold those.
- Signing is optional by design: without
  `dev-local/secrets/keystore/keystore.properties` the build produces unsigned
  APKs instead of failing. `scripts/build-all.ps1` generates the keystore on
  first run.

## The two commands you must run before pushing

```bash
./gradlew :app:testModernDebugUnitTest          # unit + Robolectric, includes all architecture gates
./gradlew :app:lintModernRelease                # baseline: 40 warnings / 1 hint / 0 errors; adding one is a no
```

Also compile the instrumented source set, because the JVM run above does **not**
see it, and this has already been the way a breakage shipped once:

```bash
./gradlew :app:compileModernDebugAndroidTestKotlin
```

Device-side tests (`:app:connectedModernDebugAndroidTest`, and the
`scripts/test-integration/` matrices) need a rooted emulator with Magisk + an
Xposed framework + the `testtarget` app. They are the only evidence that
injection actually takes effect — see "Green is not enough" below.

## Architecture gates are the contract

`app/src/test/java/com/lioisme/modev/architecture/` holds ~17 tests that read
source *text* rather than behaviour. They are not cleanup candidates. Each owns
one invariant named in `AGENTS.md`:

| Gate | Invariant it holds |
|---|---|
| `PackageBoundaryTest` | no `ui → data/hook`, no `hook → ui`, no `mcp → data.db`, FQN-text level (an inline `com.lioisme.modev.x.Y` reference counts) |
| `LayerDependencyGateTest` | no resurrection of deleted compat `typealias`es; `AppContainer` public types come from `domain.*` |
| `SingleWriteEntryPointTest` | a prefs file is opened only by its owner; port writes only through `PortWriteGuard` |
| `DbMigrationParityTest` | Room version ↔ `MIGRATION_x_y` chain ↔ `addMigrations` list agree |
| `RecordDaoAccessGateTest` | only the repository may call `cryptoRecordDao()`; queries route through `RecordQueryFactory` |
| `CapabilityMapTest` | the front-end/back-end category projection matches taxonomy, `R.string`, routes and probe files |
| `FeatureOrphanGateTest` | every registered kind and operation is actually emitted by the injection side |
| `AppListenOverridePersistenceTest` | every model property written by `toJson` has a matching `fromJson` parser arm |
| `StringResourceParityTest` / `DomainVocabularyTest` | four locales complete, placeholders consistent, banned wordings absent |
| `McpToolParityTest` / `UiMcpCapabilityParityTest` | the 50-tool MCP surface matches UI capability line by line; one mutable upstream is advertised by exactly one tool |
| `DialogConventionTest` / `ModevValueDialogTest` | `AlertDialog(` and `ModalBottomSheet(` exist only in the shared components |
| `RecordRowContainerContractTest` | rounded rows clip/背景/ripple at the container layer, never inside `ListItem` |
| `CommentDriftGateTest` | comments may only name things that still exist; no TODO markers; version bookkeeping never grows |
| `HardcodedStringConventionTest` | no Chinese literals in `ui` sources (user-visible text goes through resources) |
| `FrameworkEntrypointContractTest` | both Xposed API skeletons and their metadata stay consistent |

If your change legitimately moves an invariant, update the gate **and** the
corresponding `AGENTS.md` item in the same commit. A gate that was merely
annoying gets deleted in review, and the hole it closed comes back.

## Adding or removing a capability

This is the highest-risk edit in the repo. A category is registered in several
places and they must agree in one commit:

`CategoryTaxonomy` entry (with its `domain`) → the kind enum → the operation
enum → the hooking code that emits it → `HookSwitch` bit → both arms of
`EffectiveSwitch.readKind/applyKinds` → icon / color token / four-locale label →
`FeatureGroups` membership → the frozen count snapshots → the MCP enum.

Half-doing it produces the most expensive bug class this project has:
**a toggle that exists, can be turned on, and does nothing forever** — while
every unit test stays green. `FeatureOrphanGateTest` catches part of it; the
remaining proof is a target-app read-back.

Removing one means the same list in reverse. Before deleting a string key or
enum name, grep the *key name* (not its Chinese value) across `app/src` and
`scripts` — that miss is how the androidTest source set stopped compiling.

## User-visible text (i18n)

Four locales: `values/` (en), `values-zh/`, `values-ja/`, `values-ko/`.

**`values-ja` and `values-ko` are generated files.** Add a key by editing the
translation table, then regenerate:

```bash
# edit scripts/v42_table_*.mjs to add the new key for ja/ko
node scripts/v42_generate.mjs        # must print DONE
```

Writing ja/ko by hand has already silently broken the pipeline once (7 keys
drifted for weeks). Plurals: en uses `one`/`other`, zh uses `other` only, and
ja/ko use `other` per CLDRV. Counts are formatted through `AppCountFormat`,
never with hand-rolled `万+`/`K+` suffixes.

## Tests

New behaviour needs a test; the tier is decided by what it touches:

- pure function → plain JUnit under `app/src/test`
- framework dependency (Room, Locale, resources, `android.icu`) → Robolectric
- composition, navigation, gestures → `app/src/androidTest`
- anything whose correctness depends on ART rather than the JVM → device matrix

Instrumentation seeds have two hard rules: the read-back filter must match the
cleanup scope (`clearAll()` → global count; `clearPackage(pkg)` → filter by that
package), and any cross-screen navigation must scroll the target into view and
then wait for an anchor on the next screen — a `performClick()` on an
off-viewport node is dropped silently.

## Commit and review shape

- One commit per concern. A refactor that changes behaviour belongs in two.
- Commit messages explain **why**, and name the evidence.
- Version markers (`V47`, `WP-D1`, `#31②`) do not belong in code comments — that
  bookkeeping is what `CommentDriftGateTest` ratchets down, and it goes stale one
  batch after it is written. Put provenance in the commit message or in
  `docs/adr/`.
- No new lint warnings, no new dependencies without a reason in the message,
  no file over 5 MB, no generated output committed.

## Green is not enough

`AGENTS.md` states it as a rule and it has bitten repeatedly: a passing gate
suite is not evidence that a hook fires. For the injection domain, the acceptance
test is the target app reading back the spoofed value, i.e.
`scripts/test-integration/run-acceptance.ps1`. When you report work as done, say
which of the two you actually ran.
