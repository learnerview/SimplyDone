# Troubleshooting & Maintenance

This guide identifies common operational issues and provides structured remediation steps.

## Startup Failures

### Port Conflict
- **Error**: `Connector on port 8080 failed to start`
- **Solution**: Identify the occupying process or launch SimplyDone on a different port using the `PORT` environment variable.

### Infrastructure Unavailability
- **Error**: `Connection refused` (PostgreSQL or Redis)
- **Solution**: Ensure Docker containers are active. For remote infrastructure, verify network connectivity and firewall rules for ports 5432 and 6379.

## Execution Issues

### Jobs Stuck in "PENDING"
- **Investigation**: Check the logs for `JobWorker` polling events.
- **Solution**: Ensure the worker interval is not excessively high. Verify that the Redis queue contains the expected Job IDs.

### Recurring Failures (Retries)
- **Investigation**: Inspect the `errorMessage` field in the job details.
- **Solution**: Common issues include invalid SMTP credentials, network timeouts on API calls, or directory permission errors for file operations.

## Maintenance Operations

### Clearing the Dead Letter Queue
Jobs that fail all retry attempts are moved to the DLQ. Use the Admin interface to:
- **Analyze**: Review error details to identify systemic issues.
- **Retry**: Re-enqueue jobs once the underlying issue is resolved.
- **Purge**: Clear old failure records to maintain database performance.

### Log Management
By default, SimplyDone logs to standard output for container compatibility.
- Ensure log rotation is enabled if logging to disk.
- Monitor `logs_debug.txt` for detailed tracing when debugging complex strategies.

## Performance Tuning
- **High Latency**: Increase the worker polling frequency or scale application instances.
- **Memory Pressure**: Monitor the JVM heap usage; increase the `-Xmx` limit if processing large data sets.
- **Database Bottlenecks**: Audit the Hikari pool size and ensure appropriate indexing on the `job` table.
