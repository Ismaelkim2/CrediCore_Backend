package com.example.Credit.management.services;

import com.example.Credit.management.Model.LoanSettings;
import com.example.Credit.management.Repository.LoanSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LoanSettingsService implements SystemSettingsService {

    @Autowired
    private LoanSettingsRepository repository;

    public LoanSettings save(LoanSettings setting) {
        Optional<LoanSettings> existing = repository.findByDurationInWeeks(setting.getDurationInWeeks());
        if (existing.isPresent()) {
            setting.setId(existing.get().getId()); // update instead of insert
        }
        return repository.save(setting);
    }

    @Override
    public LoanSettings getSettings() {
        return repository.findAll().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("System settings not configured."));
    }

    @Override
    public LoanSettings updateSettings(LoanSettings settings) {
        settings.setId(getSettings().getId()); // preserve ID
        return repository.save(settings);
    }

    public List<LoanSettings> getAll() {
        return repository.findAll();
    }

    public Optional<LoanSettings> getByDuration(int weeks) {
        return repository.findByDurationInWeeks(weeks);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    /**
     * Get loan settings based on parsed duration string.
     * Supports "2 weeks", "1 month", etc.
     */
    public LoanSettings getSettingsByDuration(String duration) {
        int weeks = parseWeeks(duration);
        return repository.findByDurationInWeeks(weeks)
                .orElseThrow(() -> new RuntimeException("No loan settings found for duration: " + duration));
    }

    /**
     * Finds LoanSettings by duration in weeks.
     * Used by LoanServiceImpl to fetch applicable settings.
     */
    public LoanSettings findByDurationInWeeks(int weeks) {
        return repository.findByDurationInWeeks(weeks)
                .orElseThrow(() -> new RuntimeException("No loan settings found for duration: " + weeks + " weeks"));
    }

    /**
     * Helper to parse duration string into number of weeks.
     */
    private int parseWeeks(String duration) {
        try {
            String lower = duration.toLowerCase();
            if (lower.contains("week")) {
                return Integer.parseInt(duration.replaceAll("[^0-9]", ""));
            } else if (lower.contains("month")) {
                int months = Integer.parseInt(duration.replaceAll("[^0-9]", ""));
                return months * 4; // approximate 4 weeks per month
            } else {
                throw new IllegalArgumentException("Unsupported duration format: " + duration);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid duration format. Expected '2 weeks', '1 month', etc.");
        }
    }
}
