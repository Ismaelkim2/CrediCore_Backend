
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class TenantReminderDeliveryWorker {

    private static final Logger log =
            LoggerFactory.getLogger(TenantReminderDeliveryWorker.class);

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;

    private final TenantReminderRepository reminderRepository;
    private final ObjectProvider<ReminderDeliveryProvider> provider;

    @Value("${tenant.reminders.delivery.enabled:false}")
    private boolean deliveryEnabled;

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

        List<TenantReminder> ready = reminderRepository
                .findReadyForProcessing(
                        List.of(
                                TenantReminder.ReminderStatus.PENDING,
                                TenantReminder.ReminderStatus.SCHEDULED,
                                TenantReminder.ReminderStatus.FAILED
                        ),
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

    @Transactional
    public void processOne(
            Long reminderId,
            ReminderDeliveryProvider deliveryProvider
    ) {
        TenantReminder reminder = reminderRepository.findById(reminderId)
                .orElse(null);

        if (reminder == null) {
            return;
        }

        if (reminder.getStatus() != TenantReminder.ReminderStatus.PENDING
                && reminder.getStatus()
                != TenantReminder.ReminderStatus.SCHEDULED
                && reminder.getStatus()
                != TenantReminder.ReminderStatus.FAILED) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        if (reminder.getScheduledAt() != null
                && reminder.getScheduledAt().isAfter(now)) {
            return;
        }

        if (reminder.getNextAttemptAt() != null
                && reminder.getNextAttemptAt().isAfter(now)) {
            return;
        }

        if (reminder.getAttemptCount() >= MAX_ATTEMPTS) {
            reminder.setStatus(TenantReminder.ReminderStatus.FAILED);
            reminder.setNextAttemptAt(null);
            reminder.setLastError(
                    "Maximum delivery attempts reached."
            );
            reminderRepository.save(reminder);
            return;
        }

        // Claim the record before attempting delivery.
        reminder.setStatus(TenantReminder.ReminderStatus.PROCESSING);
        reminder.setLastAttemptAt(now);
        reminder.setAttemptCount(reminder.getAttemptCount() + 1);
        reminder.setLastError(null);

        reminderRepository.saveAndFlush(reminder);

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
