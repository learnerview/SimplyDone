package com.learnerview.simplydone.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Manages Server-Sent Event connections and broadcasts job lifecycle events.
 * Events: JOB_CREATED, JOB_STARTED, JOB_COMPLETED, JOB_FAILED, STATS_UPDATE
 */
public interface SseEmitterService {
    /** Register a new tenant client and return its SseEmitter. */
    SseEmitter subscribe(String clientId, String producer);

    /** Broadcast an event to ALL currently connected clients of a specific tenant. */
    void broadcast(String producer, String eventType, Object data);
}
