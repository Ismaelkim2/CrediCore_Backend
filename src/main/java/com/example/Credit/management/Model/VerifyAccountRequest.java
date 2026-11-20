package com.example.Credit.management.Model;

import lombok.Data;

@Data
public class VerifyAccountRequest {
    private String email;
    private String accountNumber;
}
