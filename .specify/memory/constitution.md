# Shelfly Constitution

## Core Principles

### I. On-Device & Private by Default

- User data (items, dates, notes, photos, prompts) MUST stay on the device. No backend, no
  analytics, no telemetry, and no cloud LLM calls for user content.
- The only permitted network use is fetching optional model assets (e.g. LiteRT model files)
  and platform-managed downloads (e.g. AICore). These MUST be user-initiated or clearly
  disclosed, and the app MUST stay fully usable while they are absent.
- Images captured for label scanning MUST be processed in memory or app-private storage and
  not persisted beyond the operation that needs them.

Rationale: Shelfly tracks medicines and household goods; privacy is the product, and the
"works standalone" story depends on it.

### II. AppFunctions Are the Single Contract

- Every data operation an agent can perform MUST be exposed through `ShelflyAppFunctions`.
  External agents (Gemini) and the in-app agent MUST call the same functions.
- The in-app agent MUST invoke `ShelflyAppFunctions` directly via Hilt, not through
  `AppFunctionManager` IPC.
- The agent tool catalog MUST be derived from the `@AppFunction` definitions; a parallel
  hand-maintained tool schema is not allowed.
- No MCP or other tool-protocol layer is added unless Shelfly starts consuming external tools.

Rationale: one source of truth keeps external and internal agents behaviourally identical and
keeps the AppFunctions showcase honest.

### III. Deterministic Logic Lives in Kotlin

- Models MUST be limited to extraction, transcription, and tool selection. Date arithmetic,
  duration resolution, unit conversion, and validation MUST be done in Kotlin.
- Model output MUST be parsed and validated before any write; malformed output MUST fail
  visibly (or go through an explicit repair path such as `repairJson`), never silently fall
  back to a guessed value.
- The agent is single-turn: one user request yields at most one tool call chosen by the
  model. Autonomous multi-step planning is out of scope.
- Destructive operations (delete, bulk changes) initiated by the agent MUST be confirmed by
  the user.

Rationale: measured evals showed every candidate model fails on fractional durations and
some date math, while Kotlin is exact, instant, and free.

### IV. Capability-Adaptive, Never Stranded

- `minSdk` is 33. Features that need newer platforms or optional components (AppFunctions on
  API 36+, AICore, downloaded models) MUST be gated at runtime, not by raising `minSdk`.
- The manual form path MUST always work on every supported device, with no model and no
  assistant installed.
- Backend readiness (AICore, model files) MUST be re-checked before each use; a previously
  "ready" state is not a latch. The UI MUST always offer a working recovery path
  (download, fallback backend, or manual entry).
- Backend preference order is AICore (Gemini Nano) → downloaded LiteRT Gemma E2B → external
  assistant via `ACTION_ASSIST` → manual form.

Rationale: device and model availability is fragmented and changes at runtime; users must
never end up on a dead screen.

### V. Measure Before Committing

- Changes to model backends, prompts, schemas, or sampler settings MUST be justified with the
  debug eval harness (`app/src/debug/.../spike/eval/`) on a real device, recording pass rate,
  argument accuracy, unparseable count, and latency.
- Risky or uncertain features MUST start as a spike on a feature branch with a pass threshold
  agreed up front; the main feature work starts only if the spike passes.
- Pure Kotlin logic with clear inputs and outputs (date/duration resolution, unit parsing,
  status computation) SHOULD have JVM unit tests when changed.
- A successful build is not verification. Fixes MUST be proven by a test, an eval run, or
  explicit manual verification steps that the maintainer confirms.

Rationale: on-device model behaviour is empirical; decisions made on intuition were reversed
by measurement more than once.

## Technology & Platform Constraints

- Language: Kotlin. UI: Jetpack Compose + Material 3 with dynamic color; status colors that
  carry meaning use dedicated tokens immune to dynamic theming.
- Architecture: ViewModel + StateFlow, unidirectional data flow; Room for persistence, Hilt
  for DI, WorkManager for background work, DataStore for settings.
- On-device AI: ML Kit GenAI Prompt API (AICore) and LiteRT GenAI. Dependency versions are
  pinned in `gradle/libs.versions.toml`; alpha/beta libraries MUST NOT be bumped without
  re-running the relevant eval.
- Debug-only tooling (eval harness, spikes) MUST live in the `debug` source set and never ship
  in release builds.
- User-facing strings MUST live in string resources.

## Development Workflow & Quality Gates

- Work is planned in small phases; each phase MUST leave the app building and functional, and
  is reviewed by the maintainer before the next phase starts.
- Changes are minimal and scoped to the task. Unrelated refactors are proposed separately.
- For bugs, the root cause MUST be identified and stated before a fix is made.
- No commit, push, merge, tag, or release happens without explicit maintainer instruction.
  Commit messages carry no co-author trailers.
- After code changes, run `graphify update .` to keep the knowledge graph current.

## Governance

- This constitution overrides conflicting guidance in specs, plans, and tasks. Feature plans
  MUST include a constitution check, and any deviation MUST be written down with its
  justification and the simpler alternative that was rejected.
- Amendments are proposed via `/speckit-constitution`, approved by the maintainer, and
  recorded with an updated version and amendment date.
- Versioning follows semantic versioning: MAJOR for removing or redefining a principle,
  MINOR for adding a principle or materially expanding guidance, PATCH for clarifications.
- Compliance is checked during `/speckit-plan` and `/speckit-analyze`, and when reviewing
  changes. Runtime guidance for agents lives in `CLAUDE.md`.

**Version**: 1.0.0 | **Ratified**: 2026-11-14 | **Last Amended**: 2026-11-14
