
package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.ReminderAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReminderAuditLogRepository
        extends JpaRepository<ReminderAuditLog, Long> {

    List<ReminderAuditLog> findByReminderIdOrderByCreatedAtDesc(
            Long reminderId
    );

    List<ReminderAuditLog> findByPerformedByOrderByCreatedAtDesc(
            String performedBy
    );
}
