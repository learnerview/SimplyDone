# User Interface Guide

The SimplyDone dashboard provides a professional, high-performance interface for managing background task workflows and monitoring system health.

## Dashboard Overview

Accessible at `http://localhost:8080`, the dashboard serves as the central command center for the job scheduler.

### Real-Time Metrics
- **Job Throughput**: Monitors the total volume of successfully executed tasks.
- **Queue Depth**: Displays active loads in both High and Low priority lanes.
- **Worker Status**: Real-time polling frequency and last-active timestamps for background workers.

### System Health Monitoring
- **Database Status**: Connectivity audit for the PostgreSQL persistence layer.
- **Cache Integrity**: Verification of the Redis queuing infrastructure.
- **Storage Metrics**: Monitoring of available disk space for asset management.

## Core Management Interfaces

### 1. Global Queue Monitor
Located at `/jobs`, this view provides a deep dive into pending tasks.
- **Priority Segregation**: Clear distinction between High-Speed and Background lanes.
- **Live Updates**: The interface uses efficient polling to reflect queue changes within 2 seconds.
- **Job Actions**: Immediate cancellation capabilities for pending tasks.

### 2. Job Status Audit
Accessed via `/job-status`, this tool allows for surgical inspection of any job via its unique identifier.
- **Execution Lifecycle**: Tracks status from `SUBMITTED` to `EXECUTED` or `FAILED`.
- **Failure Analysis**: Detailed stack traces and error logs for troubleshooting failed executions.

### 3. Asset Vault
A specialized interface for managing file dependencies.
- **Upload Management**: Robust handling of CSV, JSON, and other job-related assets.
- **Path Mapping**: Easy retrieval of internal file paths for use in job parameters.

### 4. Admin Control Center
The `/admin` interface provides system-wide housekeeping tools.
- **DLQ Recovery**: Manage jobs that have exhausted their retry budget.
- **Queue Scrubbing**: Capabilities to clear specific execution lanes.
- **Performance Tuning**: Access to worker configuration and system statistics.

## Submission Workflows

Each job type utilizes a specialized form optimized for its specific technical requirements:

- **Email Dispatch**: Recipient validation and HTML/Plain-text toggles.
- **Data Processing**: Advanced CSV transformation and aggregation controls.
- **API Call Engine**: Custom header management and HTTP method selection.
- **File System Ops**: Path-safe operations for archiving and cleanup.

## UI Standards
- **Aesthetic**: Modern Light Mode with a focus on high contrast and professional typography.
- **Accessibility**: Semantic HTML5 structure with support for localized accessibility standards.
- **Responsiveness**: Fully optimized for diverse display environments (Desktop, Tablet, Mobile).
