package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.LoanSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface LoanSettingsRepository extends JpaRepository<LoanSettings, Long> {
    Optional<LoanSettings> findByDurationInWeeks(int durationInWeeks);
}
