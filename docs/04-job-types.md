# Job Types

Each job is submitted with a `jobType` field and a `parameters` object. This page documents the parameters accepted by each job type.

---

## EMAIL_SEND

Sends a transactional email using the Resend API.

**Required configuration:** `simplydone.email.enabled=true` and a valid `simplydone.email.api-key`.

### Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| to | string | Yes | Recipient email address |
| subject | string | Yes | Email subject line |
| body | string | Yes | Email body content (HTML or plain text) |

### Example

```json
{
  "jobType": "EMAIL_SEND",
  "message": "Send welcome email",
  "priority": "HIGH",
  "delay": 0,
  "userId": "user-1",
  "parameters": {
    "to": "recipient@example.com",
    "subject": "Welcome to SimplyDone",
    "body": "<h1>Welcome!</h1><p>Your account is ready.</p>"
  }
}
```

### Notes

- The `to` address is validated against the pattern `[A-Za-z0-9+_.-]+@(.+)`. Invalid addresses are rejected before the job enters the queue.
- When email is disabled, the job is accepted and queued but execution is skipped with a log warning.

---

## DATA_PROCESS

Reads a CSV file and applies a transformation, aggregation, or validation operation.

### Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| operation | string | Yes | `TRANSFORM`, `AGGREGATE`, or `VALIDATE` |
| inputFile | string | Yes | Absolute path to the input CSV file |
| outputFile | string | Yes (TRANSFORM, AGGREGATE) | Absolute path for the output file |

**Additional parameters for TRANSFORM:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| transformations | array of strings | No | List of transformations to apply: `UPPERCASE`, `LOWERCASE`, `TRIM` |

**Additional parameters for AGGREGATE:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| groupBy | string | Yes | Column name to group rows by |
| aggregate | string | Yes | Column name to aggregate |
| function | string | No | Aggregation function: `SUM` (default) or `AVG` |

**Additional parameters for VALIDATE:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| requiredColumns | array of strings | No | Column names that must be present in the file |

### Example — Transform

```json
{
  "jobType": "DATA_PROCESS",
  "message": "Normalize customer CSV",
  "priority": "LOW",
  "delay": 0,
  "userId": "user-1",
  "parameters": {
    "operation": "TRANSFORM",
    "inputFile": "/data/customers.csv",
    "outputFile": "/data/customers-normalized.csv",
    "transformations": ["TRIM", "UPPERCASE"]
  }
}
```

### Example — Aggregate

```json
{
  "jobType": "DATA_PROCESS",
  "message": "Sum sales by region",
  "priority": "LOW",
  "delay": 0,
  "userId": "user-1",
  "parameters": {
    "operation": "AGGREGATE",
    "inputFile": "/data/sales.csv",
    "outputFile": "/data/sales-by-region.csv",
    "groupBy": "region",
    "aggregate": "amount",
    "function": "SUM"
  }
}
```

### Notes

- File paths are checked for path traversal attempts. Paths that normalize to a different location are rejected.
- Execution time is estimated at 1 second per MB of input file size, with a minimum of 5 seconds.

---

## API_CALL

Makes an HTTP request to an external service.

### Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| url | string | Yes | Full HTTP or HTTPS URL |
| method | string | No | HTTP method: `GET` (default), `POST`, `PUT`, `PATCH`, `DELETE` |
| headers | object | No | Key-value map of request headers |
| body | object | No | Request body for POST, PUT, PATCH |
| maxRetries | integer | No | Number of retry attempts on failure (default 3) |
| expectedStatus | integer | No | Expected HTTP status code (default 200). Execution fails if the response does not match. |
| expectedResponse | string | No | Expected substring in the response body. Execution fails if not present. |
| storeResponse | boolean | No | Whether to store the response body for later retrieval |

### Example

```json
{
  "jobType": "API_CALL",
  "message": "Ping external service",
  "priority": "HIGH",
  "delay": 0,
  "userId": "user-1",
  "parameters": {
    "url": "https://httpbin.org/post",
    "method": "POST",
    "headers": {
      "Authorization": "Bearer token123"
    },
    "body": {
      "event": "user_signup",
      "userId": "user-1"
    },
    "expectedStatus": 200
  }
}
```

### Notes

- Connect timeout is 5 seconds. Read timeout is 10 seconds.
- Built-in retry uses exponential backoff: 2 seconds for the first retry, 4 seconds for the second, 8 seconds for the third.
- The `User-Agent` header is set to `SimplyDone-JobScheduler/1.0` automatically.

---

## FILE_OPERATION

Performs a file system operation on the server where SimplyDone is running.

### Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| operation | string | Yes | `COPY`, `MOVE`, `DELETE`, `ZIP`, `UNZIP`, `CREATE_DIRECTORY`, or `LIST` |
| source | string | Yes (all except CREATE_DIRECTORY) | Source file or directory path |
| target | string | Yes (COPY, MOVE, ZIP, UNZIP, CREATE_DIRECTORY) | Target file or directory path |

### Example — Copy

```json
{
  "jobType": "FILE_OPERATION",
  "message": "Backup config file",
  "priority": "LOW",
  "delay": 0,
  "userId": "user-1",
  "parameters": {
    "operation": "COPY",
    "source": "/app/config/settings.json",
    "target": "/app/backup/settings.json"
  }
}
```

### Example — Zip

```json
{
  "jobType": "FILE_OPERATION",
  "message": "Archive logs directory",
  "priority": "LOW",
  "delay": 0,
  "userId": "user-1",
  "parameters": {
    "operation": "ZIP",
    "source": "/app/logs",
    "target": "/app/archive/logs.zip"
  }
}
```

### Notes

- Files larger than 100 MB are rejected.
- Path traversal attempts are blocked. Access to system directories (`/windows`, `/etc`, `c:\windows`) is denied.
- ZIP extraction includes zip-slip protection: extracted entries cannot escape the target directory.

---

## NOTIFICATION

Sends a notification to a messaging platform or generic webhook.

### Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| channel | string | Yes | `SLACK`, `DISCORD`, `TEAMS`, `TELEGRAM`, or `WEBHOOK` |
| webhookUrl | string | Yes | Full HTTPS webhook URL |
| message | string | Yes | Notification message text |
| title | string | No | Notification title |
| priority | string | No | `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `WARNING`, or `INFO` — controls formatting color |

**Additional parameters for DISCORD:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| username | string | No | Override the webhook display name |
| thumbnailUrl | string | No | URL for a thumbnail image in the embed |

**Additional parameters for TEAMS:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| actions | array of objects | No | Action buttons, each with `name` and `url` fields |

**Additional parameters for TELEGRAM:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| parseMode | string | No | `Markdown` (default) or `HTML` |
| disableNotification | boolean | No | Send silently without notifying the user |

**Additional parameters for WEBHOOK (generic):**

| Parameter | Type | Required | Description |
|---|---|---|---|
| data | object | No | Additional key-value data to include in the payload |

### Example — Slack

```json
{
  "jobType": "NOTIFICATION",
  "message": "Deployment completed",
  "priority": "HIGH",
  "delay": 0,
  "userId": "user-1",
  "parameters": {
    "channel": "SLACK",
    "webhookUrl": "https://hooks.slack.com/services/T00/B00/xxx",
    "message": "Production deployment finished successfully.",
    "title": "Deployment Update",
    "priority": "HIGH"
  }
}
```

### Example — Generic Webhook

```json
{
  "jobType": "NOTIFICATION",
  "message": "Send event webhook",
  "priority": "LOW",
  "delay": 0,
  "userId": "user-1",
  "parameters": {
    "channel": "WEBHOOK",
    "webhookUrl": "https://your-service.example.com/hooks/events",
    "message": "Job queue threshold exceeded",
    "data": {
      "queueDepth": 100,
      "threshold": 50
    }
  }
}
```

### Notes

- The `NOTIFICATION` job type is not idempotent by default. Retries may result in duplicate notifications.
- Priority values map to colors: `CRITICAL`/`HIGH` = red, `MEDIUM`/`WARNING` = yellow, `LOW`/`INFO` = green.

---

## REPORT_GENERATION

Generates a formatted report file from supplied data.

### Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| format | string | Yes | `HTML`, `CSV`, `JSON`, or `TXT` |
| outputPath | string | Yes | Absolute path for the generated report file |
| data | array | Yes | Array of objects representing report rows |
| title | string | No | Report title |
| columns | array of strings | No | Column names. If omitted, columns are inferred from the keys of the first data row. |

### Example

```json
{
  "jobType": "REPORT_GENERATION",
  "message": "Generate monthly sales report",
  "priority": "LOW",
  "delay": 0,
  "userId": "user-1",
  "parameters": {
    "format": "HTML",
    "outputPath": "/reports/sales-jan.html",
    "title": "Sales Report — January",
    "columns": ["region", "product", "amount"],
    "data": [
      { "region": "North", "product": "Widget A", "amount": 1200 },
      { "region": "South", "product": "Widget B", "amount": 850 }
    ]
  }
}
```

### Notes

- HTML reports include CSS styling, a metadata section with the generation timestamp, and row count.
- CSV output uses proper escaping for values containing commas, quotes, or newlines.
- JSON output wraps the data in a metadata envelope with `title` and `generatedAt` fields.
- PDF and XLSX formats are defined but require additional Maven dependencies (`itext7-core` and `poi-ooxml`). Without those dependencies, PDF falls back to HTML and XLSX falls back to CSV.

---

## CLEANUP

Performs maintenance operations on the file system or cache.

### Parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| operation | string | Yes | See operations table below |
| directory | string | Yes (most operations) | Target directory path |

**Supported operations:**

| Operation | Description |
|---|---|
| `DELETE_OLD_FILES` | Delete files older than a specified number of days |
| `CLEAR_DIRECTORY` | Remove all contents of a directory |
| `CLEAR_TEMP` | Clear temporary files matching a prefix |
| `ARCHIVE_OLD_FILES` | Move old files to an archive directory |
| `DELETE_BY_PATTERN` | Delete files matching a glob pattern |
| `CLEANUP_LOGS` | Delete log files older than a specified number of days |
| `PURGE_CACHE` | Purge application or Redis cache |

**Additional parameters by operation:**

`DELETE_OLD_FILES`:

| Parameter | Type | Default | Description |
|---|---|---|---|
| olderThanDays | integer | 30 | Delete files older than this many days |
| recursive | boolean | false | Recurse into subdirectories |

`CLEAR_DIRECTORY`:

| Parameter | Type | Default | Description |
|---|---|---|---|
| preserveDirectory | boolean | true | Keep the directory itself after clearing its contents |

`CLEAR_TEMP`:

| Parameter | Type | Default | Description |
|---|---|---|---|
| prefix | string | `simplydone-` | File name prefix to match |
| olderThanHours | integer | 24 | Only delete files older than this many hours |

`ARCHIVE_OLD_FILES`:

| Parameter | Type | Default | Description |
|---|---|---|---|
| archiveDirectory | string | — | Required. Destination directory for archived files |
| olderThanDays | integer | 90 | Archive files older than this many days |

`DELETE_BY_PATTERN`:

| Parameter | Type | Default | Description |
|---|---|---|---|
| pattern | string | — | Required. Glob pattern (e.g., `*.log`, `tmp-*`) |
| recursive | boolean | false | Recurse into subdirectories |

`CLEANUP_LOGS`:

| Parameter | Type | Default | Description |
|---|---|---|---|
| keepDays | integer | 7 | Keep log files created within this many days |
| compressOld | boolean | false | Compress old logs before deleting |

`PURGE_CACHE`:

| Parameter | Type | Default | Description |
|---|---|---|---|
| cacheType | string | — | `redis` or `application` |

### Example — Delete old files

```json
{
  "jobType": "CLEANUP",
  "message": "Remove logs older than 30 days",
  "priority": "LOW",
  "delay": 0,
  "userId": "user-1",
  "parameters": {
    "operation": "DELETE_OLD_FILES",
    "directory": "/app/logs",
    "olderThanDays": 30,
    "recursive": true
  }
}
```

### Example — Archive old files

```json
{
  "jobType": "CLEANUP",
  "message": "Archive old reports",
  "priority": "LOW",
  "delay": 0,
  "userId": "user-1",
  "parameters": {
    "operation": "ARCHIVE_OLD_FILES",
    "directory": "/app/reports",
    "archiveDirectory": "/app/archive/reports",
    "olderThanDays": 90
  }
}
```

### Notes

- Execution time is estimated at 1 second per 1000 files, with a minimum of 5 seconds.
- Operations are idempotent — missing files and empty directories are handled gracefully.
- The `PURGE_CACHE` operation with `cacheType: redis` clears the Redis key space used by the scheduler.
