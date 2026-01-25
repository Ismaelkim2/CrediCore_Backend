package com.example.Credit.management.services;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;

@Service
public class MpesaAuthService {

    // ✅ Sandbox credentials (use these while testing)
    private final String consumerKey = "KvlmT7tPG2iOvWxiDbVFAyE5SeggnaHM1Xk37Uf4MGxh9A1H";
    private final String consumerSecret = "wk32bU0R9F1RotxGaNbjJy6kY1YB322IaQWDYUOLF5QTJIj4JARB1NhAqtriifUC";

    // ✅ Sandbox authentication URL
    private final String authUrl = "https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials";

    public String getAccessToken() {
        try {
            RestTemplate restTemplate = new RestTemplate();

            // Encode consumer key and secret
            String credentials = consumerKey + ":" + consumerSecret;
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

            // Create headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encodedCredentials);

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Make request
            ResponseEntity<Map> response = restTemplate.exchange(
                    authUrl, HttpMethod.GET, request, Map.class
            );

            // Extract access token
            return (String) response.getBody().get("access_token");

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("❌ Error getting M-Pesa access token: " + e.getMessage());
        }
    }
}
