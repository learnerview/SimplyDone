# User Interface Guide

The SimplyDone web interface is a Thymeleaf application served at `http://localhost:8080`. It provides dedicated pages for submitting jobs, monitoring queues, inspecting results, and performing administrative tasks. All pages use a sidebar for navigation and poll the REST API for live data.

---

## Page Reference

### Dashboard ( / )

The main landing page. Contains:

- **Status indicators**: total executed jobs, rejected jobs, success rate, and a connectivity badge showing whether Redis is reachable.
- **Quick Launch form**: Submit any job type directly from the dashboard. Fields update dynamically based on the selected Job Category. Supported fields include recipient, subject, body for email; URL, method, headers for API calls; operation and paths for file operations; and so on.
- **Priority Queue tables**: Two tables showing jobs currently in the HIGH and LOW priority queues with their job ID, type, status, and cancel actions.

### All Jobs ( /jobs )

Displays all jobs currently waiting in both queues with live polling every 10 seconds.

- Filter by priority (HIGH or LOW) or by User Index Identifier to narrow the view.
- Each job card shows the job type icon, first 8 characters of the ID, message, user, scheduled time, and a cancel button.
- Clicking the arrow icon opens the Job Monitor page for that job.

### Job Monitor ( /job-status?id={jobId} )

Detailed view of a single job. Accepts the job ID as a query parameter.

Sections:
- **Header card**: job type, status badge (PENDING / EXECUTED / FAILED / CANCELLED), UUID reference, and message.
- **Execution Meta**: assigned user, submission time, priority, retry count vs. max retries.
- **Input Payload**: raw JSON view of the `parameters` field.
- **Output Log**: execution result (on success) or error message with stack context (on failure).
- **Action Panel**: refresh button and cancel button (cancel only shown for PENDING jobs).

The page auto-refreshes every 5 seconds until the job reaches a terminal state.

### Email and Communications ( /email-send )

Dedicated form for `EMAIL_SEND` jobs. Fields:
- User Identifier
- Priority (HIGH / LOW)
- Recipient address
- Subject
- Optional SMTP credentials (sender email and app password) for per-job credential override
- HTML body editor

### Data Engine ( /data-process )

Form for `DATA_PROCESS` jobs. Fields:
- User Identifier, Priority
- Operation (TRANSFORM, AGGREGATE, VALIDATE)
- Raw JSON data input
- Column selection and transform/aggregate options

### API Integrator ( /api-call )

Form for `API_CALL` jobs. Fields:
- User Identifier, Priority
- URL, HTTP Method
- Custom headers (key/value pairs)
- Request body
- Delay in seconds

### File Systems ( /file-operation )

Form for `FILE_OPERATION` jobs. Fields:
- User Identifier, Priority
- Operation (COPY, MOVE, DELETE, ZIP, UNZIP, CREATE_DIRECTORY, LIST)
- Source path
- Target/destination path
- Delay in seconds

### Notifications ( /notification )

Form for `NOTIFICATION` jobs. Fields:
- User Identifier, Priority
- Channel (SLACK, DISCORD, TEAMS, TELEGRAM, WEBHOOK)
- Webhook URL
- Message body
- Optional title and accent color
- Schedule delay

### Reports ( /report-generation )

Form for `REPORT_GENERATION` jobs. Fields:
- User Identifier, Priority
- Output format (HTML, CSV, JSON, TXT)
- Output file path
- Report title
- Inline JSON data editor

### Maintenance ( /cleanup )

Form for `CLEANUP` jobs. Fields:
- User Identifier, Priority
- Operation (DELETE_OLD_FILES, DELETE_BY_PATTERN, PURGE_DIRECTORY)
- Target directory
- Max age in hours or file glob pattern
- Dry run toggle

### Asset Vault ( /assets )

File upload interface. Allows uploading CSV, JSON, and other data files that are referenced by path in job parameters (particularly for `DATA_PROCESS` and `REPORT_GENERATION`). Displays the server-side file path after upload for easy copy-paste into the parameter fields.

### Rate Limits ( /rate-limits )

Per-user rate limit inspector.

- Enter any User ID to query the current window usage from Redis.
- Displays: requests used in the active 60-second window, maximum allowed, seconds until the window resets, a colour-coded usage progress bar, and an Under Limit / Throttled badge.
- Explains how the fixed-window rate limiting works (Redis `INCR` + TTL).

### Diagnostics ( /system-health )

Displays live system metrics fetched from `/api/admin/health`, `/api/admin/stats`, and `/api/admin/performance`:
- System health status (OK / DEGRADED)
- Cache latency indicator
- Worker load level (Low / Medium / High based on total processed jobs)
- Engine capabilities (all registered job types)
- Throughput metrics: executed jobs, rejected jobs, queue sizes, JVM memory

Data refreshes automatically every 5 seconds.

### Executed Jobs ( /executed-jobs )

Historical table of all jobs that have reached a terminal state (EXECUTED or FAILED), sourced from PostgreSQL.

- Count cards show executed vs. failed totals.
- Table columns: Job ID, Type, User, Status, Submitted At, Executed At, Error (if any).
- Auto-refreshes every 10 seconds, paused via the Page Visibility API when the tab is not in focus.

### Dead Letter ( /dlq )

Table of jobs that exhausted all retry attempts. Columns: Job Identifier, Context/Message, Attempts, Exit Reason, and an action button to retry or discard. Empty state is displayed when the DLQ is clear.

### Admin Tools ( /admin )

System administration interface with two sections:
- **System Vitals**: performance stats cards with current queue depth and execution counts.
- **Recovery Protocol (DLQ)**: same DLQ table as `/dlq` with retry and remove actions.

---

## Navigation

All pages share a sidebar with four groups:
- **Core**: Dashboard, All Jobs, Executed Jobs
- **Services**: Email and Comms, Data Engine, API Integrator, File Systems, Notifications, Reports, Maintenance
- **Management**: Asset Vault, Rate Limits, Diagnostics
- **Reliability**: Dead Letter

The dark/light mode toggle is at the bottom of the sidebar.

---

## JavaScript API Client

All pages use `/js/modules/api.js` which provides typed wrapper functions around `fetch()`. The module logs all requests and responses to the browser console at the `group` level for easy debugging. Errors are surfaced through toast notifications in the bottom-right corner of the screen.

