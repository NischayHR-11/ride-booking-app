package com.ridebooking.notificationservice.service;

import in.zeta.spectra.capture.SpectraLogger;
import olympus.trace.OlympusSpectra;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class TwilioSmsClient {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(TwilioSmsClient.class);

    private final RestTemplate restTemplate;
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final String defaultTo;
    private final ConfigSecretResolver secretResolver;

    public TwilioSmsClient(RestTemplate restTemplate,
            @Value("${twilio.account.sid:}") String accountSid,
            @Value("${twilio.auth.token:}") String authToken,
            @Value("${twilio.from.number:}") String fromNumber,
            @Value("${twilio.default.to:}") String defaultTo,
            ConfigSecretResolver secretResolver) {
        this.restTemplate = restTemplate;
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
        this.defaultTo = defaultTo;
        this.secretResolver = secretResolver;
    }

    public void sendSms(String to, String message) {
        String resolvedTo = resolveRecipient(to);
        if (!StringUtils.hasText(resolvedTo)) {
            logger.warn("[TwilioSmsClient] Skipping SMS: recipient missing").log();
            return;
        }

        String resolvedSid = secretResolver.resolveSecret(accountSid);
        String resolvedToken = secretResolver.resolveSecret(authToken);
        String resolvedFrom = secretResolver.resolveSecret(fromNumber);
        if (!StringUtils.hasText(resolvedSid) || !StringUtils.hasText(resolvedToken)
                || !StringUtils.hasText(resolvedFrom)) {
            logger.warn("[TwilioSmsClient] Skipping SMS: Twilio config missing").log();
            return;
        }

        String url = "https://api.twilio.com/2010-04-01/Accounts/" + resolvedSid + "/Messages.json";
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", resolvedFrom);
        form.add("To", resolvedTo);
        form.add("Body", message);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", buildBasicAuth(resolvedSid, resolvedToken));

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            logger.info("[TwilioSmsClient] SMS sent")
                    .attr("status", String.valueOf(response.getStatusCode().value()))
                    .attr("to", resolvedTo)
                    .log();
        } catch (Exception ex) {
            logger.error("[TwilioSmsClient] SMS send failed")
                    .attr("to", resolvedTo)
                    .attr("error", ex.getMessage())
                    .log();
        }
    }

    private String resolveRecipient(String candidate) {
        if (StringUtils.hasText(candidate) && candidate.startsWith("+")) {
            return candidate;
        }
        if (StringUtils.hasText(defaultTo)) {
            return defaultTo;
        }
        return "";
    }

    private String buildBasicAuth(String sid, String token) {
        String raw = sid + ":" + token;
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
