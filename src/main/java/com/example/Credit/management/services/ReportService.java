package com.example.Credit.management.services;

import com.example.Credit.management.DTO.LoanReportDTO;
import com.example.Credit.management.Repository.LoanRepository;
import com.example.Credit.management.Repository.ReportRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportService {

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private ReportRepo reportRepo;

    public List<Map<String, Object>> getReport(String type, LocalDate startDate, LocalDate endDate) {
        switch (type) {
            case "loanApplications":
                return loanRepository.findApplicationsBetweenDates(startDate, endDate);

            case "disbursedLoans":
                return loanRepository.findDisbursedLoans(startDate, endDate);

            case "overdues":
                return loanRepository.findOverdueLoans(LocalDate.now());

            case "revenue":
                return loanRepository.calculateRevenue();

            case "approvedLoans":
                return loanRepository.findByStatusBetweenDates("approved", startDate, endDate);

            case "rejectedLoans":
                return loanRepository.findByStatusBetweenDates("rejected", startDate, endDate);

            case "repayments":
                List<LoanReportDTO> repayments = reportRepo.findRawRepayments();

                return repayments.stream()
                        .peek(LoanReportDTO::computeRepaymentStatus)
                        .map(dto -> {
                            Map<String, Object> map = new java.util.HashMap<>();
                            map.put("id", dto.getId());
                            map.put("applicantName", dto.getApplicantName());
                            map.put("phoneNumber", dto.getPhoneNumber());
                            map.put("amount", dto.getAmount());
                            map.put("amountPaid", dto.getAmountPaid());
                            map.put("totalRepayableAmount", dto.getTotalRepayableAmount());
                            map.put("status", dto.getStatus());
                            return map;
                        })
                        .collect(Collectors.toList());


            default:
                return List.of(); // Empty list if type is unknown
        }
    }
}
