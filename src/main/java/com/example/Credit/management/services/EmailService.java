package com.example.Credit.management.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "email.enabled",
        havingValue = "true"
)
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;


    // =========================================================
    // ACCOUNT CREATED EMAIL
    // =========================================================

    public void sendAccountCreatedEmail(
            String to,
            String fullName,
            String accountNumber
    ) {

        try {

            SimpleMailMessage message = new SimpleMailMessage();

            message.setFrom("ismaelkim2@gmail.com");
            message.setTo(to);
            message.setSubject("Account Created Successfully");

            message.setText(
                    "Dear " + fullName + ",\n\n" +
                            "Your account has been created successfully.\n" +
                            "Account Number: " + accountNumber + "\n\n" +
                            "Thank you for choosing our services.\n\n" +
                            "Best regards,\n" +
                            "Credit Management Team"
            );

            mailSender.send(message);

            System.out.println(
                    "CREDITBANK — ACCOUNT EMAIL SENT: " + to
            );

        } catch (MailException e) {

            // =================================================
            // IMPORTANT:
            // EMAIL FAILURE MUST NEVER CANCEL ACCOUNT CREATION
            // =================================================

            System.err.println(
                    "CREDITBANK — ACCOUNT EMAIL FAILED"
            );

            System.err.println(
                    "Recipient: " + to
            );

            System.err.println(
                    "Reason: " + e.getMessage()
            );

            // DO NOT THROW THE EXCEPTION
        }
    }


    // =========================================================
    // GENERAL EMAIL
    // =========================================================

    public void sendEmail(
            String to,
            String subject,
            String body
    ) {

        try {

            SimpleMailMessage message = new SimpleMailMessage();

            message.setFrom("ismaelkim2@gmail.com");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);

            System.out.println(
                    "CREDITBANK — EMAIL SENT SUCCESSFULLY"
            );

            System.out.println(
                    "Recipient: " + to
            );

        } catch (MailException e) {

            // =================================================
            // CRITICAL:
            // EMAIL FAILURE MUST NOT FAIL THE LOAN APPLICATION
            // =================================================

            System.err.println(
                    "=========================================="
            );

            System.err.println(
                    "CREDITBANK — EMAIL DELIVERY FAILED"
            );

            System.err.println(
                    "Recipient: " + to
            );

            System.err.println(
                    "Subject: " + subject
            );

            System.err.println(
                    "Reason: " + e.getMessage()
            );

            System.err.println(
                    "Loan/application processing will CONTINUE."
            );

            System.err.println(
                    "=========================================="
            );

            // DO NOT THROW THE EXCEPTION
        }
    }


    // =========================================================
    // LOAN REMINDER EMAIL
    // =========================================================

    public void sendLoanReminderEmail(
            String to,
            String borrowerName,
            double dueAmount
    ) {

        try {

            String subject = "Loan Payment Reminder";

            String body =
                    "Dear " + borrowerName + ",\n\n" +
                            "This is a friendly reminder about your " +
                            "outstanding loan payment.\n" +
                            "Outstanding Amount: Ksh " + dueAmount + "\n\n" +
                            "Kindly make the payment at your earliest convenience.\n\n" +
                            "Best regards,\n" +
                            "Credit Management Team";


            SimpleMailMessage message =
                    new SimpleMailMessage();

            message.setFrom("ismaelkim2@gmail.com");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);

            System.out.println(
                    "CREDITBANK — LOAN REMINDER SENT: " + to
            );

        } catch (MailException e) {

            // =================================================
            // REMINDER FAILURE MUST NOT BREAK OTHER OPERATIONS
            // =================================================

            System.err.println(
                    "CREDITBANK — LOAN REMINDER EMAIL FAILED"
            );

            System.err.println(
                    "Recipient: " + to
            );

            System.err.println(
                    "Reason: " + e.getMessage()
            );

            // DO NOT THROW THE EXCEPTION
        }
    }
}