package com.ridebooking.notificationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

import org.springframework.util.MultiValueMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class TwilioSmsClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ConfigSecretResolver secretResolver;

    @Test
    void sendSms_skipsWhenRecipientMissing() {
        TwilioSmsClient client = new TwilioSmsClient(restTemplate, "sid", "token", "from", "", secretResolver);

        client.sendSms("", "hello");

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendSms_skipsWhenConfigMissing() {
        TwilioSmsClient client = new TwilioSmsClient(restTemplate, "sid", "token", "from", "+1234567890", secretResolver);
        when(secretResolver.resolveSecret("sid")).thenReturn("");
        when(secretResolver.resolveSecret("token")).thenReturn("token");
        when(secretResolver.resolveSecret("from")).thenReturn("+1500");

        client.sendSms("+15551234567", "hello");

        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendSms_postsWhenConfigValid() {
        TwilioSmsClient client = new TwilioSmsClient(restTemplate, "sid", "token", "from", "+15551234567", secretResolver);
        when(secretResolver.resolveSecret("sid")).thenReturn("sid-123");
        when(secretResolver.resolveSecret("token")).thenReturn("token-123");
        when(secretResolver.resolveSecret("from")).thenReturn("+15005550006");
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("OK"));

        client.sendSms("not-phone", "hello");

        verify(restTemplate).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    void sendSms_usesProvidedNumberWhenValid() {
        TwilioSmsClient client = new TwilioSmsClient(restTemplate, "sid", "token", "from", "+15550001111", secretResolver);
        when(secretResolver.resolveSecret("sid")).thenReturn("sid-123");
        when(secretResolver.resolveSecret("token")).thenReturn("token-123");
        when(secretResolver.resolveSecret("from")).thenReturn("+15005550006");
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("OK"));

        client.sendSms("+15551234567", "hello");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<org.springframework.http.HttpEntity<MultiValueMap<String, String>>> captor =
            ArgumentCaptor.forClass((Class) org.springframework.http.HttpEntity.class);
        verify(restTemplate).postForEntity(anyString(), captor.capture(), eq(String.class));
        MultiValueMap<String, String> form = captor.getValue().getBody();
        assertEquals("+15551234567", form.getFirst("To"));
    }
}
