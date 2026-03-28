package com.learnerview.simplydone.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "api_keys")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ApiKeyEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(length = 100, nullable = false, unique = true)
    private String apiKey;

    @Column(length = 120, nullable = false)
    private String producer;

    @Column(length = 255)
    private String label;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "is_admin", nullable = false)
    private boolean admin = false;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
