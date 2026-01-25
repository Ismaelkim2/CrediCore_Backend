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

    @Value("${sendgrid.api.key}")
    private String sendGridApiKey;

    public void sendOtpEmail(String toEmail, String otp) throws IOException {
        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalArgumentException("Recipient email is required.");
        }

        Email from = new Email("ismaelkim2@gmail.com"); // must match verified sender
        String subject = "🔐 Credit Management OTP Code";
        Email to = new Email(toEmail);
        Content content = new Content("text/plain", "Your OTP code is: " + otp + "\nValid for 5 minutes.");
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid(sendGridApiKey);
        Request request = new Request();

        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);

            System.out.println("📧 Email sent! Status: " + response.getStatusCode());
            System.out.println("Response body: " + response.getBody());
        } catch (IOException ex) {
            System.out.println("⚠️ SendGrid failed: " + ex.getMessage());
            throw ex;
        }
    }
}
