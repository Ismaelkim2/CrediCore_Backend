package com.example.Credit.management.services;

import com.example.Credit.management.Model.Loan;
import org.springframework.stereotype.Service;

import java.util.List;

@Service

public class BehavioralAnalysisService {

    public int calculateBehavioralScore(List<Loan> loans) {

        if (loans.isEmpty()) {
            return 50;
        }

        double totalRatio = loans.stream()
                .mapToDouble(loan -> {

                    double amount = loan.getAmount();
                    double paid = loan.getAmountPaid();

                    if (amount <= 0) {
                        return 0;
                    }

                    return (paid / amount) * 100;

                })
                .average()
                .orElse(0);

        int score = (int) Math.round(totalRatio);

        return Math.max(0, Math.min(score, 100));
    }

    public int calculateRepaymentDiscipline(
            List<Loan> loans
    ) {

        int score = 50;

        for (Loan loan : loans) {

            int repayments =
                    loan.getRepayments() == null
                            ? 0
                            : loan.getRepayments().size();

            if (repayments >= 5) {

                score += 5;

            } else if (repayments == 0) {

                score -= 5;
            }
        }

        return Math.max(0,
                Math.min(score, 100));
    }
}
