package com.example.Credit.management.services;

import com.example.Credit.management.Model.LoanSettings;

public interface SystemSettingsService {
    LoanSettings getSettings();
    LoanSettings updateSettings(LoanSettings settings);
}
