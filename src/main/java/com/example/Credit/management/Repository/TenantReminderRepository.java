
package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.TenantReminder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TenantReminderRepository
        extends JpaRepository<TenantReminder, Long> {

    Optional<TenantReminder> findByIdAndCreatedBy(
            Long id,
            String createdBy
    );

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<TenantReminder> findByCreatedByOrderByCreatedAtDesc(
            String createdBy,
            Pageable pageable
    );

    List<TenantReminder>
    findByCreatedByAndDueDateBetweenOrderByDueDateAscScheduledAtAsc(
            String createdBy,
            LocalDate from,
            LocalDate to
    );

    @Query("""
        SELECT r FROM TenantReminder r
        WHERE r.type <> :excludedType
          AND r.status IN :statuses
          AND (r.scheduledAt IS NULL OR r.scheduledAt <= :now)
          AND (r.nextAttemptAt IS NULL OR r.nextAttemptAt <= :now)
        ORDER BY r.createdAt ASC
        """)
    List<TenantReminder> findReadyForProcessing(
            @Param("statuses") List<TenantReminder.ReminderStatus> statuses,
            @Param("now") LocalDateTime now,
            @Param("excludedType") TenantReminder.ReminderType excludedType,
            Pageable pageable
    );

    @Query("""
        SELECT COUNT(r) FROM TenantReminder r
        WHERE r.createdBy = :actor
          AND r.status = :status
        """)
    long countByCreatorAndStatus(
            @Param("actor") String actor,
            @Param("status") TenantReminder.ReminderStatus status
    );

    Page<TenantReminder> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<TenantReminder>
    findByTypeAndDueDateBetweenOrderByDueDateAscScheduledAtAsc(
            TenantReminder.ReminderType type,
            LocalDate from,
            LocalDate to
    );

    // =====================================================
    // ATOMIC DELIVERY CLAIM
    // =====================================================

    @Modifying
    @Transactional
    @Query("""
        UPDATE TenantReminder r
        SET r.status = :processingStatus,
            r.lastAttemptAt = :now,
            r.attemptCount = r.attemptCount + 1,
            r.lastError = null
        WHERE r.id = :reminderId
          AND r.status IN :eligibleStatuses
          AND (r.scheduledAt IS NULL OR r.scheduledAt <= :now)
          AND (r.nextAttemptAt IS NULL OR r.nextAttemptAt <= :now)
          AND r.attemptCount < :maxAttempts
        """)
    int claimReminder(
            @Param("reminderId") Long reminderId,
            @Param("eligibleStatuses") List<TenantReminder.ReminderStatus> eligibleStatuses,
            @Param("processingStatus") TenantReminder.ReminderStatus processingStatus,
            @Param("now") LocalDateTime now,
            @Param("maxAttempts") int maxAttempts
    );

    // =====================================================
    // RECOVER ABANDONED DELIVERY ATTEMPTS
    // =====================================================

    @Modifying
    @Transactional
    @Query("""
        UPDATE TenantReminder r
        SET r.status = :failedStatus,
            r.nextAttemptAt = CASE
                WHEN r.attemptCount < :maxAttempts THEN :now
                ELSE null
            END,
            r.lastError = 'Previous delivery attempt was interrupted.',
            r.updatedAt = :now,
            r.version = r.version + 1
        WHERE r.status = :processingStatus
          AND r.lastAttemptAt IS NOT NULL
          AND r.lastAttemptAt < :cutoff
        """)
    int recoverAbandonedReminders(
            @Param("processingStatus") TenantReminder.ReminderStatus processingStatus,
            @Param("failedStatus") TenantReminder.ReminderStatus failedStatus,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now,
            @Param("maxAttempts") int maxAttempts
    );
}
