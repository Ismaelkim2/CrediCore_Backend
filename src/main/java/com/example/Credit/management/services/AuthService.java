
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

import java.security.SecureRandom;
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

    // Twilio configuration
    @Value("${twilio.account.sid:}")
    private String twilioAccountSid;

    @Value("${twilio.auth.token:}")
    private String twilioAuthToken;

    @Value("${twilio.phone.number:}")
    private String twilioPhoneNumber;

    // Temporary OTP storage
    private static final Map<String, OtpData> otpStore =
            new ConcurrentHashMap<>();

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private static class OtpData {

        private final String otp;
        private final LocalDateTime expiryTime;

        OtpData(String otp, LocalDateTime expiryTime) {
            this.otp = otp;
            this.expiryTime = expiryTime;
        }
    }

    // =========================================================
    // PUBLIC APPLICANT REGISTRATION
    // =========================================================

    public Map<String, Object> register(RegisterRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Registration details are required."
            );
        }

        String name = request.getName() == null
                ? ""
                : request.getName().trim();

        String email = request.getEmail() == null
                ? ""
                : request.getEmail().trim().toLowerCase(Locale.ROOT);

        String nationalId = request.getNationalId() == null
                ? ""
                : request.getNationalId().trim();

        String phoneNumber = request.getPhoneNumber() == null
                ? ""
                : request.getPhoneNumber().trim();

        String password = request.getPassword();

        if (name.isBlank() || name.length() > 150) {
            throw new IllegalArgumentException(
                    "A valid full name is required."
            );
        }

        if (email.isBlank()
                || !email.matches(
                "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        )) {
            throw new IllegalArgumentException(
                    "A valid email address is required."
            );
        }

        if (nationalId.isBlank()) {
            throw new IllegalArgumentException(
                    "National ID is required."
            );
        }

        if (phoneNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "Phone number is required."
            );
        }

        validatePassword(password);

        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException(
                    "An account with this email address already exists."
            );
        }

        if (userRepository.existsByNationalId(nationalId)) {
            throw new IllegalArgumentException(
                    "A profile with this National ID already exists."
            );
        }

        /*
         * Public registration can ONLY create APPLICANT accounts.
         * Privileged roles are assigned through administrator
         * controlled onboarding.
         *
         * Applicants complete their registration here.
         * Approval remains a separate administrator action.
         */
        LocalDateTime completedAt = LocalDateTime.now();

        User user = User.builder()
                .name(name)
                .email(email)
                .phoneNumber(phoneNumber)
                .nationalId(nationalId)
                .password(passwordEncoder.encode(password))
                .role(Role.APPLICANT)
                .status(AccountStatus.PENDING)
                .registrationCompleted(true)
                .registrationCompletedAt(completedAt)
                .build();

        User savedUser = userRepository.save(user);

        Map<String, Object> response = new HashMap<>();

        response.put(
                "message",
                "Registration successful. Await admin approval."
        );
        response.put("userId", savedUser.getId());
        response.put("name", savedUser.getName());
        response.put("email", savedUser.getEmail());
        response.put("role", savedUser.getRole().name());
        response.put("status", savedUser.getStatus().name());
        response.put("registrationCompleted", true);

        return response;
    }

    // =========================================================
    // CURRENT USER PROFILE
    // =========================================================

    public Map<String, Object> getCurrentUser(String email) {

        User user = userRepository.findByEmail(
                normalizeEmail(email)
        ).orElseThrow(() ->
                new RuntimeException("User not found")
        );

        Account account = user.getNationalId() == null
                ? null
                : accountRepository
                .findByNationalId(user.getNationalId())
                .orElse(null);

        Map<String, Object> response = new HashMap<>();

        response.put("name", user.getName());
        response.put("email", user.getEmail());
        response.put(
                "role",
                user.getRole() == null ? null : user.getRole().name()
        );
        response.put("nationalId", user.getNationalId());

        response.put(
                "phoneNumber",
                account != null
                        ? account.getPhoneNumber()
                        : user.getPhoneNumber()
        );

        response.put(
                "accountNumber",
                account != null ? account.getAccountNumber() : null
        );

        response.put(
                "branch",
                account != null ? account.getBranch() : "Head Office"
        );

        String imageUrl = user.getUserImageUrl();

        if (imageUrl == null || imageUrl.isBlank()) {

            if (account != null
                    && account.getImageUrl() != null
                    && !account.getImageUrl().isBlank()) {

                String accountImage = account.getImageUrl();

                if (accountImage.startsWith("http://")
                        || accountImage.startsWith("https://")) {

                    imageUrl = accountImage;

                } else {

                    imageUrl =
                            "http://localhost:8080/api/accounts/image/"
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

    // =========================================================
    // PASSWORD LOGIN
    // =========================================================

    public AuthResponse loginAndReturnSession(LoginRequest request) {

        if (request == null
                || request.getEmail() == null
                || request.getEmail().isBlank()
                || request.getPassword() == null) {

            throw new RuntimeException("Invalid credentials");
        }

        String email = normalizeEmail(request.getEmail());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("Invalid credentials")
                );

        if (user.getPassword() == null
                || !passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new RuntimeException("Invalid credentials");
        }

        ensureLoginAllowed(user);

        return createAuthResponse(
                user,
                "Login successful"
        );
    }

    // =========================================================
    // OTP DELIVERY
    // =========================================================

    private void sendOtpToUser(User user) {

        ensureLoginAllowed(user);

        String otp = generateOtp();

        LocalDateTime expiry = LocalDateTime.now().plusMinutes(5);

        OtpData otpData = new OtpData(otp, expiry);

        // Store against email
        otpStore.put(
                normalizeEmail(user.getEmail()),
                otpData
        );

        // Store against normalized phone if available
        if (user.getPhoneNumber() != null
                && !user.getPhoneNumber().isBlank()) {

            String normalizedPhone =
                    normalizePhone(user.getPhoneNumber());

            otpStore.put(normalizedPhone, otpData);

            // Send SMS when Twilio is configured
            if (twilioAccountSid != null
                    && !twilioAccountSid.isBlank()
                    && twilioAuthToken != null
                    && !twilioAuthToken.isBlank()
                    && twilioPhoneNumber != null
                    && !twilioPhoneNumber.isBlank()) {

                try {

                    Twilio.init(
                            twilioAccountSid,
                            twilioAuthToken
                    );

                    Message.creator(
                            new PhoneNumber(normalizedPhone),
                            new PhoneNumber(twilioPhoneNumber),
                            "Your CrediCore verification code is: "
                                    + otp
                                    + ". Valid for 5 minutes."
                    ).create();

                } catch (Exception e) {

                    System.err.println(
                            "Twilio SMS delivery failed: "
                                    + e.getMessage()
                    );
                }
            }
        }

        // Send email when SendGrid is available
        try {

            if (sendGridEmailService != null) {
                sendGridEmailService.sendOtpEmail(
                        user.getEmail(),
                        otp
                );
            }

        } catch (Exception e) {

            System.err.println(
                    "SendGrid OTP delivery failed: "
                            + e.getMessage()
            );
        }
    }

    // =========================================================
    // SEND OTP TO EMAIL AND PHONE
    // =========================================================

    public Map<String, Object> sendOtpToBoth(String email) {

        User user = userRepository.findByEmail(
                normalizeEmail(email)
        ).orElseThrow(() ->
                new RuntimeException("User not found")
        );

        sendOtpToUser(user);

        Map<String, Object> response = new HashMap<>();

        response.put(
                "message",
                "OTP generated. Delivery depends on configured channels."
        );
        response.put("email", user.getEmail());
        response.put("phoneNumber", user.getPhoneNumber());

        return response;
    }

    // =========================================================
    // VERIFY OTP
    // =========================================================

    public boolean verifyOtp(String identifier, String otp) {

        if (identifier == null
                || identifier.isBlank()
                || otp == null
                || otp.isBlank()) {

            return false;
        }

        String trimmedIdentifier = identifier.trim();

        List<String> possibleKeys = new ArrayList<>();

        if (trimmedIdentifier.contains("@")) {

            possibleKeys.add(
                    normalizeEmail(trimmedIdentifier)
            );

        } else {

            possibleKeys.add(
                    normalizePhone(trimmedIdentifier)
            );

            possibleKeys.add(trimmedIdentifier);
        }

        for (String key : possibleKeys) {

            OtpData otpData = otpStore.get(key);

            if (otpData == null) {
                continue;
            }

            if (otpData.expiryTime.isBefore(LocalDateTime.now())) {

                removeOtp(otpData);

                return false;
            }

            if (otpData.otp.equals(otp.trim())) {

                /*
                 * Remove every key referencing this same OTP.
                 * This prevents reuse through email after
                 * verification using the phone number, or vice versa.
                 */
                removeOtp(otpData);

                return true;
            }
        }

        return false;
    }

    private void removeOtp(OtpData otpData) {

        otpStore.entrySet().removeIf(
                entry -> entry.getValue() == otpData
        );
    }

    // =========================================================
    // VERIFY OTP AND RETURN SESSION
    // =========================================================

    public AuthResponse verifyOtpAndReturnSession(
            String email,
            String otp
    ) {

        String normalizedEmail = normalizeEmail(email);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new RuntimeException("User not found")
                );

        /*
         * Check approval and registration before issuing
         * an authenticated session.
         */
        ensureLoginAllowed(user);

        boolean verified = verifyOtp(normalizedEmail, otp);

        if (!verified) {
            throw new RuntimeException(
                    "Invalid or expired OTP"
            );
        }

        return createAuthResponse(
                user,
                "OTP verified successfully"
        );
    }

    // =========================================================
    // CENTRAL LOGIN ELIGIBILITY CHECK
    // =========================================================

    private void ensureLoginAllowed(User user) {

        if (user.getStatus() != AccountStatus.APPROVED) {

            throw new AccountNotApprovedException(
                    "Account not approved. Please wait for admin approval."
            );
        }

        if (user.getRole() == null) {

            throw new AccountNotApprovedException(
                    "Account role has not been configured. Contact administrator."
            );
        }

        /*
         * Explicitly incomplete users cannot log in.
         *
         * NULL is temporarily accepted for compatibility with
         * legacy users created before this field existed.
         * Backfill legacy records before enforcing strict NOT NULL.
         */
        if (Boolean.FALSE.equals(user.getRegistrationCompleted())) {

            throw new AccountNotApprovedException(
                    "Registration is incomplete. Please complete your registration invitation."
            );
        }
    }

    // =========================================================
    // AUTHENTICATION RESPONSE
    // =========================================================

    private AuthResponse createAuthResponse(
            User user,
            String message
    ) {

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

    // =========================================================
    // UPDATE USER PROFILE
    // =========================================================

    public Map<String, Object> updateUserDetails(
            String authenticatedEmail,
            Map<String, String> request
    ) {

        User existingUser = userRepository.findByEmail(
                normalizeEmail(authenticatedEmail)
        ).orElseThrow(() ->
                new RuntimeException("User not found")
        );

        if (request == null) {
            throw new IllegalArgumentException(
                    "Profile details are required."
            );
        }

        String name = request.get("name");
        String phoneNumber = request.get("phoneNumber");

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Name is required"
            );
        }

        if (name.trim().length() > 150) {
            throw new IllegalArgumentException(
                    "Name is too long"
            );
        }

        if (phoneNumber != null
                && phoneNumber.trim().length() > 30) {

            throw new IllegalArgumentException(
                    "Phone number is too long"
            );
        }

        existingUser.setName(name.trim());

        existingUser.setPhoneNumber(
                phoneNumber == null || phoneNumber.isBlank()
                        ? null
                        : phoneNumber.trim()
        );

        userRepository.save(existingUser);

        return getCurrentUser(existingUser.getEmail());
    }

    // =========================================================
    // CHANGE PASSWORD
    // =========================================================

    public void changePassword(
            String authenticatedEmail,
            String currentPassword,
            String newPassword
    ) {

        User user = userRepository.findByEmail(
                normalizeEmail(authenticatedEmail)
        ).orElseThrow(() ->
                new RuntimeException("User not found")
        );

        if (currentPassword == null
                || currentPassword.isBlank()) {

            throw new IllegalArgumentException(
                    "Current password is required"
            );
        }

        validatePassword(newPassword);

        if (!passwordEncoder.matches(
                currentPassword,
                user.getPassword()
        )) {

            throw new IllegalArgumentException(
                    "Current password is incorrect"
            );
        }

        if (passwordEncoder.matches(
                newPassword,
                user.getPassword()
        )) {

            throw new IllegalArgumentException(
                    "New password must be different from the current password"
            );
        }

        user.setPassword(
                passwordEncoder.encode(newPassword)
        );

        userRepository.save(user);
    }

    // =========================================================
    // PASSWORD VALIDATION
    // =========================================================

    private void validatePassword(String password) {

        if (password == null
                || password.length() < 8
                || !password.matches(
                "^(?=.*[A-Za-z])(?=.*\\d).+$"
        )) {

            throw new IllegalArgumentException(
                    "Password must contain at least 8 characters, including letters and numbers."
            );
        }
    }

    // =========================================================
    // OTP GENERATION
    // =========================================================

    private String generateOtp() {

        return String.format(
                "%06d",
                SECURE_RANDOM.nextInt(1_000_000)
        );
    }

    // =========================================================
    // EMAIL NORMALIZATION
    // =========================================================

    private String normalizeEmail(String email) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Email is required."
            );
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }

    // =========================================================
    // KENYAN PHONE NORMALIZATION
    // =========================================================

    private String normalizePhone(String phoneNumber) {

        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "";
        }

        String phone = phoneNumber.trim().replaceAll("\\s+", "");

        if (phone.startsWith("+")) {
            return phone;
        }

        if (phone.startsWith("0")) {
            return "+254" + phone.substring(1);
        }

        if (phone.startsWith("254")) {
            return "+" + phone;
        }

        return phone;
    }

    // =========================================================
    // UNAPPROVED ACCOUNT EXCEPTION
    // =========================================================

    public static class AccountNotApprovedException
            extends RuntimeException {

        public AccountNotApprovedException(String message) {
            super(message);
        }
    }

    // =========================================================
    // VERIFY ACCOUNT FOR PASSWORD RESET
    // =========================================================

    public void verifyAccount(
            String email,
            String accountNumber
    ) {

        User user = userRepository.findByEmail(
                normalizeEmail(email)
        ).orElseThrow(() ->
                new RuntimeException("Email not found.")
        );

        Account account = accountRepository
                .findByAccountNumber(accountNumber)
                .orElseThrow(() ->
                        new RuntimeException("Account number not found.")
                );

        if (!Objects.equals(
                user.getNationalId(),
                account.getNationalId()
        )) {

            throw new RuntimeException(
                    "Email and account number do not match."
            );
        }
    }

    // =========================================================
    // RESET PASSWORD USING BANK ACCOUNT
    // =========================================================

    public void resetPasswordUsingAccount(
            String email,
            String accountNumber,
            String newPassword
    ) {

        validatePassword(newPassword);

        verifyAccount(email, accountNumber);

        User user = userRepository.findByEmail(
                normalizeEmail(email)
        ).orElseThrow(() ->
                new RuntimeException("User not found.")
        );

        user.setPassword(
                passwordEncoder.encode(newPassword)
        );

        userRepository.save(user);
    }
}
