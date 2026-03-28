package com.learnerview.simplydone.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ApiKeyRequest {
    private String label;
    private String producer;
    private boolean admin;
}
