# Job Application Tracker

A beginner-friendly Java desktop and console application built by a third-semester Informatik
student to practice OOP, JDBC, validation, and exception handling. Applications
are stored in PostgreSQL and remain available between program runs.

## Recruitment website upgrade

Applicants can browse and search published vacancies, upload a validated PDF CV, and track their application using a private code. The company dashboard manages vacancies and candidate statuses and displays/edits existing desktop tracker records. These records remain a separate category and are never published as vacancies.

The web backend now has HTTP routing, recruitment service, repository, authentication service, and transactional schema migrations. Named company accounts use salted PBKDF2 password hashes, expiring session cookies, logout, and login throttling. PDFBox validates uploaded PDFs; binary uploads avoid Base64 expansion. Listings use server-side search and 20-record pages.

Recommended Windows build: `./build.ps1`. Start with `java -cp 'out;lib/*' main.WebMain`. Configure the database environment variables and bootstrap ADMIN_PASSWORD as described in [the web guide](docs/WEB_GUIDE.md). `./build.ps1 -Test` runs HTTP/security/recruitment and desktop integration tests against a separate `_test` database. Maven supports compilation (`mvn package`); main-method integration tests are invoked by the PowerShell build rather than Maven Surefire.

PDFBox is an additional web dependency, automatically downloaded with SHA512 verification by the build script. JDK 17+ is required. The original desktop and console entry points remain available.

## Features

- Add applications with a company name, position, and location
- Swing desktop GUI with a sortable table, text search, and status filtering
- Use PostgreSQL-generated IDs and an initial `SAVED` status
- List applications, find by ID, change status, and delete by ID
- Edit company, position, and location while preserving the ID and status
- Support `SAVED`, `APPLIED`, `INTERVIEW`, `REJECTED`, and `OFFER`
- Validate required details and positive IDs
- Retry invalid numeric/status input and report missing applications
- Handle database failures without ending the console menu

## Technologies used

Core Java (JDK 17), Swing, OOP, PostgreSQL, plain JDBC, the PostgreSQL JDBC driver,
`Scanner`, enums, and checked exceptions. No Spring Boot, Hibernate, or JPA.

## Project structure

```text
src/
├── exception/ApplicationNotFoundException.java
├── model/ApplicationStatus.java
├── model/JobApplication.java
├── repository/JobApplicationRepository.java
├── service/JobApplicationService.java
├── util/DatabaseConnection.java
├── main/Main.java            # Console entry point
├── main/GuiMain.java         # Desktop entry point
└── main/TrackerFrame.java    # Swing window
sql/schema.sql
lib/                         # Downloaded driver JAR (ignored by Git)
docs/LEARNING_GUIDE.md        # Original in-memory version, historical reference
docs/JDBC_GUIDE.md            # Explanation of the current database version
```

`Main` and `TrackerFrame` handle interaction, the service handles business rules, and the repository
handles database operations. The connection utility reads environment variables.

## How to run

### 1. Create the database and table

Install PostgreSQL and ensure its server is running. Using an existing local
PostgreSQL account with permission to create databases, run:

```sh
createdb -U YOUR_POSTGRES_USER job_application_tracker
psql -U YOUR_POSTGRES_USER -d job_application_tracker -f sql/schema.sql
```

Replace `YOUR_POSTGRES_USER` with your local account. These commands may prompt
for its password. Alternatively, use pgAdmin to create the database and execute
`sql/schema.sql` in its query tool. The script creates the table if it is absent;
it does not migrate an existing table with a different structure.

### 2. Add the JDBC driver

Download the Java 8+ JDBC driver JAR from the official
[pgJDBC download page](https://jdbc.postgresql.org/download/) and place it in
`lib/`. The JAR is not committed to Git. The desktop requires this driver; the website additionally requires PDFBox. No build
tool is necessary. Java loads the driver automatically from the runtime classpath.

### 3. Configure the connection

In the same terminal that will run Java, set these environment variables.
For macOS/Linux (bash or zsh):

```sh
export DB_URL='jdbc:postgresql://localhost:5432/job_application_tracker'
export DB_USER='YOUR_POSTGRES_USER'
read -s -r 'DB_PASSWORD?Database password: '
export DB_PASSWORD
```

The password prompt shown above is for zsh. In bash use
`read -s -r -p 'Database password: ' DB_PASSWORD` instead.
The password is entered interactively rather than saved in source code or shell
history. All three variables must be nonblank. In an IDE, configure them in the
local run configuration. The application does not automatically load `.env` files.

For Windows PowerShell:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/job_application_tracker'
$env:DB_USER = 'YOUR_POSTGRES_USER'
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Database password' -AsSecureString)).Password
```

### 4. Compile and run

From the project root on macOS/Linux:

```sh
mkdir -p out
javac --release 17 -cp "lib/*" -d out src/exception/*.java src/model/*.java src/util/*.java src/repository/*.java src/service/*.java src/main/*.java
java -cp 'out:lib/*' main.Main
```

Compilation uses standard `java.sql` classes, so it does not need the driver JAR.
Running database operations requires the JAR on the classpath.

On Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force out | Out-Null
javac --release 17 -cp "lib/*" -d out src/exception/*.java src/model/*.java src/util/*.java src/repository/*.java src/service/*.java src/main/*.java
java -cp 'out;lib/*' main.Main
```

Choose menu options 1–7; option 6 exits and option 7 edits application details.
Editing requires all three replacement details; blank values cancel the update
with a validation message. The ID and status stay unchanged.
Recompile after source changes. Use JDK 17 or newer for both compilation and running;
if Windows selects Java 8, use the full paths to your JDK's javac.exe and java.exe.
If a database operation fails, check the server, credentials, driver, and schema.
No credentials or raw database messages are printed by the console.

## Current limitations

- Desktop GUI requires a graphical desktop session
- Each operation opens its own connection; there is no connection pool
- Database setup and JDBC driver installation are manual
- Website schema migrations are versioned; the original desktop table can also be created manually
- Generated IDs can have gaps and are not reused after deletion

## Planned next steps

- Extend automated integration coverage to all repository operations
- Add server-side filtering for larger databases

See [the JDBC guide](docs/JDBC_GUIDE.md) for an explanation of every major change.

## Desktop GUI

After configuring the database environment variables and compiling as above, run:

```powershell
java -cp 'out;lib/*' main.GuiMain
```

On this Windows installation, if `java` selects Java 8, use:

```powershell
& 'C:\Program Files\Java\jdk-27\bin\java.exe' -cp 'out;lib/*' main.GuiMain
```

Fill in company, position, and location, then click **Add application**. New rows
start as SAVED. Select a row to edit details, set its status, or delete it with
confirmation. **New / Clear** prepares an empty form. **Save details** and **Set
status** are separate operations. Search matches any displayed column; combine
it with the status dropdown. Click column headings to sort and **Refresh** to
reload PostgreSQL data. Filters apply to the loaded rows.

Database calls run on background workers, with controls disabled until completion.
Errors appear in the bottom message line and leave the window usable. Credentials
are read from environment variables and are never saved by the GUI.

To run the GUI integration checks against an `_test` database (a temporary window
will open), compile the tests and run:

```powershell
javac --release 17 -cp "out;lib/*" -d test-out tests/GuiIntegrationTest.java
java -cp 'out;test-out;lib/*' main.GuiIntegrationTest
```

The GUI test adds, edits, filters, changes status, validates fields, checks delete
confirmation, and removes its temporary record. The existing console remains
available through `main.Main`.

## Test editing against PostgreSQL

Create a separate database whose name ends in `_test`, apply `sql/schema.sql`,
and configure the three database environment variables as above. With JDK 17+
selected in PowerShell, compile the application, then run:

```powershell
New-Item -ItemType Directory -Force test-out | Out-Null
javac --release 17 -cp "out;lib/*" -d test-out tests/EditDetailsIntegrationTest.java
java -cp 'out;test-out;lib/*' EditDetailsIntegrationTest
```

The test creates and removes its own application. It checks editing, trimming,
apostrophes, invalid fields, missing IDs, unchanged ID/status, and persistence
across console runs. It refuses a database name that does not end in `_test`.


