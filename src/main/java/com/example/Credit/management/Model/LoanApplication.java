package com.example.Credit.management.Model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Data
public class LoanApplication {
    @Id
    @GeneratedValue
    private Long id;
    private String applicantName;
    private Double amount;
    private LocalDate applicationDate;
    private String status;
}
