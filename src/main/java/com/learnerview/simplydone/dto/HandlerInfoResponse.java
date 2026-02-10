package com.learnerview.simplydone.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class HandlerInfoResponse {
    private String jobType;
    private String description;
    private String handlerClass;
}
