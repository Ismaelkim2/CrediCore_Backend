package com.example.Credit.management.services;

import com.example.Credit.management.Model.Loan;
import com.example.Credit.management.Repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanReminderScheduler {

    private final LoanRepository loanRepository;
    private final EmailService emailService;
    private final SmsService smsService;
    private final NotificationService notificationService;

    /**
     * Send reminder 3 days before due date
     * Runs every day at 8:00 AM
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void sendDueReminders() {

        LocalDate reminderDate = LocalDate.now().plusDays(3);

        List<Loan> loans = loanRepository.findByDueDate(reminderDate);

        for (Loan loan : loans) {

            try {

                if (!"APPROVED".equalsIgnoreCase(loan.getStatus())
                        && !"PARTIAL".equalsIgnoreCase(loan.getStatus())) {
                    continue;
                }

                if (loan.getLastReminderSent() != null
                        && loan.getLastReminderSent().equals(LocalDate.now())) {
                    continue;
                }

                String message =
                        "Loan Repayment Reminder\n\n"

                                + "Dear " + loan.getApplicantName() + ",\n\n"

                                + "This is a friendly reminder that your loan repayment is approaching.\n\n"

                                + "Loan Amount: KES " + String.format("%,.2f", loan.getAmount()) + "\n"
                                + "Outstanding Balance: KES " + String.format("%,.2f", loan.getDueAmount()) + "\n"
                                + "Due Date: " + loan.getDueDate() + "\n"
                                + "Loan Status: " + loan.getStatus() + "\n\n"

                                + "Kindly ensure sufficient funds are available or make your repayment before the due date to avoid late payment penalties and maintain a positive credit history.\n\n"

                                + "Thank you for choosing CrediCore Intelligent Lending Platform.";

                notificationService.createNotification(
                        loan.getAccount(),
                        message,
                        "Loan Repayment Reminder"
                );

                smsService.sendSms(
                        loan.getPhoneNumber(),
                        message
                );

                if (loan.getEmail() != null && !loan.getEmail().isBlank()) {
                    emailService.sendEmail(
                            loan.getEmail(),
                            "Loan Repayment Reminder - Payment Due in 3 Days",
                            message
                    );
                }

                loan.setLastReminderSent(LocalDate.now());
                loanRepository.save(loan);

            } catch (Exception ex) {
                log.error("Reminder failed for loan {}", loan.getId(), ex);
            }
        }
    }

    /**
     * Notify overdue loans
     * Runs every day at 9:00 AM
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void notifyOverdueLoans() {

        List<Loan> overdueLoans =
                loanRepository.findByDueDateBeforeAndStatus(
                        LocalDate.now(),
                        "APPROVED"
                );

        for (Loan loan : overdueLoans) {

            try {

                String message =
                        "Loan Repayment Overdue\n\n"

                                + "Dear " + loan.getApplicantName() + ",\n\n"

                                + "Our records indicate that your loan repayment is now overdue.\n\n"

                                + "Loan Amount: KES " + String.format("%,.2f", loan.getAmount()) + "\n"
                                + "Outstanding Balance: KES " + String.format("%,.2f", loan.getDueAmount()) + "\n"
                                + "Due Date: " + loan.getDueDate() + "\n\n"

                                + "Your account continues to accrue applicable penalty charges in accordance with your loan agreement.\n\n"

                                + "To avoid additional charges and protect your credit profile, kindly settle the outstanding balance as soon as possible.\n\n"

                                + "If you have already made payment, please disregard this notice or contact CrediCore Customer Support for assistance.\n\n"

                                + "CrediCore Intelligent Lending Platform";

                notificationService.createNotification(
                        loan.getAccount(),
                        message,
                        "Loan Repayment Overdue"
                );

                smsService.sendSms(loan.getPhoneNumber(), message);

                if (loan.getEmail() != null && !loan.getEmail().isBlank()) {
                    emailService.sendEmail(
                            loan.getEmail(),
                            "Important: Your Loan Repayment is Overdue",
                            message
                    );
                }

            } catch (Exception ex) {
                log.error("Overdue notification failed {}", loan.getId(), ex);
            }
        }
    }
}