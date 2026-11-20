package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.TenantReminder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // =====================================================
    // CALENDAR: REMINDERS WITHIN A DATE RANGE
    // =====================================================

    List<TenantReminder>
    findByCreatedByAndDueDateBetweenOrderByDueDateAscScheduledAtAsc(
            String createdBy,
            LocalDate from,
            LocalDate to
    );

    // =====================================================
    // DELIVERY WORKER: EXCLUDE PERSONAL CALENDAR EVENTS
    // =====================================================

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
}