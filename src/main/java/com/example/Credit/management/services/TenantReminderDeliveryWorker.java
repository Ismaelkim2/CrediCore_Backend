
package com.example.Credit.management.services;

import com.example.Credit.management.Model.TenantReminder;
import com.example.Credit.management.Repository.TenantReminderRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class TenantReminderDeliveryWorker {

    private static final Logger log =
            LoggerFactory.getLogger(TenantReminderDeliveryWorker.class);

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;

    private static final List<TenantReminder.ReminderStatus> ELIGIBLE_STATUSES =
            List.of(
                    TenantReminder.ReminderStatus.PENDING,
                    TenantReminder.ReminderStatus.SCHEDULED,
                    TenantReminder.ReminderStatus.FAILED
            );

    private final TenantReminderRepository reminderRepository;
    private final ObjectProvider<ReminderDeliveryProvider> provider;

    @Value("${tenant.reminders.delivery.enabled:false}")
    private boolean deliveryEnabled;

    @Value("${tenant.reminders.delivery.processing-timeout-minutes:30}")
    private long processingTimeoutMinutes;

    public TenantReminderDeliveryWorker(
            TenantReminderRepository reminderRepository,
            ObjectProvider<ReminderDeliveryProvider> provider
    ) {
        this.reminderRepository = reminderRepository;
        this.provider = provider;
    }

    @Scheduled(
            fixedDelayString = "${tenant.reminders.delivery.delay-ms:30000}"
    )
    public void processReadyReminders() {

        if (!deliveryEnabled) {
            return;
        }

        ReminderDeliveryProvider deliveryProvider =
                provider.getIfAvailable();

        if (deliveryProvider == null) {
            log.warn(
                    "Reminder delivery is enabled, but no delivery provider is configured."
            );
            return;
        }

        recoverAbandonedReminders();

        List<TenantReminder> ready = reminderRepository
                .findReadyForProcessing(
                        ELIGIBLE_STATUSES,
                        LocalDateTime.now(),
                        TenantReminder.ReminderType.CALENDAR,
                        PageRequest.of(0, BATCH_SIZE)
                );

        for (TenantReminder reminder : ready) {

            try {
                processOne(reminder.getId(), deliveryProvider);

            } catch (Exception exception) {

                log.error(
                        "Unexpected error processing reminder id={}",
                        reminder.getId(),
                        exception
                );
            }
        }
    }

    /**
     * Recover reminders interrupted by an application crash
     * or an unexpected worker shutdown.
     */
    private void recoverAbandonedReminders() {

        LocalDateTime now = LocalDateTime.now();

        long timeoutMinutes = Math.max(
                10,
                Math.min(processingTimeoutMinutes, 1440)
        );

        LocalDateTime cutoff = now.minusMinutes(timeoutMinutes);

        try {

            int recovered = reminderRepository.recoverAbandonedReminders(
                    TenantReminder.ReminderStatus.PROCESSING,
                    TenantReminder.ReminderStatus.FAILED,
                    cutoff,
                    now,
                    MAX_ATTEMPTS
            );

            if (recovered > 0) {
                log.warn(
                        "Recovered {} abandoned tenant reminders.",
                        recovered
                );
            }

        } catch (Exception exception) {

            log.error(
                    "Failed to recover abandoned tenant reminders.",
                    exception
            );
        }
    }

    /**
     * Atomically claims one reminder before sending.
     * External provider calls happen outside a database transaction.
     */
    public void processOne(
            Long reminderId,
            ReminderDeliveryProvider deliveryProvider
    ) {

        LocalDateTime now = LocalDateTime.now();

        int claimed = reminderRepository.claimReminder(
                reminderId,
                ELIGIBLE_STATUSES,
                TenantReminder.ReminderStatus.PROCESSING,
                now,
                MAX_ATTEMPTS
        );

        if (claimed != 1) {
            return;
        }

        // Reload after the bulk update because JPQL bulk updates
        // bypass the JPA persistence context.
        TenantReminder reminder = reminderRepository.findById(reminderId)
                .orElse(null);

        if (reminder == null) {
            log.error(
                    "Claimed reminder could not be reloaded. id={}",
                    reminderId
            );
            return;
        }

        try {

            deliveryProvider.send(reminder);

            // Provider acceptance means SENT, not necessarily DELIVERED.
            reminder.setStatus(TenantReminder.ReminderStatus.SENT);
            reminder.setLastError(null);
            reminder.setNextAttemptAt(null);

        } catch (Exception exception) {

            int attempts = reminder.getAttemptCount();

            reminder.setStatus(TenantReminder.ReminderStatus.FAILED);

            reminder.setLastError(truncate(
                    exception.getMessage() == null
                            ? "Delivery failed."
                            : exception.getMessage(),
                    1000
            ));

            if (attempts >= MAX_ATTEMPTS) {

                reminder.setNextAttemptAt(null);

            } else {

                long delayMinutes = Math.min(
                        360,
                        1L << Math.min(attempts, 8)
                );

                reminder.setNextAttemptAt(
                        LocalDateTime.now().plusMinutes(delayMinutes)
                );
            }

            log.warn(
                    "Reminder delivery failed. id={}, attempt={}",
                    reminder.getId(),
                    attempts
            );
        }

        reminderRepository.save(reminder);
    }

    private String truncate(String value, int maxLength) {

        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }
}
