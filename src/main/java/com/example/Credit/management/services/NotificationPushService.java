package com.example.Credit.management.services;

import com.example.Credit.management.Model.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationPushService {

    private final SimpMessagingTemplate messagingTemplate;

    public void pushNotification(Long accountId, Notification notification) {

        messagingTemplate.convertAndSend(
                "/topic/notifications",
                notification
        );

    }

    public void sendReadUpdate(Long notificationId) {

        messagingTemplate.convertAndSend(
                "/topic/notifications/read",
                notificationId
        );
    }


    public void sendDeleteUpdate(Long id) {
        messagingTemplate.convertAndSend(
                "/topic/notifications/delete",
                id
        );
    }

}