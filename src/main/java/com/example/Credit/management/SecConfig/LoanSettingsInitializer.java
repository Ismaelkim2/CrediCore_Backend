package com.example.Credit.management.SecConfig;

import com.example.Credit.management.Model.LoanSettings;
import com.example.Credit.management.services.LoanSettingsService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class LoanSettingsInitializer {

    private final LoanSettingsService loanSettingsService;

    public LoanSettingsInitializer(LoanSettingsService loanSettingsService) {
        this.loanSettingsService = loanSettingsService;
    }

    @PostConstruct
    public void init() {
        try {
            loanSettingsService.getSettings();  // Assuming this throws if no settings exist
        } catch (RuntimeException e) {
            LoanSettings defaultSettings = new LoanSettings();
            defaultSettings.setDurationInWeeks(4);        // Set to desired default, e.g., 4 weeks
            defaultSettings.setInterestRate(5.0);
            defaultSettings.setPenaltyRate(2.0);
            defaultSettings.setGracePeriodDays(10);
            defaultSettings.setMaxLoanAmount(10000);
            defaultSettings.setAutoApproval(false);

            loanSettingsService.save(defaultSettings);
        }
    }
}
