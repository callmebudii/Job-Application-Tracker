# Recruitment website

## Build and run

Use JDK 17+ (JAVA_HOME may select it). From the project root:

```powershell
./build.ps1
$env:DB_URL = 'jdbc:postgresql://localhost:5432/job_application_tracker'
$env:DB_USER = 'YOUR_POSTGRES_USER'
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Database password' -AsSecureString)).Password
$env:ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Initial admin password (12+ characters)' -AsSecureString)).Password
& 'C:/Program Files/Java/jdk-27/bin/java.exe' -cp 'out;lib/*' main.WebMain
```

The build script downloads PostgreSQL JDBC and PDFBox when absent; it checks PDFBox's Apache SHA512 checksum and works offline once the libraries/checksum are present. Maven is optional: `mvn package` compiles the same sources; integration test main classes are run by `build.ps1 -Test` rather than Surefire.

The server applies transactional, versioned migrations at startup. It preserves the existing tables and records and serializes concurrent migrations. `schema_migrations` records completed versions. New versions belong in `sql/migrations/` and must be registered in `SchemaMigrator`; never edit an already-applied migration for an existing deployment.

## Applicants

Open http://localhost:8080/. Search published jobs, view their details and apply with a name, email and PDF CV (maximum 5 MB, unencrypted, 1–100 pages). The application returns an ID and a private tracking code. Save that code; enter it in the tracking form to see the current status. Tracking never returns the applicant email, CV or other applicants. Codes are bearer secrets: anyone holding a code can see that application's limited status. Existing applications created before this upgrade do not have tracking codes.

CVs upload directly as binary PDF bytes with percent-encoded metadata headers. The server parses the document with PDFBox and rejects damaged PDFs, encryption, excessive complexity, executable actions and embedded attachments. This is structural validation, not an antivirus service; deployment with real candidate documents should add a dedicated malware scanning service.

## Company accounts

Open http://localhost:8080/admin and use the login form. The initial username is `admin`. ADMIN_PASSWORD bootstraps that account only if it does not exist; changing that environment variable later does not reset the stored password. Passwords are salted PBKDF2 hashes in `admin_accounts`. No Basic-auth fallback remains.

To create a separate named staff account or reset an existing account's password, run in a terminal:

```powershell
& 'C:/Program Files/Java/jdk-27/bin/java.exe' -cp 'out;lib/*' main.AdminMain recruiter
```

The password is entered through a hidden prompt. Restart the server after a password reset to revoke existing sessions. Staff accounts currently share the same company-wide permissions; this is not a multi-company tenancy system.

Sessions expire after eight hours, are revoked on logout and are lost after server restart. Cookies are HttpOnly and SameSite=Strict; HTTPS deployments also set Secure. Five failed logins from one connected IP within 15 minutes block further attempts in that window. Reverse proxies share their upstream address; configure edge-level rate limiting for per-client protection rather than trusting arbitrary forwarded headers.

The dashboard publishes, edits and closes/reopens jobs, reviews applicants, downloads CVs and changes statuses. Unchecking "open for applications" works on both new and existing jobs. The same dashboard also displays and edits the original desktop tracker records using the existing service/repository; changes are visible after refreshing the desktop app. Desktop records are not advertised as vacancies. Listings have 20-record pages, and vacancy search runs in PostgreSQL.

## Tests

Create a separate database ending in `_test`, configure DB credentials and the matching initial/admin password, choose an unused PORT (e.g. 8081), then run `./build.ps1 -Test`. The HTTP test uses actual valid PDFs and checks malformed/active/oversized file rejection, cookie login/logout, throttling, duplicate protection, private CV access, tracking, visibility, pagination, desktop synchronization and repeat migrations. It removes only its generated records. Existing GUI integration tests remain available separately through `main.GuiIntegrationTest`.

## Deployment

The default binding is localhost, with PORT=8080. To deploy, run behind an HTTPS reverse proxy, set PUBLIC_ORIGIN to the exact external origin (e.g. `https://jobs.example.com`), and set BIND_ADDRESS for the hosting network only when needed. Public HTTP origins are rejected. POST requests must have the configured Origin. Keep the Java port inaccessible directly from the public internet. Configure proxy request limits (5 MB), read/upload timeouts, edge rate limiting and TLS there. No public hosting is provisioned by this project.

Use a least-privilege PostgreSQL runtime account after migrations, encrypted backups and a tested restore procedure. The current migration startup needs DDL permission. All records and files stay in PostgreSQL; review backup size as volume grows. Sessions are in memory, so use one server instance or implement a shared session store before scaling horizontally. A connection pool, dedicated file storage, email verification/notifications, account recovery, a managed malware scanner and production load testing remain separate operational enhancements. Database logs report SQLState only and do not log CVs, credentials or candidate fields.
