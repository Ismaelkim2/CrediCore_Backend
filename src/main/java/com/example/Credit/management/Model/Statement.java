package com.example.Credit.management.Model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class Statement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nationalId;   // link to Account
    private String description;
    private Double debit;
    private Double credit;
    private Double balanceAfter;
    private LocalDateTime date;
    private String accountNumber;
}
