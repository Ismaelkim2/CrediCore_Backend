
package com.example.Credit.management.services;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${email.enabled:false}")
    private boolean emailEnabled;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public EmailService(
            ObjectProvider<JavaMailSender> mailSenderProvider
    ) {
        this.mailSenderProvider = mailSenderProvider;
    }

    // =========================================================
    // CENTRAL EMAIL DELIVERY
    // =========================================================

    private void deliverEmail(
            String to,
            String subject,
            String body
    ) {

        // Email delivery is optional.
        if (!emailEnabled) {
            System.out.println(
                    "CREDITBANK — EMAIL DISABLED. Skipping email to: " + to
            );
            return;
        }

        JavaMailSender mailSender =
                mailSenderProvider.getIfAvailable();

        if (mailSender == null) {
            System.err.println(
                    "CREDITBANK — EMAIL SERVICE UNAVAILABLE."
            );
            return;
        }

        try {

            SimpleMailMessage message = new SimpleMailMessage();

            message.setFrom(
                    mailUsername != null && !mailUsername.isBlank()
                            ? mailUsername
                            : "noreply@example.com"
            );

            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);

            System.out.println(
                    "CREDITBANK — EMAIL SENT SUCCESSFULLY: " + to
            );

        } catch (MailException e) {

            System.err.println(
                    "CREDITBANK — EMAIL DELIVERY FAILED: " + e.getMessage()
            );

            // Email failure must never interrupt core business operations.
        }
    }

    // =========================================================
    // ACCOUNT CREATED EMAIL
    // =========================================================

    public void sendAccountCreatedEmail(
            String to,
            String fullName,
            String accountNumber
    ) {

        String subject = "Account Created Successfully";

        String body =
                "Dear " + fullName + ",\n\n" +
                        "Your account has been created successfully.\n" +
                        "Account Number: " + accountNumber + "\n\n" +
                        "Thank you for choosing our services.\n\n" +
                        "Best regards,\n" +
                        "Credit Management Team";

        deliverEmail(to, subject, body);
    }

    // =========================================================
    // GENERAL EMAIL
    // =========================================================

    public void sendEmail(
            String to,
            String subject,
            String body
    ) {

        deliverEmail(to, subject, body);
    }

    // =========================================================
    // LOAN REMINDER EMAIL
    // =========================================================

    public void sendLoanReminderEmail(
            String to,
            String borrowerName,
            double dueAmount
    ) {

        String subject = "Loan Payment Reminder";

        String body =
                "Dear " + borrowerName + ",\n\n" +
                        "This is a friendly reminder about your " +
                        "outstanding loan payment.\n" +
                        "Outstanding Amount: Ksh " + dueAmount + "\n\n" +
                        "Kindly make the payment at your earliest convenience.\n\n" +
                        "Best regards,\n" +
                        "Credit Management Team";

        deliverEmail(to, subject, body);
    }
}
