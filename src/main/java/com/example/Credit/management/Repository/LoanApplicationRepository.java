package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {
    List<LoanApplication> findByApplicationDateBetween(LocalDate start, LocalDate end);
    List<LoanApplication> findByStatusAndApplicationDateBetween(String status, LocalDate start, LocalDate end);
}
