package com.example.Credit.management.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class MpesaStkService {

    @Autowired
    private MpesaAuthService authService;

    private static final String STK_URL =
            "https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest";

    // ✅ OFFICIAL SANDBOX SHORTCODE
    private static final String SHORTCODE = "174379";

    // ✅ OFFICIAL SANDBOX PASSKEY
    private static final String PASSKEY =
            "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919";

    public Map<String, Object> initiateStkPush(
            String phoneNumber,
            String amount
    ) {

        try {

            String token =
                    authService.getAccessToken();

            System.out.println("✅ ACCESS TOKEN: " + token);

            phoneNumber = formatPhone(phoneNumber);

            String timestamp =
                    new SimpleDateFormat("yyyyMMddHHmmss")
                            .format(new Date());

            String passwordString =
                    SHORTCODE + PASSKEY + timestamp;

            String password =
                    Base64.getEncoder()
                            .encodeToString(
                                    passwordString.getBytes(StandardCharsets.UTF_8)
                            );

            Map<String, Object> payload =
                    new HashMap<>();

            payload.put("BusinessShortCode", SHORTCODE);
            payload.put("Password", password);
            payload.put("Timestamp", timestamp);
            payload.put("TransactionType", "CustomerPayBillOnline");

            // ✅ integer amount only
            int stkAmount =
                    (int) Double.parseDouble(amount);

            payload.put("Amount", stkAmount);

            payload.put("PartyA", phoneNumber);
            payload.put("PartyB", SHORTCODE);
            payload.put("PhoneNumber", phoneNumber);

            // ✅ Replace with your actual callback
            payload.put(
                    "CallBackURL",
                    "https://mydomain.com/mpesa-express-simulate/"
            );

            payload.put("AccountReference", "LOAN_PAYMENT");
            payload.put("TransactionDesc", "Loan repayment");

            System.out.println("================================");
            System.out.println("PHONE: " + phoneNumber);
            System.out.println("TIMESTAMP: " + timestamp);
            System.out.println("PASSWORD STRING: " + passwordString);
            System.out.println("PASSWORD: " + password);
            System.out.println("PAYLOAD: " + payload);
            System.out.println("================================");

            HttpHeaders headers =
                    new HttpHeaders();

            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(payload, headers);

            SimpleClientHttpRequestFactory factory =
                    new SimpleClientHttpRequestFactory();

            factory.setConnectTimeout(30000);
            factory.setReadTimeout(30000);

            RestTemplate restTemplate =
                    new RestTemplate(factory);

            ResponseEntity<Map> response =
                    restTemplate.postForEntity(
                            STK_URL,
                            request,
                            Map.class
                    );

            System.out.println("✅ STK RESPONSE: " + response.getBody());

            return response.getBody();

        } catch (Exception e) {

            e.printStackTrace();

            return Map.of(
                    "error", true,
                    "message", e.getMessage()
            );
        }
    }

    // ================================
// ✅ TRACK TRANSACTION STATUS
// ================================
    private final Map<String, Map<String, Object>> transactionStatus =
            new HashMap<>();

    // ================================
// ✅ UPDATE TRANSACTION STATUS
// ================================
    public void updateTransactionStatus(
            String checkoutRequestId,
            int resultCode,
            String resultDesc
    ) {

        Map<String, Object> status =
                new HashMap<>();

        status.put("resultCode", resultCode);
        status.put("resultDesc", resultDesc);

        transactionStatus.put(checkoutRequestId, status);

        System.out.println(
                "✅ Transaction updated: "
                        + checkoutRequestId
                        + " => "
                        + status
        );
    }

    // ================================
// ✅ GET TRANSACTION STATUS
// ================================
    public Map<String, Object> getTransactionStatus(
            String checkoutRequestId
    ) {

        return transactionStatus.getOrDefault(
                checkoutRequestId,
                Map.of(
                        "resultCode", -1,
                        "resultDesc", "Pending"
                )
        );
    }

    private String formatPhone(String phone) {

        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone number required");
        }

        phone = phone.trim();

        if (phone.startsWith("+")) {
            phone = phone.substring(1);
        }

        if (phone.startsWith("0")) {
            phone = "254" + phone.substring(1);
        }

        if (!phone.startsWith("254")) {
            phone = "254" + phone;
        }

        if (phone.length() != 12) {
            throw new IllegalArgumentException(
                    "Invalid phone number: " + phone
            );
        }

        return phone;
    }

}