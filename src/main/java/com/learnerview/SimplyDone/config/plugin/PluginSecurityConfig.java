package com.learnerview.SimplyDone.config.plugin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Security configuration for plugin mode.
 * Registers API key authentication filter and configures security settings.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "simplydone.plugin.enabled", havingValue = "true")
public class PluginSecurityConfig {
    
    /**
     * Register API Key Authentication Filter.
     * This filter intercepts all /api/* requests and validates the API key header.
     */
    @Bean
    public FilterRegistrationBean<ApiKeyAuthenticationFilter> apiKeyAuthenticationFilter(
            ApiKeyAuthenticationFilter filter) {
        
        log.info("Registering API Key Authentication Filter for plugin mode");
        
        FilterRegistrationBean<ApiKeyAuthenticationFilter> registration = 
                new FilterRegistrationBean<>(filter);
        
        // Apply to all API endpoints
        registration.addUrlPatterns("/api/*");
        
        // Set filter order to run early in the chain
        registration.setOrder(1);
        
        // Filter name for logging
        registration.setName("apiKeyAuthenticationFilter");
        
        return registration;
    }
}
