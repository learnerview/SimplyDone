package com.learnerview.simplydone.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnerview.simplydone.service.SseEmitterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SseEmitterServiceImpl implements SseEmitterService {

    // 30-minute client timeout; browser will auto-reconnect via EventSource
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final ConcurrentHashMap<String, SseEmitter> clients = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SseEmitter subscribe(String clientId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);

        emitter.onCompletion(() -> clients.remove(clientId));
        emitter.onTimeout(() -> {
            clients.remove(clientId);
            emitter.complete();
        });
        emitter.onError(e -> clients.remove(clientId));

        clients.put(clientId, emitter);
        log.debug("SSE client connected: {} (total: {})", clientId, clients.size());

        // Send an initial heartbeat so the browser knows the connection is live
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"connected\":true}"));
        } catch (IOException e) {
            clients.remove(clientId);
        }
        return emitter;
    }

    @Override
    public void broadcast(String eventType, Object data) {
        if (clients.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("SSE serialization failed for event {}: {}", eventType, e.getMessage());
            return;
        }

        clients.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name(eventType).data(json));
            } catch (IOException | IllegalStateException e) {
                // Dead connection — remove it
                clients.remove(id);
                log.debug("Removed dead SSE client: {}", id);
            }
        });
    }
}
