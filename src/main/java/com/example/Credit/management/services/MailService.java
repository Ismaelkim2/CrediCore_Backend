package com.example.Credit.management.services;

import com.example.Credit.management.DTO.EmailRequestDTO;

public interface MailService {
    void sendEmail(EmailRequestDTO request);
}//