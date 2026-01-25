package com.example.Credit.management.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    // Existing method - NOT touched
    public void sendAccountCreatedEmail(String to, String fullName, String accountNumber) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("ismaelkim2@gmail.com");
        message.setTo(to);
        message.setSubject("✅ Account Created Successfully");
        message.setText("Dear " + fullName + ",\n\n" +
                "Your account has been created successfully.\n" +
                "Account Number: " + accountNumber + "\n\n" +
                "Thank you for choosing our services.\n\n" +
                "Best regards,\nCredit Management Team");

        mailSender.send(message);
    }

    // Existing method - NOT touched
    public void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("ismaelkim2@gmail.com");
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    // ===========================================
    // ✅ NEW METHOD — Loan Reminder Email
    // ===========================================
    public void sendLoanReminderEmail(String to, String borrowerName, double dueAmount) {
        String subject = "⏰ Loan Payment Reminder";
        String body = "Dear " + borrowerName + ",\n\n" +
                "This is a friendly reminder about your outstanding loan payment.\n" +
                "Outstanding Amount: Ksh " + dueAmount + "\n\n" +
                "Kindly make the payment at your earliest convenience.\n\n" +
                "Best regards,\nCredit Management Team";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("ismaelkim2@gmail.com");
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);

        System.out.println("📧 Loan reminder email sent to: " + to);
    }
}
