package com.learnerview.SimplyDone.service.strategy;

import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobType;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Strategy for executing notification jobs.
 * Supports: SLACK, DISCORD, WEBHOOK, TEAMS, TELEGRAM notifications.
 */
@Component
@Slf4j
public class NotificationJobStrategy implements JobExecutionStrategy {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public JobType getSupportedJobType() {
        return JobType.NOTIFICATION;
    }

    @Override
    public void execute(Job job) throws Exception {
        log.info("Executing notification job: {} (ID: {})", job.getMessage(), job.getId());

        validateJob(job);

        Map<String, Object> params = job.getParameters();
        String channel = ((String) params.get("channel")).toUpperCase();
        String webhookUrl = (String) params.get("webhookUrl");
        String message = (String) params.get("message");
        String title = (String) params.get("title");

        try {
            switch (channel) {
                case "SLACK":
                    sendSlackNotification(webhookUrl, message, title, params);
                    break;
                case "DISCORD":
                    sendDiscordNotification(webhookUrl, message, title, params);
                    break;
                case "TEAMS":
                    sendTeamsNotification(webhookUrl, message, title, params);
                    break;
                case "TELEGRAM":
                    sendTelegramNotification(webhookUrl, message, params);
                    break;
                case "WEBHOOK":
                    sendGenericWebhook(webhookUrl, message, title, params);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported notification channel: " + channel);
            }

            log.info("Notification sent successfully via {} for job: {}", channel, job.getId());

        } catch (Exception e) {
            log.error("Notification failed for job {}: {}", job.getId(), e.getMessage());
            throw new Exception("Notification failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateJob(Job job) throws IllegalArgumentException {
        if (job.getParameters() == null) {
            throw new IllegalArgumentException("Notification job requires parameters");
        }

        Map<String, Object> params = job.getParameters();
        String channel = (String) params.get("channel");
        String webhookUrl = (String) params.get("webhookUrl");
        String message = (String) params.get("message");

        if (channel == null || channel.trim().isEmpty()) {
            throw new IllegalArgumentException("Notification 'channel' is required");
        }

        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Notification 'webhookUrl' is required");
        }

        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Notification 'message' is required");
        }

        // Validate URL format
        if (!isValidUrl(webhookUrl)) {
            throw new IllegalArgumentException("Invalid webhook URL format");
        }
    }

    @Override
    public long estimateExecutionTime(Job job) {
        // Webhook calls typically complete in 2-5 seconds
        return 5;
    }

    private void sendSlackNotification(String webhookUrl, String message, String title,
                                       Map<String, Object> params) throws Exception {
        Map<String, Object> payload = new HashMap<>();

        if (title != null && !title.isEmpty()) {
            // Rich message with blocks
            List<Map<String, Object>> blocks = new ArrayList<>();

            // Title block
            Map<String, Object> headerBlock = new HashMap<>();
            headerBlock.put("type", "header");
            headerBlock.put("text", Map.of("type", "plain_text", "text", title));
            blocks.add(headerBlock);

            // Message block
            Map<String, Object> messageBlock = new HashMap<>();
            messageBlock.put("type", "section");
            messageBlock.put("text", Map.of("type", "mrkdwn", "text", message));
            blocks.add(messageBlock);

            payload.put("blocks", blocks);
        } else {
            // Simple text message
            payload.put("text", message);
        }

        // Optional color/priority
        String priority = (String) params.get("priority");
        if (priority != null) {
            String color = getPriorityColor(priority);
            List<Map<String, Object>> attachments = new ArrayList<>();
            Map<String, Object> attachment = new HashMap<>();
            attachment.put("color", color);
            attachment.put("text", message);
            attachments.add(attachment);
            payload.put("attachments", attachments);
        }

        sendJsonWebhook(webhookUrl, payload);
        log.debug("Slack notification sent to: {}", webhookUrl);
    }

    private void sendDiscordNotification(String webhookUrl, String message, String title,
                                         Map<String, Object> params) throws Exception {
        Map<String, Object> payload = new HashMap<>();

        if (title != null && !title.isEmpty()) {
            // Rich embed
            List<Map<String, Object>> embeds = new ArrayList<>();
            Map<String, Object> embed = new HashMap<>();
            embed.put("title", title);
            embed.put("description", message);

            // Optional color
            String priority = (String) params.get("priority");
            if (priority != null) {
                embed.put("color", getDiscordColor(priority));
            }

            // Optional thumbnail
            String thumbnailUrl = (String) params.get("thumbnailUrl");
            if (thumbnailUrl != null) {
                embed.put("thumbnail", Map.of("url", thumbnailUrl));
            }

            // Timestamp
            embed.put("timestamp", java.time.Instant.now().toString());

            embeds.add(embed);
            payload.put("embeds", embeds);
        } else {
            // Simple content
            payload.put("content", message);
        }

        // Optional username override
        String username = (String) params.get("username");
        if (username != null) {
            payload.put("username", username);
        }

        sendJsonWebhook(webhookUrl, payload);
        log.debug("Discord notification sent to: {}", webhookUrl);
    }

    private void sendTeamsNotification(String webhookUrl, String message, String title,
                                       Map<String, Object> params) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("@type", "MessageCard");
        payload.put("@context", "https://schema.org/extensions");

        if (title != null && !title.isEmpty()) {
            payload.put("title", title);
        }

        payload.put("text", message);

        // Optional theme color
        String priority = (String) params.get("priority");
        if (priority != null) {
            payload.put("themeColor", getTeamsColor(priority));
        }

        // Optional actions (buttons)
        @SuppressWarnings("unchecked")
        List<Map<String, String>> actions = (List<Map<String, String>>) params.get("actions");
        if (actions != null && !actions.isEmpty()) {
            List<Map<String, Object>> potentialActions = new ArrayList<>();
            for (Map<String, String> action : actions) {
                Map<String, Object> actionCard = new HashMap<>();
                actionCard.put("@type", "OpenUri");
                actionCard.put("name", action.get("name"));
                actionCard.put("targets", List.of(Map.of("os", "default", "uri", action.get("url"))));
                potentialActions.add(actionCard);
            }
            payload.put("potentialAction", potentialActions);
        }

        sendJsonWebhook(webhookUrl, payload);
        log.debug("Teams notification sent to: {}", webhookUrl);
    }

    private void sendTelegramNotification(String webhookUrl, String message,
                                          Map<String, Object> params) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", message);

        // Telegram supports Markdown/HTML
        String parseMode = (String) params.getOrDefault("parseMode", "Markdown");
        payload.put("parse_mode", parseMode);

        // Optional disable notification
        Boolean disableNotification = (Boolean) params.get("disableNotification");
        if (Boolean.TRUE.equals(disableNotification)) {
            payload.put("disable_notification", true);
        }

        sendJsonWebhook(webhookUrl, payload);
        log.debug("Telegram notification sent to: {}", webhookUrl);
    }

    private void sendGenericWebhook(String webhookUrl, String message, String title,
                                    Map<String, Object> params) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);

        if (title != null && !title.isEmpty()) {
            payload.put("title", title);
        }

        // Include all additional parameters
        @SuppressWarnings("unchecked")
        Map<String, Object> additionalData = (Map<String, Object>) params.get("data");
        if (additionalData != null) {
            payload.put("data", additionalData);
        }

        // Add timestamp
        payload.put("timestamp", System.currentTimeMillis());

        sendJsonWebhook(webhookUrl, payload);
        log.debug("Generic webhook sent to: {}", webhookUrl);
    }

    private void sendJsonWebhook(String webhookUrl, Map<String, Object> payload) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                webhookUrl,
                HttpMethod.POST,
                requestEntity,
                String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new Exception("Webhook responded with non-success status: " +
                                  response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to send webhook to {}: {}", webhookUrl, e.getMessage());
            throw new Exception("Webhook delivery failed: " + e.getMessage(), e);
        }
    }

    private boolean isValidUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private String getPriorityColor(String priority) {
        switch (priority.toUpperCase()) {
            case "HIGH":
            case "CRITICAL":
                return "danger";
            case "MEDIUM":
            case "WARNING":
                return "warning";
            case "LOW":
            case "INFO":
            default:
                return "good";
        }
    }

    private int getDiscordColor(String priority) {
        // Discord uses decimal color codes
        switch (priority.toUpperCase()) {
            case "HIGH":
            case "CRITICAL":
                return 15158332; // Red
            case "MEDIUM":
            case "WARNING":
                return 16776960; // Yellow
            case "LOW":
            case "INFO":
            default:
                return 3066993; // Green
        }
    }

    private String getTeamsColor(String priority) {
        // Teams uses hex color codes
        switch (priority.toUpperCase()) {
            case "HIGH":
            case "CRITICAL":
                return "E74C3C"; // Red
            case "MEDIUM":
            case "WARNING":
                return "F39C12"; // Orange
            case "LOW":
            case "INFO":
            default:
                return "2ECC71"; // Green
        }
    }
}
