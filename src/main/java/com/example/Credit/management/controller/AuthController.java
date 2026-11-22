
package com.example.Credit.management.controller;

import com.example.Credit.management.Model.*;
import com.example.Credit.management.Repository.AccountRepository;
import com.example.Credit.management.Repository.UserRepository;
import com.example.Credit.management.SecConfig.AuthResponse;
import com.example.Credit.management.SecConfig.JwtUtil;
import com.example.Credit.management.services.AuthService;
import com.example.Credit.management.services.CloudinaryImageStorageService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final JwtUtil jwtUtil;

    private final CloudinaryImageStorageService cloudinaryStorage;

    @Autowired
    public AuthController(
            AuthService authService,
            UserRepository userRepository,
            AccountRepository accountRepository,
            JwtUtil jwtUtil,
            CloudinaryImageStorageService cloudinaryStorage
    ) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.jwtUtil = jwtUtil;
        this.cloudinaryStorage = cloudinaryStorage;
    }

    /* REGISTER */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /* LOGIN */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {

        boolean SKIP_OTP = true;

        if (SKIP_OTP) {
            return ResponseEntity.ok(
                    authService.loginAndReturnSession(request)
            );
        }

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "OTP login temporarily disabled"));
    }

    /* ASSIGN USER ROLE */
    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> assignRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> request
    ) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        String roleName = request.get("role");

        if (roleName == null || roleName.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Role is required")
            );
        }

        try {
            Role role = Role.valueOf(
                    roleName.trim().toUpperCase(Locale.ROOT)
            );

            user.setRole(role);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "message", "User role updated successfully",
                    "userId", user.getId(),
                    "role", user.getRole().name()
            ));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Invalid role supplied")
            );
        }
    }

    /* RESEND OTP */
    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(
            @RequestBody Map<String, String> request
    ) {

        String email = request.get("email");

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Email is required")
            );
        }

        return ResponseEntity.ok(
                authService.sendOtpToBoth(email)
        );
    }

    /* VERIFY OTP */
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(
            @RequestBody Map<String, String> request
    ) {

        String email = request.get("email");
        String otp = request.get("otp");

        AuthResponse response =
                authService.verifyOtpAndReturnSession(email, otp);

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // UPLOAD LOGGED-IN USER PROFILE PHOTO - CLOUDINARY
    // =====================================================

    @PostMapping(
            value = "/profile-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> uploadProfileImage(
            @RequestParam("image") MultipartFile image,
            Authentication authentication
    ) {

        if (authentication == null ||
                !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message", "Authentication required."
                    ));
        }

        if (image == null || image.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message", "Please select an image."
                    ));
        }

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found."
                ));

        // Upload image to Cloudinary
        String imageUrl = cloudinaryStorage.upload(
                image,
                "credicore/users"
        );

        // Save Cloudinary URL in users table
        user.setUserImageUrl(imageUrl);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "Profile photo updated successfully.",
                "userImageUrl", imageUrl
        ));
    }


    /**
     * GET CURRENT AUTHENTICATED USER
     */
    @GetMapping("/me")
    public ResponseEntity<?> getLoggedInUser(
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required"));
        }

        return ResponseEntity.ok(
                authService.getCurrentUser(authentication.getName())
        );
    }


    /**
     * UPDATE AUTHENTICATED USER PROFILE
     */
    @PutMapping("/me")
    public ResponseEntity<?> updateLoggedInUser(
            @RequestBody Map<String, String> request,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required"));
        }

        return ResponseEntity.ok(
                authService.updateUserDetails(authentication.getName(), request)
        );
    }

    /**
     * CHANGE AUTHENTICATED USER PASSWORD
     */
    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestBody Map<String, String> request,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Authentication required"));
        }

        authService.changePassword(
                authentication.getName(),
                request.get("currentPassword"),
                request.get("newPassword")
        );

        return ResponseEntity.ok(
                Map.of("message", "Password changed successfully")
        );
    }




    /* GET ALL USERS */
    @GetMapping("/users/all")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    /* GET PENDING USERS */
    @GetMapping("/users/pending")
    public ResponseEntity<?> getPendingUsers() {

        List<User> pending =
                userRepository.findByStatus(AccountStatus.PENDING);

        Map<String, Object> response = new HashMap<>();
        response.put("count", pending.size());
        response.put("pendingUsers", pending);

        return ResponseEntity.ok(response);
    }

    /* APPROVE USER */
    @PutMapping("/approve/{id}")
    public ResponseEntity<?> approveUser(@PathVariable Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setStatus(AccountStatus.APPROVED);
        userRepository.save(user);

        return ResponseEntity.ok(
                Map.of("message", "User approved successfully")
        );
    }

    /* REVERT USER */
    @PutMapping("/revert/{id}")
    public ResponseEntity<?> revertUser(@PathVariable Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setStatus(AccountStatus.PENDING);
        userRepository.save(user);

        return ResponseEntity.ok(
                Map.of("message", "User reverted successfully")
        );
    }

    /* DELETE USER */
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {

        if (!userRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }

        userRepository.deleteById(id);

        return ResponseEntity.ok("User deleted successfully");
    }

    /* VERIFY ACCOUNT */
    @PostMapping("/verify-account")
    public ResponseEntity<?> verifyAccount(
            @RequestBody VerifyAccountRequest request
    ) {

        authService.verifyAccount(
                request.getEmail(),
                request.getAccountNumber()
        );

        return ResponseEntity.ok(Map.of(
                "verified", true,
                "message", "Account verified successfully."
        ));
    }

    /* FORGOT PASSWORD */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @RequestBody ForgotPasswordRequest request
    ) {

        authService.resetPasswordUsingAccount(
                request.getEmail(),
                request.getAccountNumber(),
                request.getNewPassword()
        );

        return ResponseEntity.ok(
                Map.of("message", "Password reset successfully.")
        );
    }
}
