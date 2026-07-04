package com.deployflow.project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Service
public class WebhookSecurityService {

    private static final Logger log = LoggerFactory.getLogger(WebhookSecurityService.class);

    @Value("${application.security.github.webhook-secret}")
    private String webhookSecret;

    public boolean verifySignature(String payload, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            log.warn("Missing or invalid signature header");
            return false;
        }

        try {
            String actualSignature = signatureHeader.substring(7); // Remove "sha256="
            
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            
            // Convert byte array to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            String expectedSignature = hexString.toString();
            return expectedSignature.equals(actualSignature);

        } catch (Exception e) {
            log.error("Error verifying webhook signature", e);
            return false;
        }
    }
}