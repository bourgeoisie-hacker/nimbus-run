package com.nimbusrun.github;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class WebhookVerifierTest {

    @Test
    void verifySignature_returnsTrue_whenHeaderMatches() {
        byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        String secret = "topsecret";
        String header = hmacSha256Header(payload, secret);

        assertTrue(WebhookVerifier.verifySignature(payload, secret, header));
    }

    @Test
    void verifySignature_returnsTrue_withEmptyPayload_whenHeaderMatches() {
        byte[] payload = new byte[0];
        String secret = "topsecret";
        String header = hmacSha256Header(payload, secret);

        assertTrue(WebhookVerifier.verifySignature(payload, secret, header));
    }

    @Test
    void verifySignature_returnsFalse_whenHeaderDiffersByOneChar() {
        byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        String secret = "topsecret";
        String header = hmacSha256Header(payload, secret);

        // Flip the last hex char to ensure equal length but different content
        String mutated = header.substring(0, header.length() - 1)
                + (header.endsWith("0") ? "1" : "0");

        assertFalse(WebhookVerifier.verifySignature(payload, secret, mutated));
    }

    @Test
    void verifySignature_returnsFalse_whenHeaderMissingPrefixButHexMatches() {
        byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        String secret = "topsecret";
        String header = hmacSha256Header(payload, secret);

        // Remove "sha256=" prefix – length will differ and should fail
        String noPrefix = header.replaceFirst("^sha256=", "");
        assertFalse(WebhookVerifier.verifySignature(payload, secret, noPrefix));
    }

    @Test
    void verifySignature_returnsFalse_whenHeaderHasUppercaseHex() {
        byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        String secret = "topsecret";
        String headerUpper = hmacSha256Header(payload, secret).toUpperCase(); // changes case

        assertFalse(WebhookVerifier.verifySignature(payload, secret, headerUpper));
    }

    @Test
    void verifySignature_returnsFalse_whenHeaderHasWhitespace() {
        byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        String secret = "topsecret";
        String header = "  " + hmacSha256Header(payload, secret) + "  ";

        assertFalse(WebhookVerifier.verifySignature(payload, secret, header));
    }

    @Test
    void verifySignature_throwsSecurityException_whenHeaderIsNull() {
        byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        String secret = "topsecret";

        SecurityException ex = assertThrows(SecurityException.class,
                () -> WebhookVerifier.verifySignature(payload, secret, null));
        assertTrue(ex.getMessage().toLowerCase().contains("header is missing"));
    }

    @Test
    void verifySignature_throwsSecurityException_whenHeaderIsEmpty() {
        byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        String secret = "topsecret";

        assertThrows(SecurityException.class,
                () -> WebhookVerifier.verifySignature(payload, secret, ""));
    }

    // --- helpers ---

    private static String hmacSha256Header(byte[] payload, String secret) {
        try {
            SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] hmac = mac.doFinal(payload);
            return "sha256=" + toHex(hmac);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        sb.append(String.format("%02x", b));
      }
        return sb.toString();
    }
}
