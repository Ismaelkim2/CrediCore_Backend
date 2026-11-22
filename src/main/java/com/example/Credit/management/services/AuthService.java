package com.example.Credit.management.services;

import com.example.Credit.management.Model.*;
import com.example.Credit.management.Repository.AccountRepository;
import com.example.Credit.management.Repository.UserRepository;
import com.example.Credit.management.SecConfig.AuthResponse;
import com.example.Credit.management.SecConfig.JwtUtil;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Autowired(required = false)
    private SendGridEmailService sendGridEmailService;

    // Twilio config
    @Value("${twilio.account.sid:}")
    private String twilioAccountSid;

    @Value("${twilio.auth.token:}")
    private String twilioAuthToken;

    @Value("${twilio.phone.number:}")
    private String twilioPhoneNumber;


    // Temporary OTP store in memory
    private static final Map<String, OtpData> otpStore = new ConcurrentHashMap<>();

    private static class OtpData {
        String otp;
        LocalDateTime expiryTime;

        OtpData(String otp, LocalDateTime expiryTime) {
            this.otp = otp;
            this.expiryTime = expiryTime;
        }
    }

    /** Register user */
    public Map<String, Object> register(RegisterRequest request) {
        Account account = accountRepository.findByNationalId(request.getNationalId())
                .orElseThrow(() -> new RuntimeException("No account found with given National ID"));

        if (userRepository.existsByNationalId(request.getNationalId())) {
            throw new RuntimeException("User already registered with this National ID");
        }

        Role userRole = request.getRole() != null ? request.getRole() : Role.APPLICANT;

        User user = User.builder()
                .name(account.getFullName())
                .email(account.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(userRole)
                .nationalId(request.getNationalId())
                .status(AccountStatus.PENDING)
                .build();

        userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Registration successful. Await admin approval.");
        return response;
    }

    public Map<String, Object> getCurrentUser(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Account account = accountRepository
                .findByNationalId(user.getNationalId())
                .orElse(null);

        Map<String, Object> response = new HashMap<>();

        response.put("name", user.getName());
        response.put("email", user.getEmail());
        response.put("role", user.getRole().name());
        response.put("nationalId", user.getNationalId());

        response.put(
                "phoneNumber",
                account != null ? account.getPhoneNumber() : user.getPhoneNumber()
        );

        response.put(
                "accountNumber",
                account != null ? account.getAccountNumber() : null
        );

        response.put(
                "branch",
                account != null ? account.getBranch() : "Head Office"
        );

        // Prefer the user's uploaded profile photograph.
        String imageUrl = user.getUserImageUrl();

        // Fall back to the account photograph if no user photograph exists.
        if (imageUrl == null || imageUrl.isBlank()) {

            if (account != null && account.getImageUrl() != null
                    && !account.getImageUrl().isBlank()) {

                String accountImage = account.getImageUrl();

                if (accountImage.startsWith("http://")
                        || accountImage.startsWith("https://")) {

                    imageUrl = accountImage;

                } else {

                    imageUrl = "http://localhost:8080/api/accounts/image/"
                            + user.getNationalId();
                }

            } else {
                imageUrl = "assets/default-admin.png";
            }

        } else if (!imageUrl.startsWith("http://")
                && !imageUrl.startsWith("https://")
                && !imageUrl.startsWith("assets/")) {

            imageUrl = "http://localhost:8080/" + imageUrl;
        }

        response.put("userImageUrl", imageUrl);

        return response;
    }

    public AuthResponse loginAndReturnSession(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        if (user.getStatus() != AccountStatus.APPROVED) {
            throw new AccountNotApprovedException(
                    "Account not approved. Please wait for admin approval."
            );
        }

        String token = jwtUtil.generateToken(
                user.getEmail(),
                user.getRole().name(),
                user.getName()
        );

        return AuthResponse.builder()
                .token(token)
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .nationalId(user.getNationalId())
                .phoneNumber(user.getPhoneNumber())
                .userImageUrl(user.getUserImageUrl())
                .build();
    }


    /** Send OTP via Twilio SMS + SendGrid Email */
    private void sendOtpToUser(User user) {
        String otp = generateOtp();

        // Save OTP in memory for 5 minutes
        otpStore.put(user.getEmail(), new OtpData(otp, LocalDateTime.now().plusMinutes(5)));
        if (user.getPhoneNumber() != null
                && twilioAccountSid != null
                && !twilioAccountSid.isBlank()
                && twilioAuthToken != null
                && !twilioAuthToken.isBlank()) {

            String normalizedPhone = normalizePhone(user.getPhoneNumber());
            otpStore.put(normalizedPhone, new OtpData(otp, LocalDateTime.now().plusMinutes(5)));

            // Send SMS
            try {
                Twilio.init(twilioAccountSid, twilioAuthToken);
                Message.creator(
                        new PhoneNumber(normalizedPhone),
                        new PhoneNumber(twilioPhoneNumber != null ? twilioPhoneNumber : "+12174066363"),
                        "🔐 Your verification code is: " + otp
                ).create();
                System.out.println("OTP sent via SMS to " + normalizedPhone);
            } catch (Exception e) {
                System.out.println("Twilio SMS failed: " + e.getMessage());
            }
        }

        // Send Email
        try {
            if (sendGridEmailService != null) {
                sendGridEmailService.sendOtpEmail(user.getEmail(), otp);
            }
        } catch (Exception e) {
            System.out.println("SendGrid Email failed: " + e.getMessage());
        }

    }

    /** Send OTP to user by email (and phone if available) */
    public Map<String, Object> sendOtpToBoth(String email) {
        // Find user by email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Send OTP via the existing private method
        sendOtpToUser(user);

        // Return a response
        Map<String, Object> response = new HashMap<>();
        response.put("message", "OTP sent successfully to email and phone (if available)");
        response.put("email", user.getEmail());
        response.put("phoneNumber", user.getPhoneNumber());
        return response;
    }


    /** Verify OTP */
    public boolean verifyOtp(String identifier, String otp) {
        if (identifier == null || identifier.isBlank() || otp == null || otp.isBlank()) {
            return false;
        }

        identifier = identifier.trim();
        otp = otp.trim();

        String normalizedPhone = identifier.startsWith("0") ? "+254" + identifier.substring(1) : null;
        List<String> possibleKeys = new ArrayList<>();
        possibleKeys.add(identifier);
        if (normalizedPhone != null) possibleKeys.add(normalizedPhone);

        for (String key : possibleKeys) {
            OtpData otpData = otpStore.get(key);
            if (otpData != null) {
                if (otpData.expiryTime.isBefore(LocalDateTime.now())) {
                    otpStore.remove(key);
                    return false;
                }
                if (otpData.otp.equals(otp)) {
                    otpStore.remove(key);
                    return true;
                }
            }
        }

        return false;
    }

    /** Verify OTP and return JWT + user info */
    public AuthResponse verifyOtpAndReturnSession(String email, String otp) {
        boolean ok = verifyOtp(email, otp);
        if (!ok) throw new RuntimeException("Invalid or expired OTP");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name(), user.getName());


        return new AuthResponse(
                token,
                "OTP verified successfully",
                user.getName(),
                user.getRole().name(),
                user.getEmail(),
                user.getNationalId(),
                user.getUserImageUrl(),
                user.getPhoneNumber()
        );
    }



    /** Update user details */

    /**
     * Update the authenticated user's permitted profile details.
     * The user's identity comes from the authenticated email.
     */
    public Map<String, Object> updateUserDetails(
            String authenticatedEmail,
            Map<String, String> request
    ) {
        User existingUser = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String name = request.get("name");
        String phoneNumber = request.get("phoneNumber");

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }

        if (name.trim().length() > 150) {
            throw new IllegalArgumentException("Name is too long");
        }

        if (phoneNumber != null && phoneNumber.trim().length() > 30) {
            throw new IllegalArgumentException("Phone number is too long");
        }

        existingUser.setName(name.trim());

        // Update the user's own phone number.
        // Do not overwrite account information or branch details.
        existingUser.setPhoneNumber(
                phoneNumber == null || phoneNumber.isBlank()
                        ? null
                        : phoneNumber.trim()
        );

        userRepository.save(existingUser);

        return getCurrentUser(existingUser.getEmail());
    }

    /**
     * Change password for the authenticated user.
     */
    public void changePassword(
            String authenticatedEmail,
            String currentPassword,
            String newPassword
    ) {
        User user = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException("Current password is required");
        }

        if (newPassword == null || newPassword.length() < 8
                || !newPassword.matches("^(?=.*[A-Za-z])(?=.*\\d).+$")) {
            throw new IllegalArgumentException(
                    "New password must contain at least 8 characters, including letters and numbers"
            );
        }

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException(
                    "New password must be different from the current password"
            );
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }


    /** Generate 6-digit OTP */
    private String generateOtp() {
        return String.format("%06d", new Random().nextInt(999999));
    }

    /** Normalize Kenyan phone numbers */
    private String normalizePhone(String phoneNumber) {
        if (phoneNumber.startsWith("+")) return phoneNumber;
        if (phoneNumber.startsWith("0")) return "+254" + phoneNumber.substring(1);
        return phoneNumber;
    }

    /** Custom exception for unapproved accounts */
    public static class AccountNotApprovedException extends RuntimeException {
        public AccountNotApprovedException(String message) {
            super(message);
        }
    }


    public void verifyAccount(String email, String accountNumber) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("Email not found."));

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() ->
                        new RuntimeException("Account number not found."));

        if (!user.getNationalId().equals(account.getNationalId())) {
            throw new RuntimeException("Email and account number do not match.");
        }

    }

    public void resetPasswordUsingAccount(
            String email,
            String accountNumber,
            String newPassword) {

        verifyAccount(email, accountNumber);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found."));

        user.setPassword(passwordEncoder.encode(newPassword));

        userRepository.save(user);
    }
}
