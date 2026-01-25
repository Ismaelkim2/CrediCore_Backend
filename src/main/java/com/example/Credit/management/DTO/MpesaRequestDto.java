package com.example.Credit.management.DTO;

import lombok.Data;

@Data
public class MpesaRequestDto {
    private String phoneNumber;
    private double amount;
    private String accountNumber;
}
