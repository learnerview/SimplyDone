package com.learnerview.simplydone.controller;

import com.learnerview.simplydone.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ready")
public class ReadinessController {

    @GetMapping
    public ResponseEntity<ApiResponse<Void>> ready() {
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("READY")
                .build());
    }
}
