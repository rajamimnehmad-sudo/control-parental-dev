# Coding Guidelines

## Principles

- Follow Clean Architecture where it adds real separation: domain code must not depend on Android UI/framework implementation details, Room, Supabase clients, Hilt, Compose or WorkManager.
- Keep modules cohesive and dependencies explicit. Communicate through stable interfaces owned by the appropriate domain/core/feature boundary.
- Prefer small functions, immutable models, constructor injection and explicit dependencies.
- Do not use private Android APIs, reflection-based hacks, hidden permissions or undocumented behavior unless a separately approved architecture explicitly justifies the risk.
- File size is a signal, not a quota. Around 500-600 lines is a review point: if a file is growing past that range, evaluate whether it contains multiple responsibilities and split only when cohesion improves. Refactor earlier whenever responsibilities are already mixed; do not fragment code artificially just to satisfy a line count.
- When adding functionality to an already large file, first consider whether the new responsibility belongs in a separate cohesive component.

## Packages

- Use `com.contentfilter.<module>` as the package root.
- Domain models live under `com.contentfilter.core.domain.model` when they are truly shared domain concepts.
- Domain repository contracts live under `com.contentfilter.core.domain.repository` or the owning core module.
- Use cases live under `com.contentfilter.core.domain.usecase` or the owning feature/domain boundary.
- Feature UI stays with its feature; app entry points stay in the app modules.

## Naming

- Domain models: business names without technical suffix when possible, for example `PolicySnapshot`.
- DTOs: suffix with `Dto` when the distinction is useful.
- Room entities: suffix with `Entity`.
- DAOs: suffix with `Dao`.
- Repository interfaces use business names; implementations may name the backing technology when useful.
- ViewModels use `ViewModel`; UI state uses `UiState`.
- Prefer names that communicate responsibility over rigid naming bureaucracy.

## Domain And Data

- Domain code should remain pure Kotlin when feasible.
- Domain models should be immutable unless mutability is a deliberate lifecycle/performance requirement.
- Room persistence belongs in the database/data layers and entities must not leak into UI/domain contracts.
- Schema changes require the appropriate migration/schema evidence.
- Supabase/provider implementations belong behind explicit boundaries; never store service-role or privileged secrets in clients.

## UI

- Compose/StateFlow are the default UI stack for the current apps.
- ViewModels expose stable/immutable UI state where practical.
- Business/security decisions should not live in composables.
- Promote components to `core-ui` only when genuinely shared; avoid premature abstraction.

## Dependency Injection

- Prefer Hilt constructor injection for Android-owned components.
- Avoid singletons unless lifecycle/state sharing requires them.
- Keep DI modules close to the implementation owner and small enough to understand.

## Coroutines And Flow

- Use structured concurrency.
- Do not use `GlobalScope`.
- Keep blocking work off the main thread.
- Use `Flow`/`StateFlow` when observation is actually required; do not introduce reactive layers for static/simple values.

## Tests

- Test the risk and behavior changed by the diff first.
- Unit-test domain/policy logic where deterministic tests give value.
- Add persistence/network/UI tests when those layers contain meaningful behavior worth protecting.
- Reuse existing fixtures/evidence when the new diff does not invalidate them.
- Do not add low-value tests only to increase count or coverage percentage.

## Quality Gates

- Declare dependencies consistently in the version catalog when that is the repository convention; exceptions require a concrete reason.
- Gates are proportional to the change and closure stage. Run targeted tests/compile first; add lint/ktlint/detekt/full builds/CI when they are relevant to the affected scope or required before integration/release.
- A preexisting failure outside the diff is reported and isolated; it does not automatically block a clean unrelated change.
- Before a real merge/release, the checks required by that integration/release gate must pass or have an explicitly accepted residual.
- No duplicate business/security logic across features without justification.
- No broad permissions without architectural justification.

## Build Variants

- Apps use the `distribution` flavor dimension where currently configured.
- `dev`, `beta` and `prod` preserve their intended identities; Production release artifacts use the production variant.
- Do not add flavor-specific behavior unless the architecture/product flow explicitly requires it.
