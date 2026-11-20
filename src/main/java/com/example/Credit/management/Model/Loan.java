package com.example.Credit.management.Model;

import com.example.Credit.management.DTO.AccountDTO;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Loan {

    // =====================================================
    // PRIMARY KEY
    // =====================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =====================================================
    // BASIC DETAILS
    // =====================================================

    private String applicantName;

    @Column(name = "phone_number")
    private String phoneNumber;

    private String location;

    @Column(nullable = false)
    private double amount = 0.0;

    private String duration;

    private String status;

    @JsonProperty("account")
    public AccountDTO getAccountDetails() {

        if (account == null) {
            return null;
        }

        AccountDTO dto = new AccountDTO();

        dto.setId(account.getId());
        dto.setNationalId(account.getNationalId());
        dto.setEmail(account.getEmail());
        dto.setMonthlyIncome(account.getMonthlyIncome());
        dto.setMonthlyExpenses(account.getMonthlyExpenses());

        return dto;
    }

    // =====================================================
    // DATES
    // =====================================================

    private LocalDate applicationDate;

    private LocalDate disbursedDate;

    private LocalDate dueDate;

    @Column(name = "last_reminder_sent")
    private LocalDate lastReminderSent;

    // =====================================================
    // FINANCIAL DETAILS
    // =====================================================

    @Column(nullable = false)
    private Double interestRate;

    private Integer riskScore;

    @Column(nullable = false)
    private double penaltyRate = 0.0;

    @Column(nullable = false)
    private String email;

    @Column(name = "total_repayable_amount", nullable = false)
    private Double totalRepayableAmount = 0.0;

    @Column(nullable = false)
    private Double dueAmount = 0.0;

    @Column(name = "application_number")
    private String applicationNumber;

    @Transient
    private Double amountPaid = 0.0;

    // =====================================================
    // REJECTION DETAILS
    // =====================================================

    // =====================================================
// REJECTION DETAILS
// =====================================================

    @Column(length = 500)
    private String rejectionReason;

    @Column(name = "rejected_by_name")
    private String rejectedByName;

    @Column(name = "rejected_by_role")
    private String rejectedByRole;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;


    // =====================================================
// LOAN LIFECYCLE HELPERS
// =====================================================

    @Transient
    public boolean isFundedFacility() {
        return LoanLifecycle.isFundedFacility(this);
    }

    @Transient
    public boolean isUnfundedApplication() {
        return LoanLifecycle.isUnfundedApplication(this);
    }

    @Transient
    public boolean isRejected() {
        return LoanLifecycle.isRejected(this);
    }

    @Transient
    public boolean isBlocked() {
        return LoanLifecycle.isBlocked(this);
    }

    @Transient
    public boolean isCompleted() {
        return LoanLifecycle.isCompleted(this);
    }

    @Transient
    public String getNormalizedStatus() {
        return LoanLifecycle.normalizeStatus(this.status);
    }

    @OneToMany(
            mappedBy = "loan",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @JsonManagedReference
    private List<Repayment> repayments = new ArrayList<>();

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;



    @ManyToOne
    @JoinColumn(name = "staff_id")
    private Staff assignedStaff;

    // =====================================================
    // JSON HELPER METHODS
    // =====================================================

    @JsonProperty("userImageUrl")
    public String getUserImageUrl() {

        return account != null
                ? account.getImageUrl()
                : null;
    }

    @JsonProperty("phoneNumber")
    public String getPhoneNumber() {

        return account != null
                ? account.getPhoneNumber()
                : phoneNumber;
    }

    @JsonProperty("nationalId")
    public String getNationalId() {

        return account != null
                ? account.getNationalId()
                : null;
    }

    @JsonProperty("accountBalance")
    public Double getAccountBalance() {

        return account != null
                ? account.getBalance()
                : 0.0;
    }

    @JsonProperty("location")
    public String getLocation() {

        if (account == null) {
            return location;
        }

        String city =
                account.getCity() != null
                        ? account.getCity()
                        : "";

        String county =
                account.getCounty() != null
                        ? account.getCounty()
                        : "";

        return (city + ", " + county)
                .replaceAll("^,|,$", "")
                .trim();
    }

    @JsonProperty("accountNumber")
    public String getAccountNumber() {

        return account != null
                ? account.getAccountNumber()
                : null;
    }

    @JsonProperty("income")
    public Double getIncome() {

        return account != null
                ? account.getMonthlyIncome()
                : null;
    }

    @JsonProperty("expenses")
    public Double getExpenses() {

        return account != null
                ? account.getMonthlyExpenses()
                : null;
    }


    // =====================================================
    // EXPLICIT SETTERS
    // =====================================================

    public void setApplicantName(String applicantName) {
        this.applicantName = applicantName;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setAmount(Double amount) {

        if (amount != null) {
            this.amount = amount;
        }
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setApplicationDate(LocalDate applicationDate) {
        this.applicationDate = applicationDate;
    }

    public void setDisbursedDate(LocalDate disbursedDate) {
        this.disbursedDate = disbursedDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public void setInterestRate(double interestRate) {
        this.interestRate = interestRate;
    }

    public void setPenaltyRate(double penaltyRate) {
        this.penaltyRate = penaltyRate;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setTotalRepayableAmount(Double totalRepayableAmount) {
        this.totalRepayableAmount = totalRepayableAmount;
    }

    public void setDueAmount(Double dueAmount) {
        this.dueAmount = dueAmount;
    }

    public void setAmountPaid(Double amountPaid) {
        this.amountPaid = amountPaid;
    }

    public void setLastReminderSent(LocalDate lastReminderSent) {
        this.lastReminderSent = lastReminderSent;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public void setRejectedAt(LocalDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public void setRepayments(List<Repayment> repayments) {
        this.repayments = repayments;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public void setAssignedStaff(Staff assignedStaff) {
        this.assignedStaff = assignedStaff;
    }

    public String getRejectedByName() {
        return rejectedByName;
    }

    public void setRejectedByName(String rejectedByName) {
        this.rejectedByName = rejectedByName;
    }

    public String getRejectedByRole() {
        return rejectedByRole;
    }

    public void setRejectedByRole(String rejectedByRole) {
        this.rejectedByRole = rejectedByRole;
    }

    // =====================================================
    // EXPLICIT GETTERS
    // =====================================================

    public Long getId() {
        return id;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public Double getAmount() {
        return amount;
    }

    public String getDuration() {
        return duration;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getApplicationDate() {
        return applicationDate;
    }

    public LocalDate getDisbursedDate() {
        return disbursedDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public double getInterestRate() {
        return interestRate;
    }

    public double getPenaltyRate() {
        return penaltyRate;
    }

    public String getEmail() {
        return email;
    }

    public Double getTotalRepayableAmount() {
        return totalRepayableAmount;
    }

    public Double getDueAmount() {
        return dueAmount;
    }

    public Double getAmountPaid() {
        return amountPaid;
    }

    public LocalDate getLastReminderSent() {
        return lastReminderSent;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }

    public List<Repayment> getRepayments() {
        return repayments;
    }

    public Account getAccount() {
        return account;
    }

    public Staff getAssignedStaff() {
        return assignedStaff;
    }
}