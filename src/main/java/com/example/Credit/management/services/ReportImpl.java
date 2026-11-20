package com.example.Credit.management.services;

import com.example.Credit.management.DTO.LoanReportDTO;
import com.example.Credit.management.Model.Loan;
import com.example.Credit.management.Model.Repayment;
import com.example.Credit.management.Repository.LoanApplicationRepository;
import com.example.Credit.management.Repository.LoanRepository;
import com.example.Credit.management.Repository.RepaymentRepository;
import com.example.Credit.management.Repository.ReportRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class ReportImpl extends ReportService {

    @Autowired
    private LoanApplicationRepository applicationRepo;

    @Autowired
    private LoanRepository loanRepo;

    @Autowired
    private RepaymentRepository repaymentRepo;

    @Autowired
    private ReportRepo reportRepo;

    @Override
    public List<Map<String, Object>> getReport(String type, LocalDate startDate, LocalDate endDate) {
        switch (type.toLowerCase()) {
            case "loanapplications":
                return applicationRepo.findByApplicationDateBetween(startDate, endDate).stream()
                        .map(app -> Map.<String, Object>of(
                                "id", app.getId(),
                                "applicantName", app.getApplicantName(),
                                "amount", app.getAmount(),
                                "applicationDate", app.getApplicationDate(),
                                "status", app.getStatus()
                        )).toList();

            case "approvedloans":
                return applicationRepo.findByStatusAndApplicationDateBetween("APPROVED", startDate, endDate).stream()
                        .map(app -> Map.<String, Object>of(
                                "id", app.getId(),
                                "applicantName", app.getApplicantName(),
                                "amount", app.getAmount(),
                                "applicationDate", app.getApplicationDate()
                        )).toList();

            case "rejectedloans":
                return applicationRepo.findByStatusAndApplicationDateBetween("REJECTED", startDate, endDate).stream()
                        .map(app -> Map.<String, Object>of(
                                "id", app.getId(),
                                "applicantName", app.getApplicantName(),
                                "amount", app.getAmount(),
                                "applicationDate", app.getApplicationDate()
                        )).toList();

            case "disbursedloans":
                return loanRepo.findByDisbursedDateBetween(startDate, endDate).stream()
                        .map(loan -> Map.<String, Object>of(
                                "id", loan.getId(),
                                "nationalId", loan.getAccount() != null ? loan.getAccount().getNationalId() : null,
                                "applicantName", loan.getApplicantName(),
                                "location", loan.getLocation(),
                                "amount", loan.getAmount(),
                                "disbursedDate", loan.getDisbursedDate(),
                                "status", loan.getStatus()
                        )).toList();

            case "repayments":
                List<LoanReportDTO> rawList = reportRepo.findRawRepayments();

                // Log how many repayment records were fetched
                System.out.println(">> [Repayments] Raw records fetched: " + rawList.size());

                rawList.forEach(dto -> System.out.println(">> Repayment: " +
                        dto.getApplicantName() + ", Paid: " + dto.getAmountPaid() +
                        ", Total Repayable: " + dto.getTotalRepayableAmount())
                );

                List<Map<String, Object>> filtered = rawList.stream()
                        .filter(dto -> {
                            Loan loan = loanRepo.findById(dto.getId()).orElse(null);

                            if (loan == null) {
                                System.out.println(">> Skipping loan ID: " + dto.getId() + " (loan is null)");
                                return false;
                            }

                            // If disbursedDate is null, still include if it has repayments
                            if (loan.getDisbursedDate() == null) {
                                System.out.println(">> Including loan ID: " + dto.getId() + " (no disbursedDate but has repayment)");
                                return true;
                            }

                            LocalDate disbursed = loan.getDisbursedDate();
                            boolean include = (startDate == null || !disbursed.isBefore(startDate)) &&
                                    (endDate == null || !disbursed.isAfter(endDate));

                            System.out.println("Loan ID: " + dto.getId() + ", disbursedDate: " + disbursed + ", include: " + include);
                            return include;
                        })

                        .peek(LoanReportDTO::computeRepaymentStatus)
                        .map(dto -> Map.<String, Object>of(
                                "applicantName", dto.getApplicantName(),
                                "phoneNumber", dto.getPhoneNumber(),
                                "amount", dto.getAmountPaid(),
                                "totalRepayableAmount", dto.getTotalRepayableAmount(),
                                "status", dto.getStatus()
                        ))
                        .toList();

                System.out.println(">> [Repayments] Filtered records to be returned: " + filtered.size());

                return filtered;


            case "overdues":
                LocalDate today = LocalDate.now();
                return loanRepo.findOverdueLoans(today);

            case "revenue":
                double totalRepayment = repaymentRepo.findByRepaymentDateBetween(startDate, endDate).stream()
                        .map(Repayment::getAmountPaid)
                        .filter(amount -> amount != null)
                        .mapToDouble(Double::doubleValue)
                        .sum();
                return List.of(Map.of("totalRevenue", totalRepayment));

            default:
                return List.of(Map.of("error", "Invalid report type"));
        }
    }
}
