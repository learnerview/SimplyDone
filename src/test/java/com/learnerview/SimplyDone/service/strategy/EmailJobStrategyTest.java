package com.learnerview.SimplyDone.service.strategy;

import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobPriority;
import com.learnerview.SimplyDone.model.JobType;
import com.learnerview.SimplyDone.service.SmtpEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailJobStrategy Tests")
class EmailJobStrategyTest {

    @Mock
    private SmtpEmailService smtpEmailService;

    private EmailJobStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new EmailJobStrategy(smtpEmailService);
    }

    private Job buildEmailJob(Map<String, Object> params) {
        return Job.builder()
                .id("email-1")
                .jobType(JobType.EMAIL_SEND)
                .userId("user-1")
                .priority(JobPriority.HIGH)
                .message("Send email")
                .executeAt(Instant.now())
                .parameters(params)
                .build();
    }

    // -------------------------------------------------------
    // getSupportedJobType
    // -------------------------------------------------------

    @Test
    @DisplayName("getSupportedJobType returns EMAIL_SEND")
    void getSupportedJobType_returnsEmailSend() {
        assertThat(strategy.getSupportedJobType()).isEqualTo(JobType.EMAIL_SEND);
    }

    // -------------------------------------------------------
    // validateJob
    // -------------------------------------------------------

    @Test
    @DisplayName("validateJob passes with valid parameters")
    void validateJob_validParams_noException() {
        Map<String, Object> params = Map.of(
                "to", "user@example.com",
                "subject", "Hello",
                "body", "World"
        );
        assertThatNoException().isThrownBy(() -> strategy.validateJob(buildEmailJob(params)));
    }

    @Test
    @DisplayName("validateJob throws when parameters are null")
    void validateJob_nullParams_throwsIllegalArgument() {
        Job job = buildEmailJob(null);
        assertThatThrownBy(() -> strategy.validateJob(job))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parameters");
    }

    @Test
    @DisplayName("validateJob throws when 'to' is missing")
    void validateJob_missingTo_throwsIllegalArgument() {
        Map<String, Object> params = Map.of("subject", "Hi", "body", "Text");
        assertThatThrownBy(() -> strategy.validateJob(buildEmailJob(params)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("to");
    }

    @Test
    @DisplayName("validateJob throws when 'subject' is blank")
    void validateJob_blankSubject_throwsIllegalArgument() {
        Map<String, Object> params = new HashMap<>();
        params.put("to", "user@example.com");
        params.put("subject", "  ");
        params.put("body", "Content");
        assertThatThrownBy(() -> strategy.validateJob(buildEmailJob(params)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject");
    }

    @Test
    @DisplayName("validateJob throws when 'body' is missing")
    void validateJob_missingBody_throwsIllegalArgument() {
        Map<String, Object> params = Map.of("to", "user@example.com", "subject", "Hi");
        assertThatThrownBy(() -> strategy.validateJob(buildEmailJob(params)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("body");
    }

    @Test
    @DisplayName("validateJob throws on invalid email address format")
    void validateJob_invalidEmail_throwsIllegalArgument() {
        Map<String, Object> params = Map.of(
                "to", "not-an-email",
                "subject", "Hi",
                "body", "Text"
        );
        assertThatThrownBy(() -> strategy.validateJob(buildEmailJob(params)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email");
    }

    // -------------------------------------------------------
    // execute
    // -------------------------------------------------------

    @Test
    @DisplayName("execute sends email via SmtpEmailService and succeeds")
    void execute_validJob_sendsEmail() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("to", "dest@example.com");
        params.put("subject", "Test Subject");
        params.put("body", "<p>Hello</p>");

        when(smtpEmailService.sendEmail(
                eq("dest@example.com"),
                eq("Test Subject"),
                eq("<p>Hello</p>"),
                isNull(),
                isNull()))
                .thenReturn(Map.of("success", true, "from", "sender@example.com"));

        assertThatNoException().isThrownBy(() -> strategy.execute(buildEmailJob(params)));

        verify(smtpEmailService).sendEmail(
                eq("dest@example.com"),
                eq("Test Subject"),
                eq("<p>Hello</p>"),
                isNull(),
                isNull());
    }

    @Test
    @DisplayName("execute uses custom sender credentials when provided")
    void execute_customSender_passesSenderCredentials() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("to", "dest@example.com");
        params.put("subject", "Hi");
        params.put("body", "Body");
        params.put("senderEmail", "custom@example.com");
        params.put("senderPassword", "secret");

        when(smtpEmailService.sendEmail(anyString(), anyString(), anyString(),
                eq("custom@example.com"), eq("secret")))
                .thenReturn(Map.of("success", true, "from", "custom@example.com"));

        assertThatNoException().isThrownBy(() -> strategy.execute(buildEmailJob(params)));
    }

    @Test
    @DisplayName("execute throws when SmtpEmailService returns success=false")
    void execute_serviceReturnsFail_throwsException() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("to", "dest@example.com");
        params.put("subject", "Hi");
        params.put("body", "Body");

        when(smtpEmailService.sendEmail(any(), any(), any(), any(), any()))
                .thenReturn(Map.of("success", false, "message", "Email service is disabled"));

        assertThatThrownBy(() -> strategy.execute(buildEmailJob(params)))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Email sending failed");
    }

    @Test
    @DisplayName("execute throws when SmtpEmailService throws an exception with original message")
    void execute_serviceThrows_rethrowsOriginalException() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("to", "dest@example.com");
        params.put("subject", "Hi");
        params.put("body", "Body");

        when(smtpEmailService.sendEmail(any(), any(), any(), any(), any()))
                .thenThrow(new jakarta.mail.MessagingException("SMTP auth failed"));

        assertThatThrownBy(() -> strategy.execute(buildEmailJob(params)))
                .isInstanceOf(jakarta.mail.MessagingException.class)
                .hasMessage("SMTP auth failed");
    }

    // -------------------------------------------------------
    // estimateExecutionTime
    // -------------------------------------------------------

    @Test
    @DisplayName("estimateExecutionTime returns a positive value")
    void estimateExecutionTime_returnsPositive() {
        Map<String, Object> params = Map.of("to", "a@b.com", "subject", "s", "body", "b");
        long estimate = strategy.estimateExecutionTime(buildEmailJob(params));
        assertThat(estimate).isPositive();
    }
}
