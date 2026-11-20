package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.MpesaRequestDto;
import com.example.Credit.management.DTO.TransferRequest;
import com.example.Credit.management.services.AccountService;
import com.example.Credit.management.services.MpesaStkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/mpesa")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class MpesaController {

    private final MpesaStkService mpesaService;
    private final AccountService accountService;

    // ===============================
    // HEALTH CHECK
    // ===============================
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "MPESA API is running ✅"
        ));
    }

    // ===============================
    // BALANCE CHECK
    // ===============================
    @GetMapping("/balance")
    public ResponseEntity<String> getBalance() {
        return ResponseEntity.ok("Balance endpoint working!");
    }

    // ===============================
    // STK PUSH INITIATION
    // ===============================
    @PostMapping("/stkpush")
    public ResponseEntity<?> initiateStkPush(@RequestBody MpesaRequestDto dto) {

        Map<String, Object> mpesaResponse =
                mpesaService.initiateStkPush(
                        dto.getPhoneNumber(),
                        String.valueOf(dto.getAmount())
                );

        // ❌ HANDLE FAILURE FIRST
        if (mpesaResponse == null || Boolean.TRUE.equals(mpesaResponse.get("error"))) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", mpesaResponse.get("message"),
                    "mpesaResponse", mpesaResponse
            ));
        }

        Object rawId = mpesaResponse.get("CheckoutRequestID");

        if (rawId == null || mpesaResponse.containsKey("error")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "STK failed",
                    "mpesaResponse", mpesaResponse
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "checkoutRequestId", rawId.toString(),
                "mpesaResponse", mpesaResponse
        ));
    }
    // ===============================
    // CHECK STATUS
    // ===============================
    @GetMapping("/status/{checkoutRequestId}")
    public ResponseEntity<?> getStkStatus(@PathVariable String checkoutRequestId) {

        return ResponseEntity.ok(
                mpesaService.getTransactionStatus(checkoutRequestId)
        );
    }

    // ===============================
    // TRANSFER (MPESA / INTERNAL)
    // ===============================
    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@RequestBody TransferRequest request) {

        try {

            switch (request.getType().toLowerCase()) {

                case "mpesa" -> {

                    Map<String, Object> response =
                            mpesaService.initiateStkPush(
                                    request.getToAccount(),
                                    String.valueOf(request.getAmount())
                            );

                    return ResponseEntity.ok(Map.of(
                            "message", "M-Pesa transfer initiated successfully",
                            "response", response
                    ));
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
                    return ResponseEntity.ok(Map.of(
                            "message", "Interbank transfer request received (pending confirmation)"
                    ));
                }

                default -> {
                    return ResponseEntity.badRequest().body(Map.of(
                            "message", "Invalid transfer type"
                    ));
                }
            }

        } catch (Exception e) {

            return ResponseEntity.internalServerError().body(Map.of(
                    "message", "Error processing transfer",
                    "details", e.getMessage()
            ));
        }
    }
}