
package com.example.Credit.management.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TenantRentSchedule {

    private static final Logger log =
            LoggerFactory.getLogger(TenantRentSchedule.class);

    private final TenantRentService rentService;

    public TenantRentSchedule(TenantRentService rentService) {
        this.rentService = rentService;
    }

    @Scheduled(
            cron = "0 0 0 * * *",
            zone = "Africa/Nairobi"
    )

    public void generateMonthlyRent() {
        try {
            int generated = rentService.generateRentCharges();

            log.info(
                    "Rent generation completed. New charges created: {}",
                    generated
            );

        } catch (Exception ex) {
            log.error(
                    "Scheduled rent generation failed. It will retry on the next run.",
                    ex
            );
        }
    }
}
