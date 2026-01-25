package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {

    Optional<Loan> findTopByIdNoOrderByIdDesc(String idNo);
    Optional<Loan> findTopByPhoneNumberOrderByIdDesc(Long phoneNumber);
    List<Loan> findByIdNo(String idNo);
    List<Loan> findByDueDateBeforeAndDueAmountGreaterThan(LocalDate date, double amount);


//    Optional<Loan> findTopByPhoneNumberOrderByIdDesc(Long phone);
//    Loan findTopByIdNoOrderByApplicationDateDesc(Long idNo);




    List<Loan> findByDisbursedDateBetween(LocalDate start, LocalDate end);

    @Query("SELECT new map(l.id as id, l.applicantName as name, l.amount as amount, l.status as status, l.applicationDate as date) " +
            "FROM Loan l WHERE l.applicationDate BETWEEN :startDate AND :endDate")
    List<Map<String, Object>> findApplicationsBetweenDates(LocalDate startDate, LocalDate endDate);

    @Query("SELECT new map(l.id as id, l.applicantName as name, l.amount as amount, l.status as status, l.disbursedDate as disbursedAt) " +
            "FROM Loan l WHERE l.status = 'disbursed' AND l.disbursedDate BETWEEN :startDate AND :endDate")
    List<Map<String, Object>> findDisbursedLoans(LocalDate startDate, LocalDate endDate);

    @Query("SELECT new map(l.applicantName as name, l.phoneNumber as phoneNumber, l.location as location, l.amount as amount, l.dueDate as dueDate, l.totalRepayableAmount as totalRepayableAmount, l.status as status) " +
            "FROM Loan l WHERE l.dueDate < :today")
    List<Map<String, Object>> findOverdueLoans(LocalDate today);

    @Query("SELECT new map(l.applicantName as name, l.phoneNumber as phoneNumber, l.location as location, l.amount as amount, l.dueDate as dueDate, l.totalRepayableAmount as totalRepayableAmount, l.status as status) " +
            "FROM Loan l WHERE l.dueDate < :today")
    List<Map<String, Object>> findLoanRepayments(LocalDate today, LocalDate endDate);


    @Query("SELECT new map(SUM(l.amount) as totalDisbursed) " +
            "FROM Loan l WHERE l.status = 'repaid'")
    List<Map<String, Object>> calculateRevenue();

    @Query("SELECT new map(l.id as id, l.applicantName as name, l.amount as amount, l.status as status, l.disbursedDate as updatedDate) " +
            "FROM Loan l WHERE l.status = :status AND l.disbursedDate BETWEEN :startDate AND :endDate")
    List<Map<String, Object>> findByStatusBetweenDates(String status, LocalDate startDate, LocalDate endDate);

    @Query("SELECT COUNT(l) FROM Loan l WHERE l.idNo = :idNo AND l.status NOT IN ('repaid', 'rejected')")
    long countActiveLoansByIdNo(String idNo);

    @Query("""
SELECT l FROM Loan l 
WHERE l.idNo = :idNo 
AND l.status IN ('APPROVED','PARTIAL','AWAITING_AUTHORIZATION')
ORDER BY l.applicationDate DESC
""")
    Optional<Loan> findActiveLoanByIdNo(@Param("idNo") String idNo);

    Optional<Loan> findTopByIdNoOrderByApplicationDateDesc(String idNo);



    @Query("""
SELECT l FROM Loan l
LEFT JOIN l.repayments r
WHERE l.dueDate IS NOT NULL
AND l.dueDate < :today
AND LOWER(l.status) NOT IN ('repaid', 'rejected')
GROUP BY l
HAVING COALESCE(SUM(r.amountPaid), 0) < l.totalRepayableAmount
""")
    List<Loan> findDefaulters(@Param("today") LocalDate today);

    @Query("SELECT l FROM Loan l LEFT JOIN FETCH l.account")
    List<Loan> findAllWithAccount();


    @Query("SELECT l FROM Loan l LEFT JOIN FETCH l.account WHERE l.id = :id")
    Optional<Loan> findByIdWithAccount(@Param("id") Long id);


    long countByIdNoAndStatusIn(String idNo, List<String> statuses);


}
