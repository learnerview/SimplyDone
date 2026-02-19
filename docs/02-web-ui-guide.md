# Web UI Guide

SimplyDone includes a modern, responsive web interface for job submission, monitoring, and system administration. All pages are accessible via sidebar navigation.

## Dashboard

**URL:** `http://localhost:8080/`

The dashboard is the home page and system overview. It displays:

### System health
- **Redis connection** — UP/DOWN status
- **PostgreSQL connection** — UP/DOWN status
- **Disk space** — Available disk for uploads and logs

### Live metrics
- **Total jobs executed** — Lifetime counter (persistent in Redis)
- **Total rejections** — Jobs rejected by rate limiting
- **Queue depths** — Number of jobs waiting in HIGH and LOW priority queues
- **Worker status** — Active/idle, last poll timestamp, polling interval

### Quick actions
- View all jobs in queues (redirect to Jobs page)
- Submit a new job (redirect to submission form)
- Check system configuration

Use the dashboard to assess system health before submitting jobs.

## Jobs page

**URL:** `http://localhost:8080/jobs`

Live monitor of both priority queues. Displays all jobs currently waiting to be executed.

### HIGH priority queue
- Top section shows jobs waiting in the HIGH priority queue
- Worker prioritizes these before LOW priority jobs
- Typical use: Urgent notifications, critical operations

### LOW priority queue
- Bottom section shows jobs in the LOW priority queue
- Executed when HIGH queue is empty
- Typical use: Batch processing, reporting, cleanup

### For each job, displays:
- **Job ID** — Unique identifier (copyable)
- **Type** — One of the seven job types
- **Message** — Human-readable description
- **User** — Who submitted the job
- **Submitted** — Timestamp of submission
- **Execute at** — Scheduled execution time

### Auto-refresh
The page auto-refreshes every 2 seconds to show live queue updates. Watch jobs move from queue to execution in real-time.

### Actions
- **View details** — Click a job to see its status
- **Copy job ID** — Click the ID to copy it
- **Admin controls** — See Admin page to clear queues

## Job Status page

**URL:** `http://localhost:8080/job-status`

Lookup any job's current state by its ID.

### Search
Enter a job ID in the search field (you receive the ID when submitting a job).

### Job details
Returns:
- **ID** — The unique identifier
- **Type** — Job type (API_CALL, EMAIL_SEND, etc.)
- **Status** — PENDING, EXECUTING, EXECUTED, or FAILED
- **Priority** — HIGH or LOW
- **Message** — Description
- **User** — Submitter
- **Submitted** — Submission timestamp
- **Execute at** — Scheduled execution timestamp
- **Executed at** — Actual execution timestamp (if executed)
- **Error details** — If status is FAILED, shows the error message and stack trace

### Not found
If the job ID doesn't exist, the page displays "Job not found" and suggests checking the ID.

## Admin page

**URL:** `http://localhost:8080/admin`

Administrative controls for queue and system management. Restricted to administrators (set via configuration).

### Queue management
- View HIGH priority queue — All pending jobs
- View LOW priority queue — All pending jobs
- View dead-letter queue — All failed jobs
- Clear HIGH queue — Remove all jobs without executing
- Clear LOW queue — Remove all jobs without executing
- Clear dead-letter queue — Remove all failed jobs

### Dead-letter queue (DLQ)
Jobs that fail all retry attempts move to the DLQ. Manually inspect and decide:
- **Retry** — Requeue a job to attempt execution again
- **Delete** — Permanently remove a failed job
- **Analyze** — View error details to understand failure reason

### Statistics
- Total jobs executed (succeed vs fail)
- Total jobs rejected by rate limiting
- Average execution time
- Database and Redis connection health

### System configuration
- Display of active configuration values
- Connection string masks (passwords hidden)
- Worker thread status

## Job submission pages

Each job type has a dedicated submission page with a form tailored to its parameters.

### Common fields (all pages)

| Field | Type | Required | Description |
|---|---|---|---|
| **User ID** | Text | Yes | Identifier for the submitting user |
| **Priority** | Dropdown | Yes | HIGH (processed first) or LOW (processed after HIGH) |
| **Message** | Text | Yes | Human-readable description of the job |
| **Delay (seconds)** | Number | No | Seconds to wait before execution (default 0) |

### Email Send — `/email-send`

Send a transactional email.

Additional fields:
- **To** (required) — Recipient email address
- **Subject** (required) — Email subject line
- **Body** (required) — Email body content (HTML or plain text)

After submission, displays:
- Assigned job ID
- Link to Job Status page
- Confirmation that email sending is available (if EMAIL_ENABLED=true)

**Note:** Email delivery requires the EMAIL_API_KEY environment variable and EMAIL_ENABLED=true in configuration.

### Data Process — `/data-process`

Read and process CSV files.

Select operation:

**TRANSFORM**
- **Input file** — Absolute path to CSV file
- **Output file** — Where to write the transformed file
- **Transformations** — Select: UPPERCASE, LOWERCASE, TRIM
- Applies selected transformations to all values

**AGGREGATE**
- **Input file** — CSV file path
- **Group by** — Column name to group rows
- **Aggregate column** — Column to aggregate
- **Function** — SUM or AVG
- Produces summary output

**VALIDATE**
- **Input file** — CSV file path
- **Required columns** — CSV or space-separated column names
- Validates file contains all required columns

### API Call — `/api-call`

Make an outbound HTTP request.

Fields:
- **URL** (required) — Full URL to call
- **Method** (required) — GET, POST, PUT, DELETE, PATCH
- **Headers** (optional) — JSON object of headers
- **Body** (optional) — Request body (for POST/PUT)
- **Timeout (seconds)** — Max wait time

The job includes built-in retry logic. If the request fails, it automatically retries with exponential backoff.

### File Operation — `/file-operation`

Manipulate files on the file system.

Select operation:

**COPY**
- **Source** — File to copy
- **Destination** — Location of copy
- Creates copy at destination

**MOVE**
- **Source** — File to move
- **Destination** — New location
- Moves file and deletes original

**DELETE**
- **Path** — File or directory to delete
- For directories, optionally delete recursively

**ZIP**
- **Source** — File or directory to zip
- **Output** — Path for the ZIP file
- Compresses to a ZIP archive

**UNZIP**
- **Source** — ZIP file to extract
- **Output directory** — Where to extract files

### Notification — `/notification`

Send messages to external platforms.

Select platform:
- **Slack** → Webhook URL + message text
- **Discord** → Webhook URL + message text
- **Teams** → Webhook URL + message text
- **Telegram** → Bot token + chat ID + message text
- **Generic Webhook** → Any HTTP endpoint receiving POST with JSON

### Report Generation — `/report-generation`

Generate formatted reports.

Select format:

**HTML**
- **Title** — Report title
- **Content** — HTML content to include
- Generates styled HTML page

**CSV**
- **Headers** — CSV column names
- **Rows** — Data rows (one per line, comma-separated)
- Generates downloadable CSV file

**JSON**
- **Data** — JSON object or array
- **Pretty print** — Format for readability
- Generates JSON file

**Text**
- **Content** — Plain text content
- **Line width** — Characters per line for wrapping

### Cleanup — `/cleanup`

Scheduled maintenance tasks.

Select operation:

**DELETE OLD FILES**
- **Directory** — Path to scan
- **Older than (days)** — Delete files modified more than N days ago
- Useful for cleanup of logs, temp files, uploads

**CLEAR DIRECTORY**
- **Directory** — Path to empty
- Deletes all files in directory (keeps directory)

**PURGE CACHE**
- **Cache name** — In-memory cache identifier
- Clears cache to free memory

## File uploads

**URL:** `http://localhost:8080` (drag-and-drop zone visible on dashboard)

### Upload via web UI

1. Navigate to home page: `http://localhost:8080`
2. Drag and drop a file onto the "Drop files here" area
3. Watch the progress indicator
4. Uploaded file is added to the file list
5. Files are automatically deleted after 10 minutes (configurable)

### Upload via API

See [API Reference — File Upload](03-api-reference.md#file-upload-endpoints) for curl and code examples.

### Uploaded files
- Stored in OS temp directory (e.g., `C:\Users\...\AppData\Local\Temp\simplydone-uploads` on Windows)
- Automatically cleaned up 10 minutes after upload
- Maximum file size: 50MB
- Security: Path traversal prevention, filename validation

## Design and UX

### Glassmorphism design
- Modern frosted glass effect cards
- Responsive layout works on desktop, tablet, mobile
- Light and dark theme support (matches system preference)
- Smooth animations and transitions

### Responsive design
- Works on all screen sizes
- Sidebar navigation collapses on mobile
- Touch-friendly buttons and form inputs

### Accessibility
- Semantic HTML
- Keyboard navigation support
- ARIA labels for screen readers
- Color-independent design

## Tips and best practices

### Monitoring jobs
- Keep the Jobs page open to watch queue processing
- Use Job Status page to investigate failures
- Check Admin page dead-letter queue for persistent issues

### Choosing priority
- **HIGH** for:
  - User-facing operations that should be fast
  - Critical alerts and notifications
  - Time-sensitive integrations
- **LOW** for:
  - Batch processing
  - Periodic cleanup
  - Non-urgent reports
  - Archival operations

### Using delay
- **Delay 0** (default) — Execute immediately when worker picks up the job
- **Delay > 0** — Useful for rate limiting external APIs, scheduling operations for future time
- Example: Delay 3600 (execute in 1 hour) for scheduled maintenance

### Handling failures
1. Job fails → Retried up to 3 times with exponential backoff
2. All retries fail → Moved to dead-letter queue
3. From admin page, retry the job to attempt again
4. Check error details to identify why it failed

### File uploads
- Drag and drop multiple files at once
- Files visible in the file list immediately after upload
- Automatic cleanup means you don't need to manage file storage
- For DATA_PROCESS jobs, upload CSV files before submitting the job

## Keyboard shortcuts

- `/` — Focus search (if on Job Status page)
- `Tab` / `Shift+Tab` — Navigate between form fields
- `Enter` — Submit form (when focused on submit button)

## Troubleshooting UI issues

### Page not loading
- Check browser console for errors (F12 → Console)
- Verify backend is running: `curl http://localhost:8080/actuator/health`
- Check that view endpoints are enabled in configuration

### Forms not submitting
- All required fields must be filled
- Check browser console for validation errors
- Verify job ID is valid when looking up job status

### Queue not updating
- Page auto-refreshes every 2 seconds
- If not updating, manually refresh browser (F5)
- Check that worker thread is running (see Admin → Worker Status)
- Verify Redis connection is UP

### Sidebar navigation not working
- Refresh page
- Clear browser cache
- Try a different browser

## Performance

### Dashboard loading slowly
- System metrics are fetched on page load
- If PostgreSQL or Redis is slow, dashboard appears slow
- Check container health: `docker compose ps`

### Queue page frozen
- Page auto-refreshes every 2 seconds
- If frozen, browser may need restart
- Check network tab in developer tools for failed requests

### File upload slow
- Upload speed depends on:
  - File size (max 50MB)
  - Network bandwidth
  - Disk I/O speed
- Large files (>10MB) may take several seconds
