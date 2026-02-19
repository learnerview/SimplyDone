# Job Type Catalog

This document defines the supported job types within the SimplyDone ecosystem and their corresponding technical specifications.

## EMAIL_SEND
Facilitates the dispatch of transactional emails.

### Specifications
- **Provider**: Resend API integration.
- **Parameters**:
  - `to`: Recipient address string.
  - `subject`: Email subject line.
  - `body`: Content (supports HTML).

### Performance Note
Execution is asynchronous; the job is marked as `EXECUTED` once the handoff to the SMTP provider is successful.

## DATA_PROCESS
Performs high-volume operations on CSV data sets.

### Supported Operations
- **TRANSFORM**: Column-level normalization (Uppercase, Lowercase, Trim).
- **AGGREGATE**: Numerical summarization (SUM, AVG) grouped by specific columns.
- **VALIDATE**: Structural audit for required column presence.

### Security
All file paths are sanitized to prevent directory traversal vulnerabilities.

## API_CALL
Executes outbound HTTP requests to external services.

### Features
- **Methods**: Supports GET, POST, PUT, DELETE, and PATCH.
- **Retry Logic**: Automatic exponential backoff (2s, 4s, 8s) on non-2xx failures.
- **Validation**: Optional `expectedStatus` and `expectedResponse` substring checks.

## FILE_OPERATION
Handles localized file system manipulations.

### Operations
- **COPY / MOVE**: Secure file redistribution.
- **ZIP / UNZIP**: Compression and extraction with path-safety (Zip-Slip protection).
- **DELETE**: Permanent removal of files or recursive directory purging.

## NOTIFICATION
Routes messages to popular collaboration platforms.

### Channels
- Slack (Webhook)
- Discord (Webhook)
- Microsoft Teams (Webhook)
- Telegram (Bot API)

## REPORT_GENERATION
Synthesizes structured data into portable document formats.

### Output Formats
- **HTML**: Styled web-layout reports.
- **CSV / JSON**: Data-interchange formats.
- **PDF**: Fixed-layout documents (Requires PDF plugin).

## CLEANUP
Automated system maintenance and lifecycle management.

### Capabilities
- **File Aging**: Deletion of files older than $N$ days.
- **Pattern Matching**: Glob-based file purging.
- **Cache Invalidation**: On-demand clearing of Redis and JVM caches.
