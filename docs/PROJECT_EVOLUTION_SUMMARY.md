# Project Evolution Summary

This document records the major architectural decisions and refactoring milestones in SimplyDone's history.

---

## Standardized API Response Envelope

All endpoints were refactored to return a uniform `ApiResponse<T>` wrapper. This ensures every client — web UI, CLI, or external integration — receives data in the same predictable shape regardless of the endpoint. Fields include `status`, `success`, `message`, `data`, `errorCode`, `validationErrors`, `path`, and `timestamp`.

---

## Centralized Exception Handling

Replaced fragmented `try/catch` blocks across controllers with a single `GlobalExceptionHandler`. This eliminated duplicated error-formatting logic and guarantees that even uncaught runtime exceptions produce a well-formed JSON error response rather than a Spring default error page.

---

## Configuration Consolidation

Consolidated environment-specific configuration into a single `application.properties` file with environment variable overrides. All secrets are read from environment variables with safe defaults for local development. The `entrypoint.sh` script normalizes Render's `DATABASE_URL` format into the JDBC properties that Spring expects.

---

## Rate Limiting Rewrite

The original rate limiting used a Redis `MULTI/EXEC` block with a `GET + INCR + EXPIRE` transaction. This was replaced with a simple atomic `INCR` followed by a conditional `EXPIRE` (set only on the first request in each window). The new implementation is simpler, avoids a potential type mismatch in the transaction result, and sets TTL only once per window rather than resetting it on every request.

---

## FILE_OPERATION Bug Fix

The `CREATE_DIRECTORY` operation in `FileOperationJobStrategy` passed `targetPath` (which is null when only `source` is provided) to the `createDirectory()` method, causing a NullPointerException wrapped as "File operation failed: null". The fix falls back to `sourcePath` when `targetPath` is absent.

---

## Docker Compose DATABASE_URL Fix

The `docker-compose.yml` had `DATABASE_URL=jdbc:postgresql://...` which is the JDBC format. The `entrypoint.sh` script only parses URLs with the `postgresql://` or `postgres://` scheme (Render format). With the JDBC prefix the script skipped parsing and the application fell back to the H2 in-memory database. The URL was corrected to `postgresql://postgres:postgres@db:5432/simplydone`.

---

## DatabaseDebugConfig Security Fix

The original `DatabaseDebugConfig` used `System.out.println` to write the datasource URL and username to standard output on every startup. This was visible in any log aggregation system and constitutes an information exposure. The component was rewritten to use SLF4J at `DEBUG` level (suppressed by the default `INFO` log level in production).

---

## Test Coverage Expansion

The test suite was expanded from 80 to 103 tests to cover the previously untested rate limiting service and email strategy. Specific additions:
- `RateLimitingServiceTest` (10 tests): fixed-window counter behavior, TTL lifecycle, fail-open on Redis unavailability, per-user isolation.
- `EmailJobStrategyTest` (12 tests): parameter validation, successful send, custom credentials, failure handling.
- `FileOperationJobStrategyTest` regression test: `CREATE_DIRECTORY` with only `source` provided.

---

## Planned Improvements

The following items are tracked for future releases:

- Native PDF generation for `REPORT_GENERATION` jobs (requires Apache PDFBox or iText dependency).
- Native Excel generation for `REPORT_GENERATION` jobs (requires Apache POI dependency).
- Per-endpoint API versioning strategy as the API surface grows.
- OAuth2 authentication for the admin endpoints.

