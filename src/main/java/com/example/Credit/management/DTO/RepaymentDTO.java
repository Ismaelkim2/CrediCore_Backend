package com.example.Credit.management.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDate;

@Data
public class RepaymentDTO {
    private Long loanId;
    private Double amountPaid;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")

    private Long id;
    private LocalDate repaymentDate;

    private String idNo;
    private String phoneNumber;
    private String location;
    private String accountNumber;
    private String paymentMethod;

    private String applicantName;    // from Loan
    private Double remainingDue;

    private String paymentReference;

    private String mpesaReceiptNumber;
// use @Data or explicit getters

}
