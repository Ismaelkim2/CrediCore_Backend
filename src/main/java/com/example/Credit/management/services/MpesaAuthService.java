package com.example.Credit.management.services;

import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class MpesaAuthService {

    private static final String CONSUMER_KEY =
            "KvlmT7tPG2iOvWxiDbVFAyE5SeggnaHM1Xk37Uf4MGxh9A1H";

    private static final String CONSUMER_SECRET =
            "wk32bU0R9F1RotxGaNbjJy6kY1YB322IaQWDYUOLF5QTJIj4JARB1NhAqtriifUC";

    private static final String AUTH_URL =
            "https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials";

    public String getAccessToken() {

        try {

            SimpleClientHttpRequestFactory factory =
                    new SimpleClientHttpRequestFactory();

            factory.setConnectTimeout(30000);
            factory.setReadTimeout(30000);

            RestTemplate restTemplate =
                    new RestTemplate(factory);

            String credentials =
                    CONSUMER_KEY + ":" + CONSUMER_SECRET;

            String encoded =
                    Base64.getEncoder()
                            .encodeToString(
                                    credentials.getBytes(StandardCharsets.UTF_8)
                            );

            HttpHeaders headers =
                    new HttpHeaders();

            headers.set("Authorization", "Basic " + encoded);
            headers.setAccept(MediaType.parseMediaTypes("application/json"));

            HttpEntity<String> entity =
                    new HttpEntity<>(headers);

            ResponseEntity<Map> response =
                    restTemplate.exchange(
                            AUTH_URL,
                            HttpMethod.GET,
                            entity,
                            Map.class
                    );

            System.out.println("✅ TOKEN RESPONSE: " + response.getBody());

            if (response.getBody() == null) {
                throw new RuntimeException("Empty token response");
            }

            Object token =
                    response.getBody().get("access_token");

            if (token == null) {
                throw new RuntimeException("Access token missing");
            }

            return token.toString();

        } catch (Exception e) {

            e.printStackTrace();

            throw new RuntimeException(
                    "❌ Error getting M-Pesa access token: "
                            + e.getMessage()
            );
        }
    }
}