package com.example.Credit.management.services;

import com.example.Credit.management.Model.Notification;
import com.example.Credit.management.Repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    public Notification createNotification(
            Long loanId,
            String message,
            String type,
            String createdBy
    ) {
        Notification n = new Notification();
        n.setLoanId(loanId);
        n.setMessage(message);
        n.setType(type);
        n.setCreatedAt(LocalDateTime.now());
        n.setCreatedBy(createdBy);

        return notificationRepository.save(n);
    }

    public List<Notification> getRecentNotifications() {
        return notificationRepository.findTop10ByOrderByCreatedAtDesc();
    }
}
