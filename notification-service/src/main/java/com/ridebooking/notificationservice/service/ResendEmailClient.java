package com.ridebooking.notificationservice.service;

import in.zeta.spectra.capture.SpectraLogger;
import olympus.trace.OlympusSpectra;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class ResendEmailClient {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(ResendEmailClient.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String fromEmail;
    private final String defaultTo;
    private final ConfigSecretResolver secretResolver;

    public ResendEmailClient(RestTemplate restTemplate,
            @Value("${resend.api.key:}") String apiKey,
            @Value("${resend.from.email:}") String fromEmail,
            @Value("${resend.default.to:}") String defaultTo,
            ConfigSecretResolver secretResolver) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.defaultTo = defaultTo;
        this.secretResolver = secretResolver;
    }

    public void sendEmail(String to, String subject, String textBody) {
        String resolvedTo = resolveRecipient(to);
        if (!StringUtils.hasText(resolvedTo)) {
            logger.warn("[ResendEmailClient] Skipping email: recipient missing").log();
            return;
        }
        String resolvedApiKey = secretResolver.resolveSecret(apiKey);
        String resolvedFrom = secretResolver.resolveSecret(fromEmail);
        if (!StringUtils.hasText(resolvedApiKey) || !StringUtils.hasText(resolvedFrom)) {
            logger.warn("[ResendEmailClient] Skipping email: Resend config missing").log();
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("from", resolvedFrom);
        payload.put("to", resolvedTo);
        payload.put("subject", subject);
        payload.put("html", textBody);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(resolvedApiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(RESEND_API_URL, request, String.class);
            logger.info("[ResendEmailClient] Email sent")
                    .attr("status", String.valueOf(response.getStatusCode().value()))
                    .attr("to", resolvedTo)
                    .log();
        } catch (Exception ex) {
            logger.error("[ResendEmailClient] Email send failed")
                    .attr("to", resolvedTo)
                    .attr("error", ex.getMessage())
                    .log();
        }
    }

    private String resolveRecipient(String candidate) {
        if (StringUtils.hasText(candidate) && candidate.contains("@")) {
            return candidate;
        }
        if (StringUtils.hasText(defaultTo)) {
            return defaultTo;
        }
        return "";
    }

}
