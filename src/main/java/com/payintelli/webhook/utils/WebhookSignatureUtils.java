package com.payintelli.webhook.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class WebhookSignatureUtils {
    
    public static String generateSignature(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
    
    public static boolean verifySignature(String payload, String secret, String receivedSignature) {
        try {
            String expectedSignature = "sha256=" + generateSignature(payload, secret);
            return expectedSignature.equals(receivedSignature);
        } catch (Exception e) {
            return false;
        }
    }
}