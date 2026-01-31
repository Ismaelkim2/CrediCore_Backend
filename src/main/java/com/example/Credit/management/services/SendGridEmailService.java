package com.example.Credit.management.services;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class SendGridEmailService {

    @Value("${sendgrid.api.key:}")
    private String sendGridApiKey;

    public void sendOtpEmail(String toEmail, String otp) throws IOException {

        // 🔴 Skip sending if key is missing
        if (sendGridApiKey == null || sendGridApiKey.isBlank()) {
            System.out.println("⚠️ SendGrid disabled: API key not configured");
            return;
        }

        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalArgumentException("Recipient email is required.");
        }

        Email from = new Email("ismaelkim2@gmail.com");
        String subject = "🔐 Credit Management OTP Code";
        Email to = new Email(toEmail);
        Content content = new Content(
                "text/plain",
                "Your OTP code is: " + otp + "\nValid for 5 minutes."
        );

        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid(sendGridApiKey);
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        sg.api(request);
        System.out.println("📧 Email sent successfully");
    }
}
