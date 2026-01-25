package com.example.Credit.management.SecConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthResponse {
    private String token;
    private String message;
    private String name;
    private String role;
    private String email;
    private String nationalId;
    private String userImageUrl;
    private String phoneNumber;

    // Single-arg constructor
    public AuthResponse(String message) {
        this.message = message;
    }

    // Optional: add message + email constructor if needed
    public AuthResponse(String message, String email) {
        this.message = message;
        this.email = email;
    }
}
