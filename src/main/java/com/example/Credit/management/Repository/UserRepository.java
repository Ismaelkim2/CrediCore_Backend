package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.AccountStatus;
import com.example.Credit.management.Model.Role;
import com.example.Credit.management.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByNationalId(String nationalId);
    List<User> findByStatus(AccountStatus status);
    Optional<User> findByNationalId(String nationalId);
    List<User> findByRole(Role role);
}
