# Moving from ArrayList to PostgreSQL with JDBC

## Data flow

`Main → JobApplicationService → JobApplicationRepository → DatabaseConnection → PostgreSQL`

Main collects input and prints results. The service validates business inputs.
The repository executes SQL. The utility supplies a connection. This keeps each
class focused without introducing frameworks or extra interfaces.

## New: util/DatabaseConnection.java

This utility reads `DB_URL`, `DB_USER`, and `DB_PASSWORD` and calls
`DriverManager.getConnection`. Missing configuration raises `SQLException`.
Its private constructor prevents unnecessary utility objects. Credentials have
no hardcoded defaults and are not logged. The caller closes each connection.
The PostgreSQL JDBC driver must be present on the runtime classpath.

## New: repository/JobApplicationRepository.java

- `save` accepts already-validated details and a status. It inserts without an ID;
  PostgreSQL's `RETURNING` clause returns the stored row including its generated ID.
  The method returns a fully populated `JobApplication`, so no temporary ID or
  mutable ID field is needed.
- `findAll` selects rows ordered by ID and returns a `List<JobApplication>`.
  A local ArrayList collects this query's results; it is not persistent storage.
- `findById` returns a matching row or `null`. Only the service translates a
  missing row into the application-specific exception.
- `updateStatus` and `deleteById` return whether one row was affected. They do
  not decide how the application should report a missing ID.
- `mapRow` reads named columns from `ResultSet` and constructs the existing model.
  It converts the status string back to an enum. An invalid stored status becomes
  an SQL error rather than a misleading console input error.

Every SQL operation uses `PreparedStatement`. Placeholders (`?`) bind input as
values rather than combining it with SQL text, including names with apostrophes.
Try-with-resources closes connections, statements, and result sets on both success
and failure. Each operation is a single statement using JDBC's default auto-commit;
there is no multi-step transaction to manage in this version.

The repository contains no prompts, default-status decisions, or business input
validation. It declares `SQLException` so the caller can handle database failures.

## New: sql/schema.sql

The table has an integer `GENERATED ALWAYS AS IDENTITY` primary key. PostgreSQL
owns ID generation, including across restarts. Text columns store the application
fields. NOT NULL and CHECK constraints protect required fields and the set of
statuses at the database boundary, including against manual SQL writes. Java's
service remains responsible for its own input validation.

Run this script once before using the application. `IF NOT EXISTS` allows repeated
setup but does not update an already-existing table's columns or constraints.

## Changed: service/JobApplicationService.java

The service receives a repository in its constructor instead of owning an
ArrayList and ID counter. Required-text validation, trimming, positive-ID checks,
and the initial `SAVED` status remain here. `getAllApplications` now returns a
`List` of database results. `findById` still throws `ApplicationNotFoundException`.

Update and delete use the repository's affected-row result instead of performing
a separate lookup first. This avoids a second database request and handles a row
removed before the operation executes. The service raises the same missing-ID
exception when no row is affected. A status update validates non-null status
before executing SQL. Methods now also declare `SQLException`; they do not hide
failed database operations or report them as successful.

## Changed: main/Main.java

Main constructs the repository and passes it to the service. Its view method uses
`List` instead of `ArrayList`. Interaction methods propagate `SQLException` to
`runMenu`, which prints a short setup/error message and continues the menu. Raw
SQL errors can contain connection or data details, so the UI does not print them.

The original six options, input parsing retries, validation messages, custom not-found
handling, and end-of-input exit remain. Main contains no SQL and never accesses
the connection utility directly.

## Supporting files

- `lib/.gitkeep` preserves the driver folder in Git without committing binary JARs.
- `.gitignore` now excludes local driver JARs and `.env` credential files.
- `README.md` documents PostgreSQL setup, the driver, environment variables,
  compilation, persistence, and current limitations.
- `docs/LEARNING_GUIDE.md` is labeled as the historical in-memory guide to retain
  earlier explanations without confusing them with the current implementation.

The model classes, status enum, and custom exception required no changes.

## Manual database verification

After setup, add an application with an apostrophe in the company name. View it,
find it by ID, change its status, then exit and restart to verify persistence.
Delete it and check that searching/updating/deleting that ID reports it missing.
Try invalid menu choices, IDs, blank details, and status names. Stop PostgreSQL
and attempt to view applications: the menu should report a database failure and
remain usable, including option 6.

Use a separate local test database for these checks. Live PostgreSQL integration
was verified for create, read, list, status update, persistence across Java
processes, and deletion.

## Editing application details

Menu option 7 shows the current application, then requests a replacement company,
position, and location. Option 6 still exits. All three values are required.
The service trims and validates every field before sending an update, so invalid
input cannot partially change an application. The repository uses one prepared
UPDATE statement and checks the affected-row count for missing IDs. The ID and
status are preserved; the model's immutable detail fields describe a snapshot,
and a subsequent query returns the updated details. No schema change is needed.
