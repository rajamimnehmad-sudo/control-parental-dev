# Coding Guidelines

## Principles

- Follow Clean Architecture: domain code must not depend on Android, Room, Supabase, Hilt, Compose, or WorkManager.
- Keep modules independent. Communicate through interfaces defined in `core-domain` or the owning core module.
- Prefer small files, small functions, immutable models, constructor injection, and explicit dependencies.
- Do not use private Android APIs, reflection-based hacks, hidden permissions, or undocumented behavior.
- Keep files near 300-400 lines at most. Refactor earlier when a class has more than one responsibility.

## Packages

- Use `com.contentfilter.<module>` as the package root.
- Domain models live under `com.contentfilter.core.domain.model`.
- Domain repository contracts live under `com.contentfilter.core.domain.repository`.
- Use cases live under `com.contentfilter.core.domain.usecase`.
- Feature UI lives under `com.contentfilter.feature.<feature>`.
- App entry points live only in `app-user` or `app-admin`.

## Naming

- Domain models: business names without technical suffix when possible, for example `PolicySnapshot`.
- DTOs: suffix with `Dto`, for example `PolicyDto`.
- Room entities: suffix with `Entity`, for example `PolicyEntity`.
- DAOs: suffix with `Dao`, for example `PolicyDao`.
- Repositories: interfaces use business names, implementations include technology, for example `PolicyRepository` and `RoomPolicyRepository`.
- Use cases: suffix with `UseCase`, for example `ObserveSystemHealthUseCase`.
- ViewModels: suffix with `ViewModel`.
- UI state: suffix with `UiState`.
- Mappers: keep as internal top-level functions in files ending with `Mappers`.

## Domain

- Domain code must be pure Kotlin.
- Domain models should be immutable `data class`, `enum class`, or `sealed interface`.
- Domain must define contracts, not implementation details.
- The `PolicyEngine` is the only business decision point for policy outcomes.

## Data And Persistence

- Room belongs only in `core-database`.
- Repository implementations belong in `core-data`.
- Database entities must not leak to UI or domain callers.
- Use explicit mappers between Entity, DTO, and domain models.
- Schema changes require migrations and exported Room schemas.

## Network

- `core-network` contains only contracts and network primitives until the Supabase phase.
- Supabase implementation is introduced in the approved sync phase only.
- Do not store service role keys or privileged secrets in clients.

## UI

- UI uses Jetpack Compose and StateFlow.
- ViewModels expose immutable UI state.
- UI must not contain business decisions.
- Shared components live in `core-ui`; feature-specific screens stay in feature modules.

## Dependency Injection

- Use Hilt constructor injection.
- Avoid singletons unless state sharing or lifecycle requires it.
- Bind interfaces in small Hilt modules located with the implementation owner.

## Coroutines And Flow

- Use structured concurrency.
- Do not use `GlobalScope`.
- Expose `Flow` or `StateFlow` for observable state.
- Keep blocking work off the main thread.

## Tests

- Unit-test domain and policy logic first.
- Add repository tests when persistence logic becomes non-trivial.
- Add UI tests only for stable, valuable user flows.

## Quality Gates

- All dependencies must be declared in `gradle/libs.versions.toml`.
- Run build, tests, lint, ktlint, and detekt before merging.
- No duplicate business logic across features.
- No broad permissions without architectural justification.

## Build Variants

- Apps define the `distribution` flavor dimension.
- Supported flavors are `dev`, `beta`, and `prod`.
- `dev` and `beta` may use application id and version suffixes.
- The `prod` flavor preserves production package identity.
- Production release artifacts use the `prodRelease` variant.
- Android Gradle Plugin does not allow a flavor named `release` because it collides with the default `release` build type.
- Do not add behavior differences to flavors unless the architecture explicitly requires them.
