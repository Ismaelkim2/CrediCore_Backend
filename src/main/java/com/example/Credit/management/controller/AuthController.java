package com.example.Credit.management.controller;
import com.example.Credit.management.Model.*;
import com.example.Credit.management.Repository.AccountRepository;
import com.example.Credit.management.Repository.UserRepository;
import com.example.Credit.management.SecConfig.AuthResponse;
import com.example.Credit.management.SecConfig.JwtUtil;
import com.example.Credit.management.services.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final JwtUtil jwtUtil;

    @Autowired
    public AuthController(AuthService authService, UserRepository userRepository,
                          AccountRepository accountRepository, JwtUtil jwtUtil) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.jwtUtil = jwtUtil;
    }

    /* REGISTER */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /* LOGIN -> SEND OTP ALWAYS */
//    @PostMapping("/login")
//    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
//        return ResponseEntity.ok(authService.login(request));
//    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {

        boolean SKIP_OTP = true;

        if (SKIP_OTP) {
            return ResponseEntity.ok(authService.loginAndReturnSession(request));
        }

        return ResponseEntity
                .status(503)
                .body(Map.of("message", "OTP login temporarily disabled"));
    }



    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> assignRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        String roleName = request.get("role");

        if (roleName == null || roleName.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Role is required"));
        }

        try {
            Role role = Role.valueOf(
                    roleName.trim().toUpperCase(Locale.ROOT));

            user.setRole(role);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "message", "User role updated successfully",
                    "userId", user.getId(),
                    "role", user.getRole().name()
            ));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Invalid role supplied"));
        }
    }



    /* RESEND OTP -> SAME METHOD AS LOGIN */
    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> request) {

        String email = request.get("email");

        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }

        return ResponseEntity.ok(authService.sendOtpToBoth(email));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");

        AuthResponse response = authService.verifyOtpAndReturnSession(email, otp);

        return ResponseEntity.ok(response);
    }


    /* VERIFY OTP */
//    @PostMapping("/verify-otp")
//    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> request) {
//        String email = request.get("email");
//        String otp = request.get("otp");
//
//        if (email == null || email.isEmpty() || otp == null || otp.isEmpty()) {
//            return ResponseEntity.badRequest().body(Map.of("error", "Email and OTP are required"));
//        }
//
//        try {
//            AuthResponse response = authService.verifyOtpAndReturnSession(email, otp);
//            return ResponseEntity.ok(response); // return token + user info
//        } catch (RuntimeException ex) {
//            return ResponseEntity.status(400).body(Map.of("error", ex.getMessage()));
//        }
//    }


    /* GET CURRENT USER */
    @GetMapping("/me")
    public ResponseEntity<?> getLoggedInUser(HttpServletRequest request) {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("error", "Missing token"));
        }

        String token = header.substring(7);
        String email = jwtUtil.getEmailFromToken(token);

        return ResponseEntity.ok(authService.getCurrentUser(email));
    }

    @GetMapping("/users/all")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/users/pending")
    public ResponseEntity<?> getPendingUsers() {
        List<User> pending = userRepository.findByStatus(AccountStatus.PENDING);

        Map<String, Object> response = new HashMap<>();
        response.put("count", pending.size());
        response.put("pendingUsers", pending);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/approve/{id}")
    public ResponseEntity<?> approveUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setStatus(AccountStatus.APPROVED);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "User approved successfully"));
    }

    @PutMapping("/revert/{id}")
    public ResponseEntity<?> revertUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setStatus(AccountStatus.PENDING);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "User reverted successfully"));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        userRepository.deleteById(id);
        return ResponseEntity.ok("User deleted successfully");
    }

    @PostMapping("/verify-account")
    public ResponseEntity<?> verifyAccount(
            @RequestBody VerifyAccountRequest request) {

        authService.verifyAccount(
                request.getEmail(),
                request.getAccountNumber());

        return ResponseEntity.ok(
                Map.of(
                        "verified", true,
                        "message", "Account verified successfully."
                ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @RequestBody ForgotPasswordRequest request) {

        authService.resetPasswordUsingAccount(
                request.getEmail(),
                request.getAccountNumber(),
                request.getNewPassword());

        return ResponseEntity.ok(
                Map.of("message", "Password reset successfully."));
    }



}
