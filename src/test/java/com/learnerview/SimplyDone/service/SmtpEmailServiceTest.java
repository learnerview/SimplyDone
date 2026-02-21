package com.learnerview.SimplyDone.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmtpEmailService Tests")
class SmtpEmailServiceTest {

    @Mock
    private JavaMailSenderImpl mailSender;

    private SmtpEmailService service;

    @BeforeEach
    void setUp() {
        service = new SmtpEmailService(mailSender);
    }

    private void setFields(String defaultFrom, boolean emailEnabled) {
        ReflectionTestUtils.setField(service, "defaultFromAddress", defaultFrom);
        ReflectionTestUtils.setField(service, "emailEnabled", emailEnabled);
    }

    // -------------------------------------------------------
    // Disabled / skip scenarios
    // -------------------------------------------------------

    @Test
    @DisplayName("sendEmail skips when emailEnabled=false, no credentials, no custom sender")
    void sendEmail_disabledNoCredentials_returnsSkipped() throws MessagingException {
        setFields("", false);

        Map<String, Object> result = service.sendEmail("to@example.com", "Subject", "Body", null, null);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("simulated")).isEqualTo(true);
        verifyNoInteractions(mailSender);
    }

    // -------------------------------------------------------
    // Auto-enable when SMTP credentials are configured
    // -------------------------------------------------------

    @Test
    @DisplayName("sendEmail proceeds when emailEnabled=false but SMTP credentials are configured")
    void sendEmail_credentialsConfigured_doesNotSkipWhenFlagFalse() throws MessagingException {
        setFields("sender@gmail.com", false);

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        Map<String, Object> result = service.sendEmail("to@example.com", "Subject", "Body", null, null);

        assertThat(result.get("success")).isEqualTo(true);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendEmail proceeds when emailEnabled=true")
    void sendEmail_emailEnabledTrue_sends() throws MessagingException {
        setFields("sender@gmail.com", true);

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        Map<String, Object> result = service.sendEmail("to@example.com", "Subject", "Body", null, null);

        assertThat(result.get("success")).isEqualTo(true);
        verify(mailSender).send(any(MimeMessage.class));
    }
}
