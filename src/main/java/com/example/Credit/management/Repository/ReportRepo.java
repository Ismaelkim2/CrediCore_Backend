package com.example.Credit.management.Repository;

import com.example.Credit.management.DTO.LoanReportDTO;
import com.example.Credit.management.DTO.OverdueLoanDTO;
import com.example.Credit.management.Model.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReportRepo extends JpaRepository<Loan, Long> {

    @Query("""
        SELECT new com.example.Credit.management.DTO.LoanReportDTO(
            l.id, l.applicantName, l.amount, 0.0,
            l.totalRepayableAmount, l.status, l.phoneNumber
        )
        FROM Loan l
        WHERE (:startDate IS NULL OR l.applicationDate >= :startDate)
          AND (:endDate IS NULL OR l.applicationDate <= :endDate)
    """)
    List<LoanReportDTO> findApplications(@Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT new com.example.Credit.management.DTO.LoanReportDTO(
            l.id, l.applicantName, l.amount, 0.0,
            l.totalRepayableAmount, l.status, l.phoneNumber
        )
        FROM Loan l
        WHERE l.status = 'APPROVED'
          AND (:startDate IS NULL OR l.dueDate >= :startDate)
          AND (:endDate IS NULL OR l.dueDate <= :endDate)
    """)
    List<LoanReportDTO> findApprovedLoans(@Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT new com.example.Credit.management.DTO.OverdueLoanDTO(
            l.id, l.applicantName, l.amount, l.dueDate)
        FROM Loan l
        WHERE l.dueDate < CURRENT_DATE
          AND l.status != 'PAID'
    """)
    List<OverdueLoanDTO> findOverdueLoans();

    @Query("""
        SELECT new com.example.Credit.management.DTO.OverdueLoanDTO(
            l.id, l.applicantName, l.amount, l.dueDate)
        FROM Loan l
        WHERE l.dueDate < CURRENT_DATE
          AND l.status != 'PAID'
          AND (:startDate IS NULL OR l.dueDate >= :startDate)
          AND (:endDate IS NULL OR l.dueDate <= :endDate)
    """)
    List<OverdueLoanDTO> findOverdueLoans(@Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT new com.example.Credit.management.DTO.LoanReportDTO(
            l.id, l.applicantName, l.amount, 0.0,
            l.totalRepayableAmount, l.status, l.phoneNumber
        )
        FROM Loan l
        WHERE l.status = 'REJECTED'
          AND (:startDate IS NULL OR l.applicationDate >= :startDate)
          AND (:endDate IS NULL OR l.applicationDate <= :endDate)
    """)
    List<LoanReportDTO> findRejectedLoans(@Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);

    @Query("""
    SELECT new com.example.Credit.management.DTO.LoanReportDTO(
        l.id,
        l.applicantName,
        l.amount,
        COALESCE(SUM(r.amountPaid), 0.0),
        l.totalRepayableAmount,
        l.status,
        l.phoneNumber
    )
    FROM Loan l
    LEFT JOIN l.repayments r
    GROUP BY l.id, l.applicantName, l.amount, l.totalRepayableAmount, l.status, l.phoneNumber
""")
    List<LoanReportDTO> findRawRepayments();


}
