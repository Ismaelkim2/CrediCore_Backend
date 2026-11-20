package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.CustomerCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends
        JpaRepository<Account, Long>,
        JpaSpecificationExecutor<Account> {

    /* =====================================================
       BASIC LOOKUPS
       ===================================================== */

    Optional<Account> findByNationalId(String nationalId);

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findByPhoneNumber(String phoneNumber);

    Optional<Account> findByEmail(String email);

    Optional<Account> findByCifNumber(String cifNumber);

    Optional<Account> findByRegistrationNumber(String registrationNumber);

    /* =====================================================
       EXISTENCE CHECKS
       ===================================================== */

    boolean existsByNationalId(String nationalId);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByAccountNumber(String accountNumber);

    boolean existsByRegistrationNumber(String registrationNumber);

    boolean existsByCifNumber(String cifNumber);

    /* =====================================================
       SEARCHES
       ===================================================== */

    List<Account> findByFullNameContainingIgnoreCase(String name);

    List<Account> findByNationalIdContaining(String nationalId);

    List<Account> findByPhoneNumberContaining(String phone);

    List<Account> findByOrganizationNameContainingIgnoreCase(String organization);

    List<Account> findByCustomerCategory(CustomerCategory category);

    List<Account> findByBranch(String branch);

    List<Account> findByBranchCode(String branchCode);

    List<Account> findByApprovalStatus(String approvalStatus);

    List<Account> findByAccountStatus(String accountStatus);

    List<Account> findByRiskRating(String riskRating);

    List<Account> findByKycStatus(String kycStatus);

    List<Account> findByAmlStatus(String amlStatus);

    /* =====================================================
       DORMANT / CLOSED
       ===================================================== */

    List<Account> findByLastTransactionDateBefore(LocalDateTime date);

    List<Account> findByClosedAtIsNotNull();

    /* =====================================================
       DASHBOARD COUNTS
       ===================================================== */

    long countByCustomerCategory(CustomerCategory category);

    long countByApprovalStatus(String approvalStatus);

    long countByAccountStatus(String status);

    long countByKycStatus(String status);

    long countByAmlStatus(String status);

    /* =====================================================
       HIGH VALUE CUSTOMERS
       ===================================================== */

    List<Account> findByMonthlyIncomeGreaterThan(Double income);

    List<Account> findByAverageMonthlyTurnoverGreaterThan(Double turnover);

    /* =====================================================
       NEWEST CUSTOMERS
       ===================================================== */

    List<Account> findTop10ByOrderByCreatedAtDesc();

    List<Account> findTop20ByOrderByCreatedAtDesc();

    /* =====================================================
       CUSTOM QUERIES
       ===================================================== */

    @Query("""
            SELECT a
            FROM Account a
            WHERE LOWER(a.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR a.phoneNumber LIKE CONCAT('%', :keyword, '%')
               OR a.nationalId LIKE CONCAT('%', :keyword, '%')
               OR LOWER(a.organizationName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(a.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    List<Account> globalSearch(String keyword);

    @Query("""
            SELECT a
            FROM Account a
            ORDER BY a.balance DESC
            """)
    List<Account> richestAccounts(Pageable pageable);


    @Query("""
SELECT DISTINCT a
FROM Account a
LEFT JOIN FETCH a.organizationProfile op
LEFT JOIN FETCH op.people
""")
    List<Account> findAllWithOrganizationDetails();

}