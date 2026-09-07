package com.ridebooking.notificationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class ResendEmailClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ConfigSecretResolver secretResolver;

    @Test
    void sendEmail_skipsWhenRecipientMissing() {
        ResendEmailClient client = new ResendEmailClient(restTemplate, "key", "from", "", secretResolver);

        client.sendEmail("", "Subject", "Body");

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendEmail_skipsWhenConfigMissing() {
        ResendEmailClient client = new ResendEmailClient(restTemplate, "key", "from", "to@example.com", secretResolver);
        when(secretResolver.resolveSecret("key")).thenReturn("");
        when(secretResolver.resolveSecret("from")).thenReturn("from@example.com");

        client.sendEmail("to@example.com", "Subject", "Body");

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendEmail_postsWhenConfigValid() {
        ResendEmailClient client = new ResendEmailClient(restTemplate, "key", "from", "default@example.com", secretResolver);
        when(secretResolver.resolveSecret("key")).thenReturn("key-123");
        when(secretResolver.resolveSecret("from")).thenReturn("from@example.com");
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("OK"));

        client.sendEmail("user-id", "Subject", "Body");

        verify(restTemplate).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    void sendEmail_usesProvidedEmailRecipient() {
        ResendEmailClient client = new ResendEmailClient(restTemplate, "key", "from", "default@example.com", secretResolver);
        when(secretResolver.resolveSecret("key")).thenReturn("key-123");
        when(secretResolver.resolveSecret("from")).thenReturn("from@example.com");
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("OK"));

        client.sendEmail("user@example.com", "Subject", "Body");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<org.springframework.http.HttpEntity<Map<String, Object>>> captor =
            ArgumentCaptor.forClass((Class) org.springframework.http.HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));
        Map<String, Object> payload = captor.getValue().getBody();
        assertEquals("user@example.com", payload.get("to"));
    }
}
