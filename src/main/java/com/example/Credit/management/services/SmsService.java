package com.example.Credit.management.services;

import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    @Value("${twilio.phone.number}")
    private String twilioNumber;

    /**
     * Normalize Kenyan phone numbers to +254 format.
     *
     * IMPORTANT:
     * This method is allowed to throw internally, but sendSms()
     * catches the exception so SMS problems NEVER block loan processing.
     */
    private String normalizePhone(String phone) {

        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number is empty");
        }

        // Remove spaces, dashes, brackets, and other non-numeric characters
        phone = phone.replaceAll("[^0-9]", "");

        // 0712345678 -> +254712345678
        if (phone.startsWith("0")) {
            return "+254" + phone.substring(1);
        }

        // 712345678 -> +254712345678
        if (phone.startsWith("7")) {
            return "+254" + phone;
        }

        // 254712345678 -> +254712345678
        if (phone.startsWith("254")) {
            return "+" + phone;
        }

        throw new IllegalArgumentException(
                "Invalid Kenyan phone number: " + phone
        );
    }

    /**
     * Sends an SMS notification.
     *
     * IMPORTANT:
     * SMS is a notification only.
     *
     * A failed SMS MUST NOT:
     * - cancel a loan application
     * - rollback a loan
     * - return HTTP 500
     * - prevent the success report from displaying
     * - prevent receipt generation
     */
    public void sendSms(String phone, String messageText) {

        try {

            // Validate recipient
            String formattedPhone = normalizePhone(phone);

            // Validate Twilio sender
            if (twilioNumber == null || twilioNumber.trim().isEmpty()) {

                System.err.println("==========================================");
                System.err.println("CREDITBANK — SMS DELIVERY FAILED");
                System.err.println("Recipient: " + formattedPhone);
                System.err.println("Reason: Twilio phone number is not configured");
                System.err.println("Loan/application processing will CONTINUE.");
                System.err.println("==========================================");

                return;
            }

            // Send SMS through Twilio
            Message message = Message.creator(
                    new PhoneNumber(formattedPhone),
                    new PhoneNumber(twilioNumber),
                    messageText
            ).create();

            System.out.println("==========================================");
            System.out.println("CREDITBANK — SMS SENT SUCCESSFULLY");
            System.out.println("Recipient: " + formattedPhone);
            System.out.println("SID: " + message.getSid());
            System.out.println("==========================================");

        } catch (Exception e) {

            /*
             * =========================================================
             * CRITICAL:
             *
             * DO NOT THROW THE EXCEPTION.
             *
             * SMS is only a notification. It must never determine
             * whether the loan application succeeds or fails.
             * =========================================================
             */

            System.err.println("==========================================");
            System.err.println("CREDITBANK — SMS DELIVERY FAILED");
            System.err.println("Recipient: " + phone);
            System.err.println("Reason: " + e.getMessage());
            System.err.println("Loan/application processing will CONTINUE.");
            System.err.println("==========================================");

            // IMPORTANT:
            // No throw here.
            //
            // The loan processing method will continue normally.
        }
    }
}