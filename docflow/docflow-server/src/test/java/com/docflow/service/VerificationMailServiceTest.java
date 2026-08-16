package com.docflow.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationMailServiceTest {

    @Mock private JavaMailSender mailSender;
    private VerificationMailService service;

    @BeforeEach
    void setUp() {
        service = new VerificationMailService();
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "from", "no-reply@example.com");
        ReflectionTestUtils.setField(service, "expireMinutes", 10);
    }

    @Test
    void buildsHtmlMailWithoutUsingExternalSmtp() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.send("user@example.com", "PASSWORD_RESET", "123456");

        verify(mailSender).send(message);
        assertThat(message.getSubject()).contains("重置密码");
        assertThat(message.getContent().toString()).contains("123456", "10 分钟", "不要向任何人提供验证码");
    }
}
