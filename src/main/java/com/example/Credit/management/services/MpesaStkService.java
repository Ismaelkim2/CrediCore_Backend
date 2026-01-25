package com.example.Credit.management.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class MpesaStkService {

    @Autowired
    private MpesaAuthService authService;

    private final Map<String, Map<String, Object>> transactionStatus = new HashMap<>();

    /**
     * ✅ Simulate crediting user account after successful STK callback
     */
    public void creditUserAccount(String phoneNumber, double amount) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            System.out.println("⚠️ Cannot credit account — phone number is null or empty.");
            return;
        }
        System.out.println("💰 Crediting account for " + phoneNumber + " with amount: " + amount);
    }

    /**
     * ✅ Initiate STK Push request
     */
    public String initiateStkPush(String phoneNumber, String amount) {
        try {
            // ✅ Validate phone number
            if (phoneNumber == null || phoneNumber.isBlank()) {
                throw new IllegalArgumentException("Phone number is missing.");
            }

            // ✅ Normalize to 254 format
            phoneNumber = phoneNumber.trim();
            if (phoneNumber.startsWith("0")) {
                phoneNumber = "254" + phoneNumber.substring(1);
            } else if (phoneNumber.startsWith("+254")) {
                phoneNumber = phoneNumber.substring(1); // remove '+' if exists
            } else if (!phoneNumber.startsWith("254")) {
                throw new IllegalArgumentException("Invalid phone number format. Use 07XXXXXXXX.");
            }

            System.out.println("📲 Initiating STK Push for " + phoneNumber + " | Amount: " + amount);

            // ✅ Get access token
            String token = authService.getAccessToken();

            // ✅ Safaricom sandbox URL
            String url = "https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());

            // ✅ Daraja credentials (sandbox)
            String businessShortCode = "174379"; // Paybill or till number
            String passkey = "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919";

            // ✅ Generate password
            String password = Base64.getEncoder().encodeToString(
                    (businessShortCode + passkey + timestamp).getBytes()
            );

            // ✅ Prepare payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("BusinessShortCode", businessShortCode);
            payload.put("Password", password);
            payload.put("Timestamp", timestamp);
            payload.put("TransactionType", "CustomerPayBillOnline");
            int validAmount = (int) Double.parseDouble(amount);
            payload.put("Amount", validAmount);
            payload.put("PartyA", phoneNumber);
            payload.put("PartyB", businessShortCode);
            payload.put("PhoneNumber", phoneNumber);
            payload.put("CallBackURL", "https://ea0ae552c08f.ngrok-free.app/api/mpesa/callback");
            payload.put("AccountReference", "Kims Bank");
            payload.put("TransactionDesc", "CreditBank annual deductions via M-Pesa");

            // ✅ Send STK push request
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            System.out.println("🌐 Sending STK Push request to Safaricom...");
            System.out.println("➡️ Payload: " + payload);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            System.out.println("✅ STK Push Response: " + response.getBody());
            return response.getBody();

        } catch (IllegalArgumentException e) {
            System.err.println("⚠️ Validation Error: " + e.getMessage());
            return "❌ Validation Error: " + e.getMessage();

        } catch (Exception e) {
            System.err.println("❌ STK Push Error: " + e.getMessage());
            e.printStackTrace();
            return "❌ Error initiating STK Push: " + e.getMessage();
        }
    }

    /**
     * ✅ Update STK transaction status
     */
    public void updateTransactionStatus(String checkoutRequestId, int resultCode, String resultDesc) {
        Map<String, Object> status = new HashMap<>();
        status.put("resultCode", resultCode);
        status.put("resultDesc", resultDesc);
        transactionStatus.put(checkoutRequestId, status);
        System.out.println("📦 Updated STK transaction status: " + checkoutRequestId + " => " + status);
    }

    /**
     * ✅ Retrieve STK transaction status
     */
    public Map<String, Object> getTransactionStatus(String checkoutRequestId) {
        return transactionStatus.getOrDefault(checkoutRequestId, Map.of(
                "resultCode", -1,
                "resultDesc", "Pending"
        ));
    }
}
