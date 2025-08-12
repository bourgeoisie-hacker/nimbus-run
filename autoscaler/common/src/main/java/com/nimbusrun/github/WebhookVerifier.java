package com.nimbusrun.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class WebhookVerifier {
    public static final String SECRET_HEADER="X-Hub-Signature-256";
    private static Logger log = LoggerFactory.getLogger(WebhookVerifier.class);
    public static boolean verifySignature(byte[] payloadBody, String secretToken, String signatureHeader) throws SecurityException {
        if (signatureHeader == null || signatureHeader.isEmpty()) {
            throw new SecurityException("x-hub-signature-256 header is missing!");
        }

        try {
            // Compute HMAC SHA-256
            SecretKeySpec secretKey = new SecretKeySpec(secretToken.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(payloadBody);
            String expectedSignature = "sha256=" + bytesToHex(hmacBytes);

            if (!constantTimeEquals(expectedSignature, signatureHeader)) {
                log.debug("Request signatures didn't match!");
                return false;
            }
            return true;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to calculate HMAC SHA-256", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
