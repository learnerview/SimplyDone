package com.learnerview.simplydone.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final ApiKeyAuthFilter apiKeyAuthFilter;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/login", "/signup", "/css/**", "/js/**", "/img/**",
                                                                "/favicon.ico",
                                                                "/api/jobs/types", "/api/ping", "/v3/api-docs/**",
                                                                "/swagger-ui/**",
                                                                "/swagger-ui.html",
                                                                // Explicit paths, not /actuator/** wildcard. If 'env'
                                                                // or 'beans'
                                                                // are accidentally added to
                                                                // management.endpoints.web.exposure.include,
                                                                // they will NOT be publicly reachable.
                                                                       "/actuator/health", "/actuator/metrics", "/ready")
                                                .permitAll()
                                                .requestMatchers("/api/auth/**").permitAll()
                                                .requestMatchers("/", "/dashboard", "/jobs", "/admin", "/dlq", "/error", "/recover")
                                                .permitAll()
                                                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/api/jobs/**", "/api/events").authenticated()
                                                .anyRequest().authenticated())
                                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}
