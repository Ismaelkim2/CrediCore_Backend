package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.Account;
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

    // Find the latest loan by phone number (if needed)
    Optional<Loan> findTopByPhoneNumberOrderByIdDesc(String phoneNumber);

    Optional<Loan> findTopByAccountOrderByApplicationDateDesc(Account account);

    // LoanRepository.java
    List<Loan> findByAccount(Account account);

    // Find loans that are overdue
    List<Loan> findByDueDateBeforeAndDueAmountGreaterThan(LocalDate date, double amount);

    // Loans disbursed within a period
    List<Loan> findByDisbursedDateBetween(LocalDate start, LocalDate end);

    // Applications between dates
    @Query("SELECT new map(l.id as id, l.applicantName as name, l.amount as amount, l.status as status, l.applicationDate as date) " +
            "FROM Loan l WHERE l.applicationDate BETWEEN :startDate AND :endDate")
    List<Map<String, Object>> findApplicationsBetweenDates(LocalDate startDate, LocalDate endDate);

    // Disbursed loans between dates
    @Query("SELECT new map(l.id as id, l.applicantName as name, l.amount as amount, l.status as status, l.disbursedDate as disbursedAt) " +
            "FROM Loan l WHERE l.status = 'disbursed' AND l.disbursedDate BETWEEN :startDate AND :endDate")
    List<Map<String, Object>> findDisbursedLoans(LocalDate startDate, LocalDate endDate);

    // Overdue loans
    @Query("SELECT new map(l.applicantName as name, l.phoneNumber as phoneNumber, l.location as location, l.amount as amount, l.dueDate as dueDate, l.totalRepayableAmount as totalRepayableAmount, l.status as status) " +
            "FROM Loan l WHERE l.dueDate < :today")
    List<Map<String, Object>> findOverdueLoans(LocalDate today);

    // Loan repayments within a period
    @Query("SELECT new map(l.applicantName as name, l.phoneNumber as phoneNumber, l.location as location, l.amount as amount, l.dueDate as dueDate, l.totalRepayableAmount as totalRepayableAmount, l.status as status) " +
            "FROM Loan l WHERE l.dueDate < :today")
    List<Map<String, Object>> findLoanRepayments(LocalDate today, LocalDate endDate);

    // Total revenue from repaid loans
    @Query("SELECT new map(SUM(l.amount) as totalDisbursed) FROM Loan l WHERE l.status = 'repaid'")
    List<Map<String, Object>> calculateRevenue();

    // Loans by status between dates
    @Query("SELECT new map(l.id as id, l.applicantName as name, l.amount as amount, l.status as status, l.disbursedDate as updatedDate) " +
            "FROM Loan l WHERE l.status = :status AND l.disbursedDate BETWEEN :startDate AND :endDate")
    List<Map<String, Object>> findByStatusBetweenDates(String status, LocalDate startDate, LocalDate endDate);

    // Count active loans by account and status
    long countByAccountAndStatusIn(Account account, List<String> statuses);

    // Find defaulters (due date passed, not fully paid)
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

    // Fetch all loans with account eagerly
    @Query("SELECT l FROM Loan l LEFT JOIN FETCH l.account")
    List<Loan> findAllWithAccount();

    // Fetch loan by ID with account eagerly
    @Query("SELECT l FROM Loan l LEFT JOIN FETCH l.account WHERE l.id = :id")
    Optional<Loan> findByIdWithAccount(@Param("id") Long id);

    List<Loan> findByPhoneNumber(String phoneNumber);

    List<Loan> findByAccount_NationalId(String nationalId);

    List<Loan> findByDueDate(LocalDate dueDate);

    List<Loan> findByDueDateBeforeAndStatus(
            LocalDate dueDate,
            String status);

    List<Loan> findByAccount_Id(Long accountId);


    Optional<Loan> findByApplicationNumber(String applicationNumber);
}