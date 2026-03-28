package com.learnerview.simplydone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ApiKeyResponse {
    private String id;
    private String apiKey;
    private String producer;
    private String label;
    private boolean active;
    @JsonProperty("admin")
    private boolean admin;
    private Instant createdAt;
}
