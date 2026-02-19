package com.learnerview.SimplyDone.model;

/**
 * Enumeration of different job types that can be executed.
 * Each type has its own execution strategy and requirements.
 */
public enum JobType {
    EMAIL_SEND("Send Email", "Sends an email to specified recipients"),
    DATA_PROCESS("Process Data", "Processes and transforms data files"),
    API_CALL("API Call", "Makes HTTP calls to external services"),
    FILE_OPERATION("File Operation", "Performs file system operations"),
    NOTIFICATION("Send Notification", "Sends push notifications"),
    REPORT_GENERATION("Generate Report", "Generates analytical reports"),
    CLEANUP("Cleanup Task", "Performs cleanup operations");

    private final String displayName;
    private final String description;

    JobType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
