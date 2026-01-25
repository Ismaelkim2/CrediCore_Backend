package com.example.Credit.management.Repository;

import com.example.Credit.management.DTO.RepaymentTransactionDTO;
import com.example.Credit.management.Model.Loan;
import com.example.Credit.management.Model.Repayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public interface RepaymentRepository extends JpaRepository<Repayment, Long> {

    // Get repayments within a specific date range
    List<Repayment> findByRepaymentDateBetween(LocalDate start, LocalDate end);
    List<Repayment> findByLoanId(Long loanId);


    // Projection for reporting: loan ID, amount paid, date, etc.
    @Query("""
        SELECT new map(
            r.loan.id as loanId,
            r.amountPaid as amountPaid,
            r.repaymentDate as repaymentDate,
            r.phoneNumber as phoneNumber,
            r.location as location
        )
        FROM Repayment r
        WHERE r.repaymentDate BETWEEN :startDate AND :endDate
    """)
    List<Map<String, Object>> findLoanRepayments(LocalDate startDate, LocalDate endDate);

    // Total amount paid toward a specific loan
    @Query("SELECT COALESCE(SUM(r.amountPaid), 0) FROM Repayment r WHERE r.loan.id = :loanId")
    BigDecimal findTotalPaidByLoanId(Long loanId);

    // Fetch loans that are overdue and either partially paid or unpaid
    @Query("SELECT l FROM Loan l " +
            "WHERE l.dueDate <= :today " +
            "AND (l.status = 'Pending' OR l.status = 'Partial')")
    List<Loan> findDefaulters(@Param("today") LocalDate today);


    @Query("""
SELECT new com.example.Credit.management.DTO.RepaymentTransactionDTO(
    r.repaymentDate,
    r.amountPaid,
    r.paymentMethod
)
FROM Repayment r
WHERE r.loan.id = :loanId
ORDER BY r.repaymentDate DESC
""")
    List<RepaymentTransactionDTO> findTransactionsByLoanId(@Param("loanId") Long loanId);

}
