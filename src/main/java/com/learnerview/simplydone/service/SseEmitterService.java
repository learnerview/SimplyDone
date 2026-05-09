package com.learnerview.simplydone.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Manages tenant SSE connections and job lifecycle broadcasts.
 */
public interface SseEmitterService {
    /** Registers a tenant client and returns the SSE emitter. */
    SseEmitter subscribe(String clientId, String producer);

    /** Broadcasts an event to all active clients for a tenant. */
    void broadcast(String producer, String eventType, Object data);
}
