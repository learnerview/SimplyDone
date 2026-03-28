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

    // Map of producer (tenant) -> Set of active emitters
    private final ConcurrentHashMap<String, java.util.Set<SseEmitter>> tenantClients = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SseEmitter subscribe(String clientId, String producer) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);

        java.util.Set<SseEmitter> emitters = tenantClients.computeIfAbsent(producer, k -> java.util.Collections.synchronizedSet(new java.util.HashSet<>()));
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(emitter));

        log.debug("SSE client connected for tenant {}: {} (tenant total: {})", producer, clientId, emitters.size());

        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"connected\":true}"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    @Override
    public void broadcast(String producer, String eventType, Object data) {
        java.util.Set<SseEmitter> emitters = tenantClients.get(producer);
        if (emitters == null || emitters.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("SSE serialization failed for event {}: {}", eventType, e.getMessage());
            return;
        }

        synchronized (emitters) {
            java.util.Iterator<SseEmitter> iterator = emitters.iterator();
            while (iterator.hasNext()) {
                SseEmitter emitter = iterator.next();
                try {
                    emitter.send(SseEmitter.event().name(eventType).data(json));
                } catch (IOException | IllegalStateException e) {
                    iterator.remove();
                    log.debug("Removed dead SSE client for tenant: {}", producer);
                }
            }
        }
    }
}
