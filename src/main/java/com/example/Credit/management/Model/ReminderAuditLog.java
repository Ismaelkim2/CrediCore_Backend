package com.example.Credit.management.Model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reminder_audit_logs")
public class ReminderAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reminder_id", nullable = false)
    private Long reminderId;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "performed_by", nullable = false, length = 180)
    private String performedBy;

    @Column(length = 1000)
    private String details;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ReminderAuditLog() {}

    public ReminderAuditLog(
            Long reminderId, String action,
            String performedBy, String details) {
        this.reminderId = reminderId;
        this.action = action;
        this.performedBy = performedBy;
        this.details = details;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getReminderId() { return reminderId; }
    public String getAction() { return action; }
    public String getPerformedBy() { return performedBy; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}