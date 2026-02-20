# Job Type Catalog

Each job type is executed by a dedicated strategy class that validates the parameters, performs the work, and handles failures. This document lists every supported job type, its required and optional parameters, and behavioral notes.

---

## EMAIL_SEND

Sends an HTML email via Gmail SMTP using Spring JavaMailSender.

### Parameters

| Parameter | Required | Description |
|---|---|---|
| `to` | yes | Recipient email address |
| `subject` | yes | Email subject line |
| `body` | yes | Email body content (HTML is supported) |
| `senderEmail` | no | Custom sender Gmail address. Overrides the system default |
| `senderPassword` | no | App Password for the custom sender. Required when `senderEmail` is provided |

### Behavior

- If `EMAIL_ENABLED` is `false` and no `senderEmail` is provided, the job fails with the message "Email service is disabled in configuration".
- If a custom `senderEmail` and `senderPassword` are provided, a temporary `JavaMailSenderImpl` is constructed for that single delivery. The system-level credentials are not affected.
- The `body` field is sent as HTML. Use standard HTML markup.

### Example Parameters

```json
{
  "to": "customer@example.com",
  "subject": "Your order has shipped",
  "body": "<h1>Order Shipped</h1><p>Your order #12345 is on its way.</p>"
}
```

---

## DATA_PROCESS

Performs column-level transformations and validations on in-memory data sets.

### Parameters

| Parameter | Required | Description |
|---|---|---|
| `operation` | yes | One of: `TRANSFORM`, `AGGREGATE`, `VALIDATE` |
| `data` | yes | Array of objects (rows), each being a JSON object with string keys |
| `columns` | no | List of column names to process. Defaults to all columns in the first row |
| `targetColumn` | depends | Required for `AGGREGATE` — the numeric column to summarize |
| `groupByColumn` | no | Column to group by in `AGGREGATE` operations |
| `aggregateFunction` | depends | Required for `AGGREGATE` — one of `SUM`, `AVG`, `COUNT`, `MIN`, `MAX` |
| `transformType` | depends | Required for `TRANSFORM` — one of `UPPERCASE`, `LOWERCASE`, `TRIM` |
| `requiredColumns` | depends | Required for `VALIDATE` — list of column names that must be present |

### Example: Transform

```json
{
  "operation": "TRANSFORM",
  "data": [{"name": "  alice  "}, {"name": "  bob  "}],
  "columns": ["name"],
  "transformType": "TRIM"
}
```

---

## API_CALL

Executes an outbound HTTP request to an external service.

### Parameters

| Parameter | Required | Description |
|---|---|---|
| `url` | yes | Full URL including scheme (`http://` or `https://`) |
| `method` | no | HTTP method. Default: `GET`. Supported: `GET`, `POST`, `PUT`, `PATCH`, `DELETE` |
| `headers` | no | Object of key/value string pairs added to the request |
| `body` | no | Request body for `POST`, `PUT`, `PATCH` requests |
| `expectedStatus` | no | HTTP status code the response must match. Default: `200` |
| `expectedResponse` | no | String that must appear in the response body |
| `maxRetries` | no | Override per-job retry count. Default: uses global `simplydone.retry.max-attempts` |
| `storeResponse` | no | Boolean. If `true`, logs the response body. Default: `false` |

### Behavior

- Timeouts: 5-second connection timeout, 10-second read timeout.
- On non-matching `expectedStatus` or missing `expectedResponse`, the attempt is counted as a failure and retried.

### Example

```json
{
  "url": "https://api.example.com/webhooks/deploy",
  "method": "POST",
  "headers": {"Authorization": "Bearer token123"},
  "body": {"event": "deploy.complete"},
  "expectedStatus": 200
}
```

---

## FILE_OPERATION

Performs file system operations. All paths are validated against a blocklist to prevent access to system directories (`/etc`, `/root`, `/boot`, `/sys`, `/proc`).

### Parameters

| Parameter | Required | Description |
|---|---|---|
| `operation` | yes | One of: `COPY`, `MOVE`, `DELETE`, `ZIP`, `UNZIP`, `CREATE_DIRECTORY`, `LIST` |
| `source` | depends | Source path. Required for all operations except `CREATE_DIRECTORY` when using `target` |
| `target` | depends | Destination path. Required for `COPY`, `MOVE`, `ZIP`, `UNZIP`. Optional for `CREATE_DIRECTORY` (falls back to `source` if omitted) |

### Operation Details

| Operation | Description |
|---|---|
| `COPY` | Copies the file or directory at `source` to `target` |
| `MOVE` | Moves the file at `source` to `target` |
| `DELETE` | Deletes the file or recursively deletes the directory at `source`. No-op if the path does not exist |
| `ZIP` | Creates a ZIP archive at `target` from the file or directory at `source` |
| `UNZIP` | Extracts the ZIP archive at `source` into the directory at `target` |
| `CREATE_DIRECTORY` | Creates the directory (and any intermediate directories) at `target` or `source` |
| `LIST` | Logs the contents of the directory at `source` |

### Example

```json
{
  "operation": "CREATE_DIRECTORY",
  "source": "/tmp/simplydone-reports"
}
```

---

## NOTIFICATION

Posts a message to a supported collaboration platform via webhook or bot API.

### Parameters

| Parameter | Required | Description |
|---|---|---|
| `channel` | yes | One of: `SLACK`, `DISCORD`, `TEAMS`, `TELEGRAM`, `WEBHOOK` |
| `webhookUrl` | yes | The full webhook URL or Telegram Bot API URL |
| `message` | yes | Message text (Markdown is supported on most platforms) |
| `title` | no | Title displayed as a header (Slack, Discord, Teams) |
| `color` | no | Accent color as hex string (Slack attachment, Discord embed) |
| `username` | no | Display name override (Discord only) |
| `parseMode` | no | `Markdown` or `HTML` (Telegram only). Default: `Markdown` |

### Example: Slack

```json
{
  "channel": "SLACK",
  "webhookUrl": "https://hooks.slack.com/services/T.../B.../...",
  "message": "Deployment to production completed successfully.",
  "title": "Deploy Notification"
}
```

---

## REPORT_GENERATION

Generates a structured report file from provided data.

### Parameters

| Parameter | Required | Description |
|---|---|---|
| `format` | yes | One of: `HTML`, `CSV`, `JSON`, `TXT` (TEXT also accepted) |
| `outputPath` | yes | Absolute file path where the report is written |
| `data` | yes | Array of objects representing the report rows |
| `title` | no | Report title shown in the header (HTML and TXT formats) |
| `columns` | no | Ordered list of column names. Auto-detected from the first data row if omitted |

Note: `PDF` and `EXCEL`/`XLSX` formats are recognized but throw an `UnsupportedOperationException` indicating the library dependency required to enable them. HTML and CSV reports are generated as fallbacks respectively.

### Example

```json
{
  "format": "CSV",
  "outputPath": "/tmp/reports/sales-2024-01.csv",
  "title": "Sales Report January 2024",
  "data": [
    {"product": "Widget A", "units": 150, "revenue": 3000},
    {"product": "Widget B", "units": 80, "revenue": 1600}
  ]
}
```

---

## CLEANUP

Deletes files that match age or pattern criteria. Used for automated maintenance of log directories, temporary upload folders, and report archives.

### Parameters

| Parameter | Required | Description |
|---|---|---|
| `operation` | yes | One of: `DELETE_OLD_FILES`, `DELETE_BY_PATTERN`, `PURGE_DIRECTORY` |
| `directory` | yes | Directory to scan |
| `maxAgeHours` | depends | Required for `DELETE_OLD_FILES` — delete files older than this many hours |
| `pattern` | depends | Required for `DELETE_BY_PATTERN` — glob pattern (e.g., `*.tmp`, `report-*.csv`) |
| `dryRun` | no | Boolean. If `true`, logs what would be deleted without actually deleting. Default: `false` |

### Example

```json
{
  "operation": "DELETE_OLD_FILES",
  "directory": "/tmp/simplydone-uploads",
  "maxAgeHours": 24
}
```

