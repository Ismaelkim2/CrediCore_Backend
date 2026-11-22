
package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.AccountStatus;
import com.example.Credit.management.Model.Role;
import com.example.Credit.management.Model.User;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // =====================================================
    // USER LOOKUPS
    // =====================================================

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNationalId(String nationalId);

    Optional<User> findByNationalId(String nationalId);

    List<User> findByStatus(AccountStatus status);

    List<User> findByRole(Role role);

    // =====================================================
    // STAFF INVITATION LOOKUPS
    // =====================================================

    Optional<User> findByRegistrationTokenHash(String registrationTokenHash);

    boolean existsByRegistrationTokenHash(String registrationTokenHash);

    /**
     * Locks the invitation-owning User row until the current
     * transaction completes.
     *
     * Use this query when completing an invitation, not when
     * simply displaying invitation details.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT u
            FROM User u
            WHERE u.registrationTokenHash = :tokenHash
            """)
    Optional<User> findByRegistrationTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    // =====================================================
    // ADMINISTRATIVE STAFF LISTINGS
    // =====================================================

    List<User> findByRoleIn(List<Role> roles);

    List<User> findByRoleAndStatus(
            Role role,
            AccountStatus status
    );
}
