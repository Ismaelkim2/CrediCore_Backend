package com.example.Credit.management.services;

import com.example.Credit.management.Model.Loan;
import com.example.Credit.management.Repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReminderService {

    private final LoanRepository loanRepository;
    private final SmsService smsService;

    @Scheduled(cron = "0 0 9 * * *")  // Runs daily at 9 AM
    public void sendDefaulterReminders() {
        LocalDate today = LocalDate.now();

        List<Loan> overdueLoans = loanRepository.findByDueDateBeforeAndDueAmountGreaterThan(today, 0.0);

        overdueLoans.forEach(loan -> {
            String message = "Dear " + loan.getApplicantName()
                    + ", your loan is overdue. Your outstanding balance is Ksh "
                    + loan.getDueAmount() + ". Please repay immediately.";

            smsService.sendSms(String.valueOf(loan.getPhoneNumber()), message);
        });
    }
}
