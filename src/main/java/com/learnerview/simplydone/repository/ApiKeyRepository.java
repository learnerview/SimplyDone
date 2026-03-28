package com.learnerview.simplydone.repository;

import com.learnerview.simplydone.entity.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, String> {
    Optional<ApiKeyEntity> findByApiKeyAndActiveTrue(String apiKey);
}
