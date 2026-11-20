package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop10ByAccount_IdOrderByCreatedAtDesc(Long accountId);

    List<Notification> findTop10ByOrderByCreatedAtDesc();

    List<Notification> findTop50ByOrderByCreatedAtDesc();

    long countByAccount_IdAndReadFalse(Long accountId);

    List<Notification> findByAccount_IdAndReadFalseOrderByCreatedAtDesc(Long accountId);
}