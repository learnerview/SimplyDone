package com.learnerview.simplydone.controller;

import com.learnerview.simplydone.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Provides a Server-Sent Events stream for real-time job status updates.
 * Connect from JS: const es = new EventSource('/api/events');
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterService sseEmitterService;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        String clientId = UUID.randomUUID().toString();
        return sseEmitterService.subscribe(clientId);
    }
}
