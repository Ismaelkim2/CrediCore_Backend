package com.example.Credit.management.services;

import com.example.Credit.management.Model.Account;
import org.springframework.stereotype.Service;

@Service
public class EligibilityService {

    public double calculateEligibleLimit(
            Account account,
            int riskScore,
            double monthlyIncome
    ) {

        // =====================================================
        // BASE MULTIPLIER
        // =====================================================

        double multiplier;

        if (riskScore >= 85) {

            multiplier = 0.60;

        } else if (riskScore >= 70) {

            multiplier = 0.45;

        } else if (riskScore >= 50) {

            multiplier = 0.25;

        } else {

            multiplier = 0.10;
        }

        // =====================================================
        // BASE ELIGIBLE AMOUNT
        // =====================================================

        double eligibleAmount =
                monthlyIncome * multiplier;

        // =====================================================
        // ACCOUNT BALANCE BONUS
        // =====================================================

        if (account != null && account.getBalance() != null) {

            double balance =
                    account.getBalance();

            // Strong balance bonus

            if (balance >= 500000) {

                eligibleAmount += 150000;

            } else if (balance >= 200000) {

                eligibleAmount += 75000;

            } else if (balance >= 100000) {

                eligibleAmount += 30000;
            }
        }

        // =====================================================
        // SAFETY LIMITS
        // =====================================================

        if (eligibleAmount < 1000) {

            eligibleAmount = 1000;
        }

        if (eligibleAmount > 5000000) {

            eligibleAmount = 5000000;
        }

        // =====================================================
        // ROUNDING
        // =====================================================

        return Math.round(eligibleAmount);
    }
}