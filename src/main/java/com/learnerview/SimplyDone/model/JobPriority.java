package com.learnerview.SimplyDone.model;

/**
 * Enumeration representing job priority levels.
 * 
 * HIGH priority jobs are always executed before LOW priority jobs
 * when both are ready for execution at the same time.
 */
public enum JobPriority {
    /**
     * High priority jobs - executed first when available
     */
    HIGH,
    
    /**
     * Low priority jobs - executed after all HIGH priority jobs
     */
    LOW
}
