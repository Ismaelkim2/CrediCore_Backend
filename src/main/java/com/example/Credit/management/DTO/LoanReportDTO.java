package com.example.Credit.management.DTO;

public class LoanReportDTO {

    private Long id;
    private String applicantName;
    private String nationalId;
    private Double amount;
    private Double amountPaid;
    private Double totalRepayableAmount;
    private String status;
    private String phoneNumber;

    public LoanReportDTO(Long id,
                         String applicantName,
                         String nationalId,
                         Double amount,
                         Double amountPaid,
                         Double totalRepayableAmount,
                         String status,
                         String phoneNumber) {

        this.id = id;
        this.applicantName = applicantName;
        this.nationalId = nationalId;
        this.amount = amount;
        this.amountPaid = amountPaid;
        this.totalRepayableAmount = totalRepayableAmount;
        this.status = status;
        this.phoneNumber = phoneNumber;
    }

    public void computeRepaymentStatus() {

        if (amountPaid == null || totalRepayableAmount == null) {
            status = "Unknown";
        }
        else if (amountPaid >= totalRepayableAmount) {
            status = "Repaid";
        }
        else if (amountPaid > 0) {
            status = "Partially Paid";
        }
        else {
            status = "Unpaid";
        }
    }

    public Long getId() { return id; }
    public String getApplicantName() { return applicantName; }
    public String getNationalId() { return nationalId; }
    public Double getAmount() { return amount; }
    public Double getAmountPaid() { return amountPaid; }
    public Double getTotalRepayableAmount() { return totalRepayableAmount; }
    public String getStatus() { return status; }
    public String getPhoneNumber() { return phoneNumber; }

    public void setStatus(String status) { this.status = status; }
    public void setAmountPaid(Double amountPaid) { this.amountPaid = amountPaid; }
    public void setTotalRepayableAmount(Double totalRepayableAmount) { this.totalRepayableAmount = totalRepayableAmount; }
}