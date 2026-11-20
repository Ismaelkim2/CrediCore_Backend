
package com.example.Credit.management.Model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "tenant_reminders",
        indexes = {
                @Index(
                        name = "idx_reminder_status_schedule",
                        columnList = "status,scheduled_at"
                ),
                @Index(
                        name = "idx_reminder_tenant",
                        columnList = "tenant_id"
                ),
                @Index(
                        name = "idx_reminder_due_date",
                        columnList = "due_date"
                ),
                @Index(
                        name = "idx_reminder_created_by",
                        columnList = "created_by"
                ),
                @Index(
                        name = "idx_reminder_next_attempt",
                        columnList = "status,next_attempt_at"
                )
        }
)
public class TenantReminder {

    public enum ReminderType {
        RENT_DUE,
        RENT_OVERDUE,
        LEASE_EXPIRY,
        MAINTENANCE,
        DOCUMENT,
        FOLLOW_UP,
        CALENDAR
    }

    public enum ReminderStatus {
        PENDING,
        SCHEDULED,
        PROCESSING,
        SENT,
        DELIVERED,
        FAILED,
        CANCELLED,
        FOLLOWED_UP
    }

    public enum ReminderChannel {
        IN_APP,
        SMS,
        EMAIL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "property_id")
    private Long propertyId;

    @Column(name = "unit_id")
    private Long unitId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReminderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReminderStatus status = ReminderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderChannel channel = ReminderChannel.IN_APP;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "created_by", nullable = false, length = 180)
    private String createdBy;

    @Column(name = "assigned_to", length = 180)
    private String assignedTo;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "idempotency_key", unique = true, length = 200)
    private String idempotencyKey;

    @Column(name = "followed_up_at")
    private LocalDateTime followedUpAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;

        if (status == null) {
            status = ReminderStatus.PENDING;
        }

        if (channel == null) {
            channel = ReminderChannel.IN_APP;
        }

        if (attemptCount < 0) {
            attemptCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getPropertyId() {
        return propertyId;
    }

    public Long getUnitId() {
        return unitId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public ReminderType getType() {
        return type;
    }

    public ReminderStatus getStatus() {
        return status;
    }

    public ReminderChannel getChannel() {
        return channel;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public LocalDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public LocalDateTime getFollowedUpAt() {
        return followedUpAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setTenantId(Long value) {
        this.tenantId = value;
    }

    public void setPropertyId(Long value) {
        this.propertyId = value;
    }

    public void setUnitId(Long value) {
        this.unitId = value;
    }

    public void setTitle(String value) {
        this.title = value;
    }

    public void setMessage(String value) {
        this.message = value;
    }

    public void setType(ReminderType value) {
        this.type = value;
    }

    public void setStatus(ReminderStatus value) {
        this.status = value;
    }

    public void setChannel(ReminderChannel value) {
        this.channel = value;
    }

    public void setDueDate(LocalDate value) {
        this.dueDate = value;
    }

    public void setAmount(BigDecimal value) {
        this.amount = value;
    }

    public void setScheduledAt(LocalDateTime value) {
        this.scheduledAt = value;
    }

    public void setCreatedBy(String value) {
        this.createdBy = value;
    }

    public void setAssignedTo(String value) {
        this.assignedTo = value;
    }

    public void setAttemptCount(int value) {
        this.attemptCount = value;
    }

    public void setLastAttemptAt(LocalDateTime value) {
        this.lastAttemptAt = value;
    }

    public void setNextAttemptAt(LocalDateTime value) {
        this.nextAttemptAt = value;
    }

    public void setLastError(String value) {
        this.lastError = value;
    }

    public void setIdempotencyKey(String value) {
        this.idempotencyKey = value;
    }

    public void setFollowedUpAt(LocalDateTime value) {
        this.followedUpAt = value;
    }

    public void setCancelledAt(LocalDateTime value) {
        this.cancelledAt = value;
    }
}
