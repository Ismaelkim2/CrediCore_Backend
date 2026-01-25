package com.example.Credit.management.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDate;

@Data
public class RepaymentDTO {
    private Long loanId;
    private Double amountPaid;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate repaymentDate;

    private String idNo;
    private String phoneNumber;
    private String location;
    private String accountNumber;
}
