package com.example.Credit.management.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class RepaymentTransactionDTO {

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime date;

        private double amount;
        private String method;

        // Constructor matching your JPQL query
        public RepaymentTransactionDTO(LocalDate date, double amount, String method) {
                this.date = date.atStartOfDay();
                this.amount = amount;
                this.method = method;
        }

        // Getters and setters
        public LocalDateTime getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date.atStartOfDay(); }

        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
}
