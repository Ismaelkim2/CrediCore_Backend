package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.LoanDecisionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanDecisionAuditRepository
        extends JpaRepository<LoanDecisionAudit, Long> {
}