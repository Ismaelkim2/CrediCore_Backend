package com.example.Credit.management.Model;

import lombok.Data;

@Data
public class TransferRequest {
    private String fromAccount;
    private String toAccount;
    private double amount;
    private String type;
}