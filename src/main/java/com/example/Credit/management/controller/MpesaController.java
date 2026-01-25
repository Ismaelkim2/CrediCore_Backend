package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.MpesaRequestDto;
import com.example.Credit.management.DTO.TransferRequest;
import com.example.Credit.management.services.AccountService;
import com.example.Credit.management.services.MpesaStkService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;


@RestController
@RequestMapping("/api/mpesa")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class MpesaController {

    private final MpesaStkService mpesaService;
    private final AccountService accountService;

    @GetMapping("/balance")
    public ResponseEntity<String> getBalance() {
        // call your Mpesa service logic here
        return ResponseEntity.ok("Balance endpoint working!");
    }

    /**
     * ✅ Initiates an M-Pesa STK Push (CustomerPayBillOnline)
     */
    @PostMapping("/stkpush")
    public ResponseEntity<?> initiateStkPush(@RequestBody MpesaRequestDto dto) {
        try {
            String response = mpesaService.initiateStkPush(dto.getPhoneNumber(), String.valueOf(dto.getAmount()));

            // Convert JSON string to Map for easy access
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> json = mapper.readValue(response, Map.class);

            String checkoutRequestId = (String) json.get("CheckoutRequestID");

            Map<String, Object> res = new HashMap<>();
            res.put("status", "success");
            res.put("message", "STK Push initiated successfully");
            res.put("checkoutRequestId", checkoutRequestId);
            res.put("mpesaResponse", json);

            return ResponseEntity.ok(res);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Failed to initiate STK Push");
            error.put("details", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }


    @GetMapping("/status/{checkoutRequestId}")
    public ResponseEntity<?> getStkStatus(@PathVariable String checkoutRequestId) {
        Map<String, Object> status = mpesaService.getTransactionStatus(checkoutRequestId);
        return ResponseEntity.ok(status);
    }


    /**
     * ✅ Safaricom will POST the transaction result here
     *    after the customer responds to the STK prompt.
     */
    @PostMapping("/callback")
    public ResponseEntity<?> stkCallback(@RequestBody Map<String, Object> callbackData) {
        System.out.println("📩 STK Callback received: " + callbackData);

        try {
            Map<String, Object> body = (Map<String, Object>) callbackData.get("Body");
            Map<String, Object> stkCallback = (Map<String, Object>) body.get("stkCallback");

            String checkoutRequestId = (String) stkCallback.get("CheckoutRequestID");
            int resultCode = (int) stkCallback.get("ResultCode");
            String resultDesc = (String) stkCallback.get("ResultDesc");

            // ✅ Update transaction status in memory
            mpesaService.updateTransactionStatus(checkoutRequestId, resultCode, resultDesc);

            String message;
            if (resultCode == 0) {
                message = "✅ Payment successful!";
            } else if (resultCode == 1032) {
                message = "❌ Payment cancelled by user.";
            } else if (resultCode == 2001) {
                message = "⚠️ Insufficient balance.";
            } else {
                message = "⚠️ Payment failed: " + resultDesc;
            }

            // ✅ If success, credit user account
            if (resultCode == 0 && stkCallback.containsKey("CallbackMetadata")) {
                Map<String, Object> metadata = (Map<String, Object>) stkCallback.get("CallbackMetadata");
                List<Map<String, Object>> items = (List<Map<String, Object>>) metadata.get("Item");

                String amount = null;
                for (Map<String, Object> item : items) {
                    if ("Amount".equals(item.get("Name"))) {
                        amount = item.get("Value").toString();
                        break;
                    }
                }

                // 🔹 Replace with actual user lookup logic
                String phoneNumber = "07XXXXXXXX";
                mpesaService.creditUserAccount(phoneNumber, Double.parseDouble(amount));
            }

            // ✅ Return clear JSON for frontend
            return ResponseEntity.ok(Map.of(
                    "status", resultCode == 0 ? "success" : "failed",
                    "message", message,
                    "checkoutRequestId", checkoutRequestId
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }


    /**
     * ✅ Optional test endpoint to check server is running
     */
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of("status", "MPESA API is running ✅"));
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@RequestBody TransferRequest request) {
        try {
            switch (request.getType().toLowerCase()) {
                case "mpesa" -> {
                    MpesaRequestDto dto = new MpesaRequestDto();
                    dto.setPhoneNumber(request.getToAccount());
                    dto.setAmount(request.getAmount());
                    String response = mpesaService.initiateStkPush(dto.getPhoneNumber(), String.valueOf(dto.getAmount()));
                    return ResponseEntity.ok(Map.of("message", "M-Pesa transfer initiated successfully", "response", response));
                }
                case "intrabank" -> {
                    boolean success = accountService.transferFunds(
                            request.getFromAccount(),
                            request.getToAccount(),
                            request.getAmount()
                    );
                    return success
                            ? ResponseEntity.ok(Map.of("message", "Intra-bank transfer successful"))
                            : ResponseEntity.badRequest().body(Map.of("message", "Insufficient funds or invalid account"));
                }
                case "otherbank" -> {
                    // Simulate sending to another bank
                    return ResponseEntity.ok(Map.of("message", "Interbank transfer request received (pending confirmation)"));
                }
                default -> {
                    return ResponseEntity.badRequest().body(Map.of("message", "Invalid transfer type"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Error processing transfer", "details", e.getMessage()));
        }
    }

}
