package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.EmailRequestDTO;
import com.example.Credit.management.services.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EmailController {

    private final MailService mailService;

    @PostMapping("/send")
    public ResponseEntity<?> sendEmail(@RequestBody EmailRequestDTO request) {
        mailService.sendEmail(request);
        return ResponseEntity.ok("Email sent successfully");
    }
}
