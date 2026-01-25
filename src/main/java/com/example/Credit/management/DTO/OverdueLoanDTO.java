package com.example.Credit.management.DTO;
import java.time.LocalDate;

public class OverdueLoanDTO {
    private Long loanId;
    private String customer;
    private Double amount;
    private LocalDate dueDate;

    public OverdueLoanDTO(Long loanId, String customer, Double amount, LocalDate dueDate) {
        this.loanId = loanId;
        this.customer = customer;
        this.amount = amount;
        this.dueDate = dueDate;
    }

    // Getters
    public Long getLoanId() { return loanId; }
    public String getCustomer() { return customer; }
    public Double getAmount() { return amount; }
    public LocalDate getDueDate() { return dueDate; }
}
