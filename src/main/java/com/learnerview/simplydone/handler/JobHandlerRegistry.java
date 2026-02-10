package com.learnerview.simplydone.handler;

import com.learnerview.simplydone.dto.HandlerInfoResponse;
import com.learnerview.simplydone.exception.HandlerNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * O(1) HashMap-based handler registry.
 * Auto-discovers all JobHandler beans via Spring constructor injection.
 */
@Component
@Slf4j
public class JobHandlerRegistry {

    private final Map<String, JobHandler> handlers;

    public JobHandlerRegistry(List<JobHandler> handlerList) {
        this.handlers = new LinkedHashMap<>();
        for (JobHandler handler : handlerList) {
            String type = handler.getJobType();
            if (handlers.containsKey(type)) {
                log.warn("Duplicate handler for '{}', keeping first", type);
                continue;
            }
            handlers.put(type, handler);
            log.info("Registered handler: '{}' -> {}", type, handler.getClass().getSimpleName());
        }
        log.info("Registry ready: {} handlers [{}]", handlers.size(), String.join(", ", handlers.keySet()));
    }

    public JobHandler getHandler(String jobType) {
        JobHandler h = handlers.get(jobType);
        if (h == null) throw new HandlerNotFoundException(jobType, getRegisteredTypes());
        return h;
    }

    public boolean hasHandler(String jobType) {
        return handlers.containsKey(jobType);
    }

    public Set<String> getRegisteredTypes() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    public List<HandlerInfoResponse> getHandlerInfo() {
        return handlers.values().stream()
                .map(h -> HandlerInfoResponse.builder()
                        .jobType(h.getJobType())
                        .description(h.getDescription())
                        .handlerClass(h.getClass().getSimpleName())
                        .build())
                .collect(Collectors.toList());
    }
}
