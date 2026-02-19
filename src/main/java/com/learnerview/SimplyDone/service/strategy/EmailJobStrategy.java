package com.learnerview.SimplyDone.service.strategy;

import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobType;
import com.learnerview.SimplyDone.service.SmtpEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Strategy for executing email sending jobs using Resend API.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EmailJobStrategy implements JobExecutionStrategy {

    private final SmtpEmailService smtpEmailService;

    @Override
    public JobType getSupportedJobType() {
        return JobType.EMAIL_SEND;
    }

    @Override
    public void execute(Job job) throws Exception {
        log.info("Executing email job: {} (ID: {})", job.getMessage(), job.getId());
        
        validateJob(job);
        
        Map<String, Object> params = job.getParameters();
        String to = (String) params.get("to");
        String subject = (String) params.get("subject");
        String body = (String) params.get("body");
        
        try {
            Map<String, Object> result = smtpEmailService.sendEmail(to, subject, body);
            
            if (Boolean.TRUE.equals(result.get("success"))) {
                log.info("Email sent successfully to: {} for job: {}", to, job.getId());
            } else {
                String message = (String) result.getOrDefault("message", "Unknown error");
                throw new Exception("Email sending failed: " + message);
            }
            
        } catch (Exception e) {
            log.error("Failed to send email for job {}: {}", job.getId(), e.getMessage());
            throw new Exception("Email sending failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void validateJob(Job job) throws IllegalArgumentException {
        if (job.getParameters() == null) {
            throw new IllegalArgumentException("Email job requires parameters");
        }
        
        Map<String, Object> params = job.getParameters();
        String to = (String) params.get("to");
        String subject = (String) params.get("subject");
        String body = (String) params.get("body");
        
        if (to == null || to.trim().isEmpty()) {
            throw new IllegalArgumentException("Email 'to' address is required");
        }
        if (subject == null || subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Email 'subject' is required");
        }
        if (body == null || body.trim().isEmpty()) {
            throw new IllegalArgumentException("Email 'body' is required");
        }
        
        // Basic email validation
        if (!isValidEmail(to)) {
            throw new IllegalArgumentException("Invalid email address: " + to);
        }
    }
    
    @Override
    public long estimateExecutionTime(Job job) {
        // Email sending via API typically takes 2-10 seconds
        return 10;
    }
    
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }
}
