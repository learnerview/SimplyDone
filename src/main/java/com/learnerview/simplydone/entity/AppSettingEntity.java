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
@Table(name = "app_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppSettingEntity {

    @Id
    @Column(name = "setting_key", length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 1024)
    private String settingValue;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}