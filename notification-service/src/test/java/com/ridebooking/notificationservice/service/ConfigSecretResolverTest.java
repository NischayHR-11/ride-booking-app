package com.ridebooking.notificationservice.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigSecretResolverTest {

    @Test
    void resolveSecret_returnsPlainValueWhenNotEncoded() {
        ConfigSecretResolver resolver = new ConfigSecretResolver("pass");

        assertEquals("plain", resolver.resolveSecret("plain"));
    }

    @Test
    void resolveSecret_decodesBase64Value() {
        ConfigSecretResolver resolver = new ConfigSecretResolver("pass");
        String encoded = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));

        assertEquals("hello", resolver.resolveSecret("b64:" + encoded));
    }

    @Test
    void resolveSecret_decryptsEncryptedValue() throws Exception {
        String passphrase = "pass";
        ConfigSecretResolver resolver = new ConfigSecretResolver(passphrase);
        String encrypted = encrypt(passphrase, "secret-value");

        assertEquals("secret-value", resolver.resolveSecret("enc:" + encrypted));
    }

    @Test
    void resolveSecret_returnsEmptyWhenPassphraseMissing() throws Exception {
        String encrypted = encrypt("pass", "secret-value");
        ConfigSecretResolver resolver = new ConfigSecretResolver("");

        assertEquals("", resolver.resolveSecret("enc:" + encrypted));
    }

    @Test
    void resolveSecret_returnsEmptyOnInvalidBase64() {
        ConfigSecretResolver resolver = new ConfigSecretResolver("pass");

        assertEquals("", resolver.resolveSecret("b64:@@@"));
    }

    @Test
    void resolveSecret_returnsEmptyOnShortEncryptedValue() {
        ConfigSecretResolver resolver = new ConfigSecretResolver("pass");
        byte[] shortBytes = new byte[10];
        String encoded = Base64.getEncoder().encodeToString(shortBytes);

        assertEquals("", resolver.resolveSecret("enc:" + encoded));
    }

    private String encrypt(String passphrase, String plain) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = Arrays.copyOf(digest.digest(passphrase.getBytes(StandardCharsets.UTF_8)), 16);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

        byte[] out = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
        return Base64.getEncoder().encodeToString(out);
    }
}
