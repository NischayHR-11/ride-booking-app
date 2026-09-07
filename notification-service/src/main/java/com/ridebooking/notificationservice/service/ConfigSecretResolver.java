package com.ridebooking.notificationservice.service;

import in.zeta.spectra.capture.SpectraLogger;
import olympus.trace.OlympusSpectra;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

@Component
public class ConfigSecretResolver {

    private static final SpectraLogger logger = OlympusSpectra.getLogger(ConfigSecretResolver.class);

    private final String cryptoPassphrase;

    public ConfigSecretResolver(@Value("${resend.crypto.passphrase:}") String cryptoPassphrase) {
        this.cryptoPassphrase = cryptoPassphrase;
    }

    public String resolveSecret(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (value.startsWith("enc:")) {
            return decryptAesGcm(value.substring(4));
        }
        if (value.startsWith("b64:")) {
            return decodeBase64(value.substring(4));
        }
        return value;
    }

    private String decodeBase64(String raw) {
        try {
            byte[] decoded = Base64.getDecoder().decode(raw);
            return new String(decoded, StandardCharsets.UTF_8).trim();
        } catch (IllegalArgumentException ex) {
            logger.warn("[ConfigSecretResolver] Invalid base64 config value").log();
            return "";
        }
    }

    private String decryptAesGcm(String raw) {
        if (!StringUtils.hasText(cryptoPassphrase)) {
            logger.warn("[ConfigSecretResolver] Missing crypto passphrase").log();
            return "";
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(raw);
            if (decoded.length < 13) {
                logger.warn("[ConfigSecretResolver] Encrypted value too short").log();
                return "";
            }
            byte[] iv = Arrays.copyOfRange(decoded, 0, 12);
            byte[] cipherText = Arrays.copyOfRange(decoded, 12, decoded.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(cryptoPassphrase), spec);
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8).trim();
        } catch (Exception ex) {
            logger.warn("[ConfigSecretResolver] Failed to decrypt config value")
                    .attr("error", ex.getMessage())
                    .log();
            return "";
        }
    }

    private SecretKeySpec deriveKey(String passphrase) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(passphrase.getBytes(StandardCharsets.UTF_8));
        byte[] keyBytes = Arrays.copyOf(hash, 16);
        return new SecretKeySpec(keyBytes, "AES");
    }
}
