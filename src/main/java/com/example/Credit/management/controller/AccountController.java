package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.AccountDTO;
import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.User;
import com.example.Credit.management.services.AccountService;
import com.example.Credit.management.services.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/accounts")
@CrossOrigin(origins = "*")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AuthService authService;

    @GetMapping("/search/{nationalId}")
    public ResponseEntity<Account> searchByNationalId(@PathVariable String nationalId) {
        Account account = accountService.getByNationalId(nationalId);
        if (account == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(account);
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> createAccount(
            @RequestParam("fullName") String fullName,
            @RequestParam("nationalId") String nationalId,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam("email") String email,
            @RequestParam(value = "image", required = false) MultipartFile image
    ) throws IOException {
        // Check for existing account by nationalId, email, or phone
        Optional<Account> existing = accountService.getAllAccounts().stream()
                .filter(a -> a.getNationalId().equals(nationalId) ||
                        a.getEmail().equalsIgnoreCase(email) ||
                        a.getPhoneNumber().equals(phoneNumber))
                .findFirst();

        if (existing.isPresent()) {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("message", "Account already exists");
            errorBody.put("existingAccount", accountService.toDTO(existing.get()));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody);
        }

        Account account = new Account();
        account.setFullName(fullName);
        account.setNationalId(nationalId);
        account.setPhoneNumber(phoneNumber);
        account.setEmail(email);

        if (image != null && !image.isEmpty()) {
            String uploadDir = "uploads/";
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            String fileName = System.currentTimeMillis() + "_" + image.getOriginalFilename();
            Path filePath = Paths.get(uploadDir, fileName);
            Files.write(filePath, image.getBytes());
            account.setImageUrl(filePath.toString().replace("\\", "/"));
        }

        Account created = accountService.createAccount(account);
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.toDTO(created));
    }


    // ✅ Use toDTO so netBalance is included
    @GetMapping("/by-id/{nationalId}")
    public ResponseEntity<AccountDTO> getByNationalId(@PathVariable String nationalId) {
        Account account = accountService.getByNationalId(nationalId);
        AccountDTO dto = accountService.toDTO(account); // ✅ converts to DTO including netBalance
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    public ResponseEntity<List<AccountDTO>> getAllAccounts() {
        List<Account> accounts = accountService.getAllAccounts();
        List<AccountDTO> dtoList = accounts.stream()
                .map(accountService::toDTO)
                .toList();
        return ResponseEntity.ok(dtoList);
    }


    @DeleteMapping("/{accountNumber}")
    public ResponseEntity<?> deleteAccount(@PathVariable String accountNumber) {
        accountService.deleteAccountByAccountNumber(accountNumber); // implement this in service
        return ResponseEntity.ok().build();
    }




    @GetMapping("/image/{nationalId}")
    public ResponseEntity<Resource> getImage(@PathVariable String nationalId) throws IOException {
        Account account = accountService.getByNationalId(nationalId);
        if (account.getImageUrl() == null) {
            return ResponseEntity.notFound().build();
        }

        Path path = Paths.get(account.getImageUrl());
        Resource resource = new UrlResource(path.toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }

    @PostMapping("/deposit")
    public ResponseEntity<AccountDTO> deposit(@RequestBody Map<String, Object> dto) {
        String accountNumber = (String) dto.get("accountNumber");
        Double amount = Double.valueOf(dto.get("amount").toString());

        Account account = accountService.getByAccountNumber(accountNumber);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // ✅ Update only real balance
        account.setBalance(account.getBalance() + amount);
        accountService.save(account);

        // Record transaction (for statements)
        Map<String, Object> txn = Map.of(
                "date", LocalDateTime.now().toString(),
                "description", "Deposit",
                "credit", amount,
                "debit", 0,
                "balanceAfter", account.getBalance()
        );

        List<Map<String, Object>> existingStatements = accountService.getStatements(accountNumber);
        if (existingStatements == null) existingStatements = new ArrayList<>();
        existingStatements.add(txn);
        accountService.saveStatements(accountNumber, existingStatements);

        // ✅ Generate DTO for frontend (net balance shown but not saved)
        AccountDTO updatedDTO = accountService.toDTO(account);
        return ResponseEntity.ok(updatedDTO);
    }


    @PutMapping("/updateProfile")
    public ResponseEntity<?> updateProfile(@RequestBody User userRequest) {
        try {
            User updatedUser = authService.updateUserDetails(userRequest);
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Failed to update profile", "message", e.getMessage()));
        }
    }
}
