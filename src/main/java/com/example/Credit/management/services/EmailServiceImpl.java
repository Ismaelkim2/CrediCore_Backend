package com.example.Credit.management.services;

import com.example.Credit.management.DTO.EmailRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements MailService {

    private final JavaMailSender mailSender;

    @Override
    public void sendEmail(EmailRequestDTO request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(request.getTo());
        message.setSubject(request.getSubject());
        message.setText(request.getMessage());
        message.setFrom("no-reply@yourdomain.com");

        mailSender.send(message);
    }
}
