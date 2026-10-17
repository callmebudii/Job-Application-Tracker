# Upgrade verification

Verified against a separate local PostgreSQL `_test` database on 14 October 2026.

## Automated repeatable checks

Run `./build.ps1 -Test` with DB_URL, DB_USER, DB_PASSWORD and the test account's ADMIN_PASSWORD configured. Choose a free PORT. The HTTP integration test starts and stops its own server and cleans up its own records. Tests cover:

- Transactional migrations and safe repeated startup.
- Cookie login and company endpoint access protection, HttpOnly cookies, logout/session revocation, and five-failure throttling.
- Creating closed/open vacancies, closed-job application rejection and pagination.
- Binary upload and byte-identical authenticated CV downloads.
- Valid PDFs, fake/malformed PDFs, active JavaScript documents and oversized upload rejection.
- Duplicate application rejection, candidate status updates and private tracking without email/CV exposure.
- Viewing and editing desktop records through the web dashboard, with persistence checked through the original repository.
- Cross-origin request rejection.
- Original desktop edit validation, console flow, missing IDs and persistence tests.

These checks passed in staging and again after installation into the original project.

## Automated live browser checks

The installed website was exercised through the Codex browser automation tools:

1. Open applicant page, follow company link, log in using the actual login form.
2. Publish a clearly marked test vacancy from the company form.
3. Open applicant view and submit a generated, valid PDF using the actual file chooser.
4. Verify submission confirmation and generated private tracking code.
5. Track the application and verify its initial APPLIED state.
6. Open company view and change the synthetic candidate's status to INTERVIEW.
7. Log out and verify the login form reappears.
8. Track the synthetic application again while logged out and verify INTERVIEW is shown.

The browser flow passed twice, including a replay of the locators and assertions saved in `tests/BrowserWorkflow.cua.mjs`. This script takes a Codex browser Tab, credentials and a sample PDF path; it is run through `cua_repl`, separately from the command-line build. If the runtime disallows local module imports, paste/load its function into the REPL. The test vacancies and synthetic applications remain in the demo database so the user can inspect them. Existing user records were preserved. Desktop GUI tests remain available separately; they were not rerun in this upgrade session.

No public deployment, external email delivery, dedicated malware scanning service, production load test or disaster recovery test was performed.
