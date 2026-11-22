package com.example.Credit.management.services;
import com.example.Credit.management.Model.*;
import com.example.Credit.management.Repository.StaffRepository;
import com.example.Credit.management.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.List;

@Service
public class StaffOnboardingServiceImpl implements StaffOnboardingService {

    private static final int INVITATION_VALIDITY_HOURS = 24;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final SendGridEmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    @Value("${app.frontend.staff-invitation-path:/staff-invitation}")
    private String invitationPath;

    public StaffOnboardingServiceImpl(
            UserRepository userRepository,
            StaffRepository staffRepository,
            PasswordEncoder passwordEncoder,
            SendGridEmailService emailService
    ) {
        this.userRepository = userRepository;
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    // =====================================================
    // CREATE STAFF AND GENERATE INVITATION
    // =====================================================

    @Override
    @Transactional
    public Map<String, Object> createStaffInvitation(
            StaffInvitationRequest request
    ) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Staff details are required."
            );
        }

        String name = clean(request.getName());
        String email = clean(request.getEmail())
                .toLowerCase(Locale.ROOT);
        String nationalId = clean(request.getNationalId());
        String phone = clean(request.getPhone());
        String department = clean(request.getDepartment());

        Role role = request.getRole();

        if (name.isBlank()
                || email.isBlank()
                || nationalId.isBlank()
                || phone.isBlank()
                || department.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Name, email, national ID, phone and department are required."
            );
        }

        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Enter a valid email address."
            );
        }

        if (role == null || role == Role.APPLICANT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Select a valid staff role."
            );
        }

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A user with this email already exists."
            );
        }

        if (userRepository.existsByNationalId(nationalId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A user with this national ID already exists."
            );
        }

        if (staffRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A staff profile with this email already exists."
            );
        }

        // Generate a cryptographically secure invitation token.
        String rawToken = generateToken();

        // Store only the token hash.
        String tokenHash = hashToken(rawToken);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusHours(
                INVITATION_VALIDITY_HOURS
        );

        /*
         * Staff have not chosen their passwords yet.
         * Store a random BCrypt hash temporarily.
         */
        String temporaryPassword = generateToken();

        User user = new User();

        user.setName(name);
        user.setEmail(email);
        user.setNationalId(nationalId);
        user.setPhoneNumber(phone);

        user.setPassword(
                passwordEncoder.encode(temporaryPassword)
        );

        user.setRole(role);
        user.setStatus(AccountStatus.PENDING);

        user.setRegistrationCompleted(false);
        user.setRegistrationTokenHash(tokenHash);
        user.setRegistrationTokenExpiry(expiry);
        user.setRegistrationCompletedAt(null);

        User savedUser = userRepository.save(user);

        // Create the linked staff business profile.
        Staff staff = new Staff();

        staff.setName(name);
        staff.setEmail(email);
        staff.setPhone(phone);
        staff.setDepartment(department);
        staff.setUser(savedUser);

        Staff savedStaff = staffRepository.save(staff);

        // Raw token is returned only to the administrator.
        String invitationUrl = buildInvitationUrl(rawToken);

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("success", true);
        result.put("message", "Staff invitation created successfully.");
        result.put("userId", savedUser.getId());
        result.put("staffId", savedStaff.getId());
        result.put("name", name);
        result.put("email", email);
        result.put("role", role.name());
        result.put("status", AccountStatus.PENDING.name());
        result.put("registrationCompleted", false);
        result.put("invitationUrl", invitationUrl);
        result.put("expiresAt", expiry);
        result.put("deliveryStatus", "MANUAL_SHARE");

        // Optional email delivery.
        // A failed email must not invalidate the invitation.
        if (request.isSendEmail()) {

            try {

                emailService.sendStaffInvitationEmail(
                        email,
                        name,
                        invitationUrl
                );

                result.put("deliveryStatus", "EMAIL_SENT");

                result.put(
                        "message",
                        "Staff invitation created and email sent successfully."
                );

            } catch (Exception ex) {

                // Do not log the raw invitation URL or token.
                result.put("deliveryStatus", "EMAIL_FAILED");

                result.put(
                        "emailError",
                        "Email delivery failed. Copy and share the invitation link manually."
                );
            }
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> validateInvitation(String token) {

        User user = findValidInvitation(token);

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("valid", true);
        result.put("name", user.getName());
        result.put("email", user.getEmail());
        result.put("role", user.getRole().name());
        result.put("expiresAt", user.getRegistrationTokenExpiry());

        return result;
    }
    // =====================================================
    // COMPLETE STAFF REGISTRATION
    // =====================================================

    @Override
    @Transactional
    public Map<String, Object> completeInvitation(
            CompleteInvitationRequest request
    ) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invitation details are required."
            );
        }

        String newPassword = request.getNewPassword();
        String confirmPassword = request.getConfirmPassword();

        if (newPassword == null
                || confirmPassword == null
                || newPassword.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password and password confirmation are required."
            );
        }

        if (!Objects.equals(newPassword, confirmPassword)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Passwords do not match."
            );
        }

        if (newPassword.length() < 8) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must contain at least 8 characters."
            );
        }
        User user = findValidInvitationForUpdate(request.getToken());

        user.setPassword(
                passwordEncoder.encode(newPassword)
        );

        user.setRegistrationCompleted(true);
        user.setRegistrationCompletedAt(LocalDateTime.now());

        // Invalidate the invitation immediately after use.
        user.setRegistrationTokenHash(null);
        user.setRegistrationTokenExpiry(null);

        // Registration completion is separate from approval.
        user.setStatus(AccountStatus.PENDING);

        userRepository.save(user);

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("success", true);

        result.put(
                "message",
                "Registration completed. Your account is awaiting administrator approval."
        );

        result.put("userId", user.getId());
        result.put("registrationCompleted", true);
        result.put("status", AccountStatus.PENDING.name());

        return result;
    }

    // =====================================================
    // FIND AND VALIDATE INVITATION
    // =====================================================

    private User findValidInvitation(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invitation token is required."
            );
        }

        String tokenHash = hashToken(rawToken.trim());

        User user = userRepository
                .findByRegistrationTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invitation is invalid or has already been used."
                ));

        if (user.getRegistrationTokenExpiry() == null
                || !user.getRegistrationTokenExpiry().isAfter(
                LocalDateTime.now()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This invitation has expired."
            );
        }

        if (Boolean.TRUE.equals(user.getRegistrationCompleted())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Registration has already been completed."
            );
        }

        if (user.getRole() == null
                || user.getRole() == Role.APPLICANT) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This invitation is not valid for staff registration."
            );
        }

        if (user.getStatus() != AccountStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This staff invitation is no longer active."
            );
        }

        return user;
    }

    // =====================================================
    // SECURE TOKEN GENERATION
    // =====================================================

    private String generateToken() {

        byte[] bytes = new byte[32];

        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    // =====================================================
    // TOKEN HASHING
    // =====================================================

    private String hashToken(String token) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return Base64.getEncoder()
                    .encodeToString(hash);

        } catch (Exception ex) {

            throw new IllegalStateException(
                    "Unable to process invitation token.",
                    ex
            );
        }
    }

    // =====================================================
    // BUILD INVITATION URL
    // =====================================================

    private String buildInvitationUrl(String token) {

        String baseUrl = frontendBaseUrl.replaceAll("/+$", "");

        String path = invitationPath.startsWith("/")
                ? invitationPath
                : "/" + invitationPath;

        String encodedToken = URLEncoder.encode(
                token,
                StandardCharsets.UTF_8
        );

        return baseUrl + path + "?token=" + encodedToken;
    }

    // =====================================================
    // INPUT NORMALIZATION
    // =====================================================

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private User findValidInvitationForUpdate(String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invitation token is required."
            );
        }

        String tokenHash = hashToken(rawToken.trim());

        User user = userRepository
                .findByRegistrationTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invitation is invalid or has already been used."
                ));

        if (user.getRegistrationTokenExpiry() == null
                || !user.getRegistrationTokenExpiry().isAfter(
                LocalDateTime.now()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This invitation has expired."
            );
        }

        if (Boolean.TRUE.equals(user.getRegistrationCompleted())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Registration has already been completed."
            );
        }

        if (user.getRole() == null || user.getRole() == Role.APPLICANT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid staff invitation."
            );
        }

        if (user.getStatus() != AccountStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This staff invitation is no longer active."
            );
        }

        return user;
    }


    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllStaff() {

        List<Staff> staffList = staffRepository.findAll();

        return staffList.stream().map(staff -> {

            User user = staff.getUser();

            Map<String, Object> item = new LinkedHashMap<>();

            item.put("staffId", staff.getId());
            item.put("name", staff.getName());
            item.put("email", staff.getEmail());
            item.put("phone", staff.getPhone());
            item.put("department", staff.getDepartment());

            if (user != null) {
                item.put("userId", user.getId());
                item.put("role", user.getRole() != null
                        ? user.getRole().name() : null);
                item.put("status", user.getStatus() != null
                        ? user.getStatus().name() : null);
                item.put("registrationCompleted",
                        Boolean.TRUE.equals(user.getRegistrationCompleted()));
                item.put("invitationPending",
                        user.getRegistrationTokenHash() != null
                                && user.getRegistrationTokenExpiry() != null
                                && user.getRegistrationTokenExpiry().isAfter(
                                LocalDateTime.now()
                        )
                                && !Boolean.TRUE.equals(user.getRegistrationCompleted()));
                item.put("registrationCompletedAt",
                        user.getRegistrationCompletedAt());
            } else {
                item.put("userId", null);
                item.put("role", null);
                item.put("status", null);
                item.put("registrationCompleted", false);
                item.put("invitationPending", false);
                item.put("registrationCompletedAt", null);
            }

            return item;

        }).toList();
    }



    @Override
    @Transactional
    public Map<String, Object> regenerateStaffInvitation(
            Long userId,
            boolean sendEmail
    ) {

        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User ID is required."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Staff user was not found."
                ));

        if (user.getRole() == null
                || user.getRole() == Role.APPLICANT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This user is not a staff member."
            );
        }

        if (Boolean.TRUE.equals(user.getRegistrationCompleted())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Registration is already completed. No invitation is required."
            );
        }

        if (user.getStatus() != AccountStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only pending staff accounts can receive invitations."
            );
        }

        // Ensure this user has a linked Staff profile.
        Staff staff = staffRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Linked staff profile was not found."
                ));

        // Generate a new token. The previous token becomes invalid.
        String rawToken = generateToken();
        String tokenHash = hashToken(rawToken);

        LocalDateTime expiry = LocalDateTime.now()
                .plusHours(INVITATION_VALIDITY_HOURS);

        user.setRegistrationTokenHash(tokenHash);
        user.setRegistrationTokenExpiry(expiry);

        userRepository.save(user);

        String invitationUrl = buildInvitationUrl(rawToken);

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("success", true);
        result.put("message", "Staff invitation regenerated successfully.");
        result.put("userId", user.getId());
        result.put("staffId", staff.getId());
        result.put("name", user.getName());
        result.put("email", user.getEmail());
        result.put("role", user.getRole().name());
        result.put("invitationUrl", invitationUrl);
        result.put("expiresAt", expiry);
        result.put("deliveryStatus", "MANUAL_SHARE");

        if (sendEmail) {
            try {
                emailService.sendStaffInvitationEmail(
                        user.getEmail(),
                        user.getName(),
                        invitationUrl
                );

                result.put("deliveryStatus", "EMAIL_SENT");
                result.put(
                        "message",
                        "Invitation regenerated and email sent successfully."
                );

            } catch (Exception ex) {
                result.put("deliveryStatus", "EMAIL_FAILED");
                result.put(
                        "emailError",
                        "Email delivery failed. Copy and share the new invitation link manually."
                );
            }
        }

        return result;
    }


}
