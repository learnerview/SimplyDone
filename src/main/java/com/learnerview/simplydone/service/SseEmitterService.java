package com.learnerview.simplydone.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Manages Server-Sent Event connections and broadcasts job lifecycle events.
 * Events: JOB_CREATED, JOB_STARTED, JOB_COMPLETED, JOB_FAILED, STATS_UPDATE
 */
public interface SseEmitterService {
    /** Register a new browser client and return its SseEmitter. */
    SseEmitter subscribe(String clientId);

    /** Broadcast an event to ALL currently connected clients. */
    void broadcast(String eventType, Object data);
}
