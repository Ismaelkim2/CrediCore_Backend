package com.example.Credit.management.services;

import com.example.Credit.management.Model.Account;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.Optional;


@Service
public class LoanRiskService {

    public double calculateLoanLimit(Account account) {

        int score = 0;

        long days = ChronoUnit.DAYS.between(
                account.getCreatedAt().toLocalDate(),
                LocalDate.now()
        );

        // ✅ Always give base points
        score += 20;

        if (days >= 90) score += 30;
        else if (days >= 30) score += 20;
        else if (days >= 2) score += 15;

        // ✅ Ignore missing data penalties during testing
        double turnover = Optional.ofNullable(account.getAverageMonthlyTurnover()).orElse(100_000.0);
        if (turnover >= 200_000) score += 15;

        if (Boolean.TRUE.equals(account.getGoodCreditStanding())) score += 10;

        double collateral = Optional.ofNullable(account.getCollateralValue()).orElse(0.0);
        score += Math.min((int) (collateral / 1_000_000) * 10, 20);

        // ✅ LOWER THRESHOLDS FOR TESTING
        if (score >= 60) return 1_000_000;
        if (score >= 40) return 200_000;
        if (score >= 25) return 50_000;

        return 0;
    }

}

