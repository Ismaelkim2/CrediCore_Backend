package com.example.Credit.management.services;

import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.Notification;
import com.example.Credit.management.Repository.AccountRepository;
import com.example.Credit.management.Repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private NotificationPushService pushService;

    // =========================
    // CREATE NOTIFICATION (CORRECT)
    // =========================
    public Notification createNotification(Account account, String message, String type) {
        System.out.println("🟡 CREATE NOTIFICATION FOR ACCOUNT: " + account.getId());
        System.out.println("🟡 MESSAGE: " + message);

        Account managedAccount = accountRepository.findById(account.getId())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        Notification n = new Notification();
        n.setAccount(managedAccount);
        n.setMessage(message);
        n.setType(type);
        n.setCreatedAt(LocalDateTime.now());
        n.setRead(false);

        Notification saved = notificationRepository.save(n);

        // 🔥 REAL-TIME PUSH
        pushService.pushNotification(managedAccount.getId(), saved);

        return saved;
    }

    // =========================
    // RECENT NOTIFICATIONS
    // =========================
    public List<Notification> getRecentNotifications() {
        return notificationRepository.findTop10ByOrderByCreatedAtDesc();
    }

    // =========================
    // UNREAD COUNT
    // =========================
    public long getUnreadCount(Long accountId) {
        return notificationRepository.countByAccount_IdAndReadFalse(accountId);
    }

    // =========================
    // MARK AS READ
    // =========================
    public void markAsRead(Long id) {

        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        n.setRead(true);
        notificationRepository.save(n);

        // 🔥 REAL-TIME UPDATE TO FRONTEND
        pushService.sendReadUpdate(n.getId());
    }
}