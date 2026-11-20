
package com.example.Credit.management.services;

import com.example.Credit.management.Model.TenantReminder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RentReminderScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(RentReminderScheduler.class);

    private final AutomaticRentReminderService automaticReminderService;

    @Value("${tenant.reminders.automation.enabled:false}")
    private boolean automationEnabled;

    @Value("${tenant.reminders.automation.actor:}")
    private String automationActor;

    public RentReminderScheduler(
            AutomaticRentReminderService automaticReminderService
    ) {
        this.automaticReminderService = automaticReminderService;
    }

    /**
     * Runs every day at 08:00 using the application's configured timezone.
     * Disabled by default until an automation actor is configured.
     */
    @Scheduled(cron = "${tenant.reminders.automation.cron:0 0 8 * * *}")
    public void generateDailyRentReminders() {

        if (!automationEnabled) {
            log.debug("Automatic rent reminders are disabled.");
            return;
        }

        if (automationActor == null || automationActor.isBlank()) {
            log.error(
                    "Automatic rent reminders are enabled, but no automation actor is configured."
            );
            return;
        }

        generate("DUE_SOON", 7);
        generate("DUE_TODAY", 7);
        generate("OVERDUE", 7);
    }

    private void generate(String category, int daysAhead) {
        try {
            int created = automaticReminderService.generate(
                    automationActor,
                    category,
                    daysAhead,
                    TenantReminder.ReminderChannel.IN_APP
            );

            log.info(
                    "Rent reminder generation completed. category={}, created={}",
                    category,
                    created
            );

        } catch (Exception exception) {
            log.error(
                    "Rent reminder generation failed for category={}",
                    category,
                    exception
            );
        }
    }
}
