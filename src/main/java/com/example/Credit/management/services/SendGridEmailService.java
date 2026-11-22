
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

    private static final String FROM_EMAIL = "ismaelkim2@gmail.com";

    /*
     * EXISTING OTP EMAIL
     * Preserves the existing optional OTP email behaviour.
     */
    public void sendOtpEmail(String toEmail, String otp) throws IOException {

        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalArgumentException(
                    "Recipient email is required."
            );
        }

        if (otp == null || otp.isBlank()) {
            throw new IllegalArgumentException(
                    "OTP is required."
            );
        }

        if (!isEmailConfigured()) {
            System.out.println(
                    "SendGrid disabled: API key not configured."
            );
            return;
        }

        Email from = new Email(FROM_EMAIL);
        Email to = new Email(toEmail.trim());

        Content content = new Content(
                "text/plain",
                "Your Credit Management OTP code is: "
                        + otp
                        + "\n\nThis code is valid for 5 minutes."
        );

        Mail mail = new Mail(
                from,
                "Credit Management - OTP Verification",
                to,
                content
        );

        sendEmail(mail);
    }

    /*
     * STAFF INVITATION EMAIL
     *
     * Unlike OTP delivery, this method reports missing
     * SendGrid configuration so the administrator knows
     * that email delivery was not attempted.
     *
     * The invitation remains valid even if email fails.
     */
    public void sendStaffInvitationEmail(
            String toEmail,
            String name,
            String invitationUrl
    ) throws IOException {

        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalArgumentException(
                    "Staff email address is required."
            );
        }

        if (invitationUrl == null || invitationUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Invitation link is required."
            );
        }

        if (!isEmailConfigured()) {
            throw new IllegalStateException(
                    "Staff invitation email could not be sent. "
                            + "SendGrid API key is not configured. "
                            + "You can still share the invitation link manually."
            );
        }

        String staffName = (
                name == null || name.isBlank()
                        ? "Staff Member"
                        : name.trim()
        );

        String message =
                "Hello " + staffName + ",\n\n"
                        + "You have been invited to join CrediCore "
                        + "as a staff member.\n\n"
                        + "To complete your registration and set your password, "
                        + "open the invitation link below:\n\n"
                        + invitationUrl + "\n\n"
                        + "This invitation link expires after 24 hours "
                        + "and can only be used once.\n\n"
                        + "Your account will remain pending until an administrator "
                        + "approves it.\n\n"
                        + "If you were not expecting this invitation, "
                        + "please contact your system administrator.\n\n"
                        + "Regards,\n"
                        + "CrediCore Administration";

        Email from = new Email(FROM_EMAIL);
        Email to = new Email(toEmail.trim());

        Content content = new Content(
                "text/plain",
                message
        );

        Mail mail = new Mail(
                from,
                "CrediCore - Staff Registration Invitation",
                to,
                content
        );

        sendEmail(mail);
    }

    /*
     * CENTRAL SENDGRID DELIVERY
     *
     * Only reports success when SendGrid returns
     * a successful HTTP response.
     */
    private void sendEmail(Mail mail) throws IOException {

        SendGrid sendGrid = new SendGrid(sendGridApiKey);

        Request request = new Request();

        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        Response response = sendGrid.api(request);

        if (response == null
                || response.getStatusCode() < 200
                || response.getStatusCode() >= 300) {

            int statusCode = response == null
                    ? -1
                    : response.getStatusCode();

            throw new IOException(
                    "SendGrid email delivery failed. HTTP status: "
                            + statusCode
            );
        }

        System.out.println(
                "SendGrid accepted email. HTTP status: "
                        + response.getStatusCode()
        );
    }

    /*
     * Checks whether SendGrid is configured.
     */
    public boolean isEmailConfigured() {

        return sendGridApiKey != null
                && !sendGridApiKey.isBlank();
    }
}
