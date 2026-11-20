package com.example.Credit.management.Model;

import lombok.Data;

@Data
public class ForgotPasswordRequest {
    private String email;
    private String accountNumber;
    private String newPassword;
}
