package com.example.Credit.management.controller;

import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.Notification;
import com.example.Credit.management.Repository.AccountRepository;
import com.example.Credit.management.Repository.NotificationRepository;
import com.example.Credit.management.services.NotificationPushService;
import com.example.Credit.management.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;
    private final NotificationPushService notificationPushService;

    // =========================
    // GET MY NOTIFICATIONS
    // =========================
    @GetMapping("/my")
    public ResponseEntity<List<Notification>> getMyNotifications(
            Authentication authentication
    ) {

        String email = authentication.getName();

        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        List<Notification> notifications =
                notificationRepository.findTop10ByAccount_IdOrderByCreatedAtDesc(
                        account.getId()
                );

        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/all")
    public ResponseEntity<List<Notification>> getAllNotifications() {

        return ResponseEntity.ok(
                notificationRepository.findTop50ByOrderByCreatedAtDesc()
        );
    }

    // =========================
    // UNREAD COUNT
    // =========================
    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(Authentication authentication) {

        String email = authentication.getName();

        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        return ResponseEntity.ok(
                notificationService.getUnreadCount(account.getId())
        );
    }

    // =========================
    // MARK AS READ
    // =========================
    @PutMapping("/{id}/read")
    public ResponseEntity<String> markAsRead(@PathVariable Long id) {

        notificationService.markAsRead(id);
        return ResponseEntity.ok("Notification marked as read");
    }

    // =========================
    // MARK ALL AS READ
    // =========================
    @PutMapping("/read-all")
    public ResponseEntity<String> markAllAsRead(Authentication authentication) {

        String email = authentication.getName();

        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        List<Notification> notifications =
                notificationRepository.findByAccount_IdAndReadFalseOrderByCreatedAtDesc(
                        account.getId()
                );

        notifications.forEach(n -> n.setRead(true));

        notificationRepository.saveAll(notifications);

        return ResponseEntity.ok("All notifications marked as read");
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteNotification(@PathVariable Long id) {

        notificationRepository.deleteById(id);

        notificationPushService.sendDeleteUpdate(id); // 🔥 ADD THIS

        return ResponseEntity.ok("Notification deleted successfully");
    }


    @GetMapping("/preferences/sound")
    public ResponseEntity<Boolean> getSoundPreference(Authentication authentication) {

        String email = authentication.getName();

        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(
                account.getNotificationSoundEnabled() != null
                        ? account.getNotificationSoundEnabled()
                        : true
        );
    }

    @PutMapping("/preferences/sound")
    public ResponseEntity<String> updateSoundPreference(
            Authentication authentication,
            @RequestBody Boolean soundEnabled
    ) {

        System.out.println("SOUND ENABLED RECEIVED = " + soundEnabled);

        String email = authentication.getName();

        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        System.out.println("BEFORE SAVE = "
                + account.getNotificationSoundEnabled());

        account.setNotificationSoundEnabled(soundEnabled);

        Account saved = accountRepository.saveAndFlush(account);

        System.out.println("AFTER SAVE = "
                + saved.getNotificationSoundEnabled());

        return ResponseEntity.ok("Notification preference updated");
    }
}