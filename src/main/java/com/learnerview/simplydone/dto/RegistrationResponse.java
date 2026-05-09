package com.learnerview.simplydone.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RegistrationResponse {
    private String apiKey;
    private String producerId;
    private String organizationName;
    private String message;
}
