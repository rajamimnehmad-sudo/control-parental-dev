# Database

## Scope

The local database is the offline-first source of truth for the protected device.

Room stores:

- active policies and rules;
- daily limits;
- usage sessions;
- access requests;
- sync cursors;
- outbox operations;
- system health snapshots;
- technical diagnostics.

## Migration Strategy

- Version 1 is the initial schema.
- Room schema export is enabled in `core-database`.
- Migrations are registered centrally in `DatabaseMigrations`.
- Destructive migration fallback is not allowed for production data.
- Every future schema change must include an explicit migration and a short reason in the migration file.

## Boundaries

- Entities stay inside `core-database`.
- DAOs contain SQL access only.
- Mappers stay inside `core-data`.
- Business decisions stay in `core-policy`.
- Domain contracts and models stay in `core-domain`.
