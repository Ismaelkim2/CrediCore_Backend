package com.example.Credit.management.services;

import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    @Value("${twilio.phone.number}")
    private String twilioNumber;

    // ✅ Auto-format phone number to +254 format
    private String normalizePhone(String phone) {

        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number is empty");
        }

        // Remove spaces and dashes
        phone = phone.replaceAll("[^0-9]", "");

        // Case 1: Number starts with 0 --> convert to +2547...
        if (phone.startsWith("0")) {
            return "+254" + phone.substring(1);
        }

        // Case 2: Number starts with 7... --> convert to +2547...
        if (phone.startsWith("7")) {
            return "+254" + phone;
        }

        // Case 3: Already in correct format
        if (phone.startsWith("254")) {
            return "+" + phone;
        }

        // Case 4: Already international
        if (phone.startsWith("+254")) {
            return phone;
        }

        throw new IllegalArgumentException("Invalid Kenyan phone number: " + phone);
    }

    public void sendSms(String phone, String messageText) {
        try {
            String formattedPhone = normalizePhone(phone);

            Message message = Message.creator(
                    new PhoneNumber(formattedPhone),      // Correct formatted number
                    new PhoneNumber(twilioNumber),        // Your Twilio number
                    messageText
            ).create();

            System.out.println("✅ SMS sent! SID: " + message.getSid());

        } catch (Exception e) {
            System.err.println("❌ Failed to send SMS: " + e.getMessage());
            throw new RuntimeException("Failed to send SMS", e);
        }
    }
}
