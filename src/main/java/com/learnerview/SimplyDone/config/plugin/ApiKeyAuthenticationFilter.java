package com.learnerview.SimplyDone.config.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnerview.SimplyDone.dto.plugin.PluginErrorDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * API Key Authentication Filter for plugin mode.
 * Validates API key header for all /api/* requests when plugin mode is enabled.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "simplydone.plugin.enabled", havingValue = "true")
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    
    @Value("${simplydone.plugin.security.api-key:}")
    private String configuredApiKey;
    
    @Value("${simplydone.plugin.security.require-api-key:true}")
    private boolean requireApiKey;
    
    @Value("${simplydone.plugin.security.key-header-name:X-Plugin-API-Key}")
    private String apiKeyHeader;
    
    private final ObjectMapper objectMapper;
    
    public ApiKeyAuthenticationFilter() {
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        String method = request.getMethod();
        
        // Skip authentication for non-API endpoints and health checks
        if (!requestPath.startsWith("/api/") || 
            requestPath.equals("/api/jobs/health") ||
            requestPath.equals("/actuator/health")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        log.debug("API request received: {} {}", method, requestPath);
        
        // Check if API key is required
        if (!requireApiKey) {
            log.debug("API key not required, allowing request through");
            filterChain.doFilter(request, response);
            return;
        }
        
        // Skip authentication for excluded endpoints (if any)
        if (isExcludedEndpoint(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Extract API key from header
        String providedApiKey = request.getHeader(apiKeyHeader);
        
        if (providedApiKey == null || providedApiKey.isEmpty()) {
            log.warn("API request missing authentication: {} {}", method, requestPath);
            sendAuthenticationError(response, "Missing authentication header: " + apiKeyHeader,
                    PluginErrorDto.ErrorCodes.MISSING_API_KEY);
            return;
        }
        
        // Validate API key
        if (!isValidApiKey(providedApiKey)) {
            log.warn("API request with invalid key: {} {}", method, requestPath);
            sendAuthenticationError(response, "Invalid API key",
                    PluginErrorDto.ErrorCodes.INVALID_API_KEY);
            return;
        }
        
        log.debug("API key validated successfully");
        filterChain.doFilter(request, response);
    }
    
    /**
     * Validate the provided API key against configured key.
     */
    private boolean isValidApiKey(String providedKey) {
        if (configuredApiKey == null || configuredApiKey.isEmpty()) {
            log.error("No API key configured for plugin mode");
            return false;
        }
        
        // Use timing-safe comparison to prevent timing attacks
        return timingSafeEquals(providedKey, configuredApiKey);
    }
    
    /**
     * Timing-safe string comparison to prevent timing attacks.
     */
    private boolean timingSafeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        
        int result = 0;
        
        // Compare all bytes even if lengths don't match
        for (int i = 0; i < Math.max(aBytes.length, bBytes.length); i++) {
            byte aByte = i < aBytes.length ? aBytes[i] : 0;
            byte bByte = i < bBytes.length ? bBytes[i] : 0;
            result |= aByte ^ bByte;
        }
        
        return result == 0 && aBytes.length == bBytes.length;
    }
    
    /**
     * Check if endpoint is excluded from authentication.
     */
    private boolean isExcludedEndpoint(String path) {
        // Plugin status endpoint may not require auth for public health checks
        return path.equals("/api/plugin/status");
    }
    
    /**
     * Send authentication error response.
     */
    private void sendAuthenticationError(HttpServletResponse response, String message, String errorCode) 
            throws IOException {
        
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        
        PluginErrorDto errorResponse = PluginErrorDto.builder()
                .error("AUTHENTICATION_ERROR")
                .plugin("simplydone-scheduler")
                .code(errorCode)
                .message(message)
                .timestamp(LocalDateTime.now())
                .severity(PluginErrorDto.Severity.ERROR)
                .component("SecurityFilter")
                .category("Authentication")
                .suggestion("Verify the API key in the " + apiKeyHeader + " header")
                .details(PluginErrorDto.ErrorDetails.builder()
                        .httpStatus(HttpServletResponse.SC_UNAUTHORIZED)
                        .retryable("false")
                        .build())
                .context(createErrorContext())
                .build();
        
        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
    
    /**
     * Create error context information.
     */
    private Map<String, Object> createErrorContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("expectedHeader", apiKeyHeader);
        context.put("requiresAuthentication", true);
        return context;
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        
        // Don't filter these paths
        return path.equals("/") ||
               path.equals("/actuator/health") ||
               path.startsWith("/static/") ||
               path.startsWith("/templates/");
    }
}
