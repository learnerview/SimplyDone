package com.learnerview.simplydone.repository;

import com.learnerview.simplydone.entity.AppSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppSettingRepository extends JpaRepository<AppSettingEntity, String> {
}